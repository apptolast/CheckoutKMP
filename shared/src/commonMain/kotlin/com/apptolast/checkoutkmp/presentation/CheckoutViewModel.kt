package com.apptolast.checkoutkmp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptolast.checkoutkmp.domain.giftcard.GiftCardLookup
import com.apptolast.checkoutkmp.domain.giftcard.GiftCardRedemption
import com.apptolast.checkoutkmp.domain.giftcard.ReversalResult
import com.apptolast.checkoutkmp.domain.simulation.PaymentSimulator
import com.apptolast.checkoutkmp.domain.tokenizer.CardTokenizer
import com.apptolast.checkoutkmp.domain.tokenizer.RawCard
import com.apptolast.checkoutkmp.domain.tokenizer.TokenizationResult
import com.apptolast.checkoutkmp.domain.model.GiftCard
import com.apptolast.checkoutkmp.domain.model.GiftCardTender
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.model.RedirectReturn
import com.apptolast.checkoutkmp.domain.usecase.SplitPaymentEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the checkout screen. Reduces [CheckoutIntent] + current state into a new
 * [CheckoutState] by driving the shared [CheckoutUseCases] (authorize, SCA, split tenders,
 * capture, refund).
 *
 * The raw card only appears as a local `val` inside [submit]; it is tokenized and then out of scope.
 * The pending [PaymentRequest] kept for SCA is PAN-free, so retaining it is safe.
 *
 * Idempotency: every operation owns its key — the authorization key lives in [pendingRequest];
 * the gift-card redemption/reversal and the capture/refund each get their own, created on the
 * first attempt and reused while retrying, so nothing can ever run twice against the backends.
 *
 * Compensation: while a split payment is unfinished, [pendingRedemption] tracks the consumed
 * gift-card balance. Abandoning the attempt (cancelled SCA, giving up on a failure) reverses the
 * redemption so the customer keeps their balance.
 */
class CheckoutViewModel(
    private val useCases: CheckoutUseCases,
    private val tokenizer: CardTokenizer,
    private val scenarioController: PaymentSimulator,
    initialState: CheckoutState,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<CheckoutState> = _state.asStateFlow()

    /**
     * Applies a settlement-bearing status change and records its receipt in the order history —
     * the single funnel for outcomes, so every lifecycle step (authorize, capture, void, refund)
     * updates the same order.
     */
    private fun applyStatus(newStatus: CheckoutStatus) {
        newStatus.receiptOrNull()?.let { useCases.recordOrder(it) }
        _state.update { it.copy(status = newStatus) }
    }

    private fun CheckoutStatus.receiptOrNull() = when (this) {
        is CheckoutStatus.Authorized -> receipt
        is CheckoutStatus.Captured -> receipt
        is CheckoutStatus.Refunded -> receipt
        is CheckoutStatus.Voided -> receipt
        else -> null
    }

    /** The in-flight card request, reused (same IdempotencyKey) to complete SCA and to retry. */
    private var pendingRequest: PaymentRequest? = null

    /** The gift card of the in-flight split attempt (needed to re-run the saga on retry). */
    private var pendingGiftCard: GiftCard? = null

    /** Consumed-but-unsettled gift balance; reversed if the attempt is abandoned. */
    private var pendingRedemption: GiftCardRedemption? = null

    /** Keys of the in-flight split attempt: redemption and its (possible) compensation. */
    private var redemptionKey: IdempotencyKey? = null
    private var reversalKey: IdempotencyKey? = null

    /** Key of the current capture attempt; reused on retry, cleared once the capture succeeds. */
    private var captureKey: IdempotencyKey? = null

    /** Key of the current void attempt; reused on retry, cleared once the hold is released. */
    private var voidKey: IdempotencyKey? = null

    /** Keys of the current refund attempt (card refund + gift-card reversal to origin). */
    private var refundKey: IdempotencyKey? = null
    private var refundReversalKey: IdempotencyKey? = null

    init {
        scenarioController.scenario = initialState.scenario
    }

    fun onIntent(intent: CheckoutIntent) {
        when (intent) {
            is CheckoutIntent.SelectMethod -> _state.update { it.copy(method = intent.option) }

            is CheckoutIntent.SelectScenario -> {
                scenarioController.scenario = intent.scenario
                _state.update { it.copy(scenario = intent.scenario) }
            }

            is CheckoutIntent.ApplyGiftCard -> applyGiftCard(intent.code)

            CheckoutIntent.RemoveGiftCard ->
                _state.update { it.copy(giftCard = null, giftCardNotFound = false) }

            CheckoutIntent.ClearGiftCardError ->
                _state.update { it.copy(giftCardNotFound = false) }

            is CheckoutIntent.Submit -> submit(intent.card)
            CheckoutIntent.SubmitGiftCardOnly -> submitGiftCardOnly()
            CheckoutIntent.SubmitWallet -> submitWallet()
            is CheckoutIntent.SubmitOtp -> verifyOtp(intent.otp)
            is CheckoutIntent.CompleteRedirect -> completeRedirect(intent.returned)

            CheckoutIntent.CancelSca -> {
                compensateAbandonedRedemption()
                pendingRequest = null
                _state.update { it.copy(status = CheckoutStatus.Failed(PaymentError.Cancelled)) }
            }

            CheckoutIntent.Capture -> capture()
            CheckoutIntent.Void -> voidAuthorization()
            CheckoutIntent.Refund -> refund()

            CheckoutIntent.Retry -> retry()

            CheckoutIntent.Reset -> {
                compensateAbandonedRedemption()
                clearPendingWork()
                _state.update { it.copy(status = CheckoutStatus.Editing) }
            }
        }
    }

    private fun clearPendingWork() {
        pendingRequest = null
        pendingGiftCard = null
        pendingRedemption = null
        redemptionKey = null
        reversalKey = null
        captureKey = null
        voidKey = null
        refundKey = null
        refundReversalKey = null
    }

    private fun applyGiftCard(code: String) {
        if (_state.value.isApplyingGiftCard) return
        // Busy from the moment the intent arrives; a new lookup also voids the previous verdict.
        _state.update { it.copy(isApplyingGiftCard = true, giftCardNotFound = false) }
        viewModelScope.launch {
            val lookup = useCases.applyGiftCard(code)
            _state.update {
                when (lookup) {
                    is GiftCardLookup.Found ->
                        it.copy(giftCard = lookup.card, isApplyingGiftCard = false)

                    GiftCardLookup.NotFound ->
                        it.copy(giftCardNotFound = true, isApplyingGiftCard = false)
                }
            }
        }
    }

    private fun submit(card: RawCard) {
        // Tokenize first; the raw card never leaves this scope.
        when (val tokenized = tokenizer.tokenize(card)) {
            is TokenizationResult.Failure ->
                _state.update { it.copy(status = CheckoutStatus.Failed(tokenized.error)) }

            is TokenizationResult.Success -> {
                clearPendingWork()
                val current = _state.value
                val giftCard = current.giftCard
                val request = PaymentRequest(
                    // With a gift card applied, the card is only charged the remainder.
                    amount = current.plan.remainder,
                    method = PaymentMethod.Card(tokenized.token),
                    idempotencyKey = IdempotencyKey.random(),
                )
                pendingRequest = request
                if (giftCard == null) {
                    authorize(request)
                } else {
                    startSplit(giftCard, request)
                }
            }
        }
    }

    private fun submitGiftCardOnly() {
        val current = _state.value
        val giftCard = current.giftCard ?: return
        if (!current.plan.coversTotal) return

        clearPendingWork()
        startSplit(giftCard, cardRequest = null)
    }

    /** Pay with the selected redirect wallet for the full total (wallets do not split tenders). */
    private fun submitWallet() {
        val provider = when (_state.value.method) {
            MethodOption.PAYPAL -> PaymentMethod.Wallet.Provider.PAYPAL
            MethodOption.BIZUM -> PaymentMethod.Wallet.Provider.BIZUM
            MethodOption.CARD -> return
        }
        clearPendingWork()
        val request = PaymentRequest(
            amount = _state.value.amount,
            method = PaymentMethod.Wallet(provider),
            idempotencyKey = IdempotencyKey.random(),
        )
        pendingRequest = request
        authorize(request)
    }

    /**
     * The user came back from the provider claiming [returned]. The PSP reconciles the claim
     * against its webhook record — an approved return can still fail if the provider never
     * confirmed the payment.
     */
    private fun completeRedirect(returned: RedirectReturn) {
        val request = pendingRequest ?: return
        val current = _state.value.status as? CheckoutStatus.RequiresRedirect ?: return
        if (current.isConfirming) return

        viewModelScope.launch {
            useCases.completeRedirect(request, returned).collect { paymentState ->
                val newStatus = when (paymentState) {
                    PaymentState.Idle,
                    PaymentState.Processing -> current.copy(isConfirming = true)

                    is PaymentState.Failed -> {
                        pendingRequest = null
                        CheckoutStatus.Failed(paymentState.error)
                    }

                    else -> {
                        if (paymentState.isSettled) pendingRequest = null
                        paymentState.toCheckoutStatus()
                    }
                }
                applyStatus(newStatus)
            }
        }
    }

    private fun startSplit(giftCard: GiftCard, cardRequest: PaymentRequest?) {
        pendingGiftCard = giftCard
        redemptionKey = IdempotencyKey.random()
        reversalKey = IdempotencyKey.random()
        runSplit(giftCard, cardRequest)
    }

    /**
     * Re-run a transient failure with the **same** pending request/keys. For a split attempt the
     * whole saga is re-run: the redemption replays idempotently, only the card call retries.
     * Non-transient failures have no safe automatic retry, so we return to editing.
     */
    private fun retry() {
        val failed = _state.value.status as? CheckoutStatus.Failed
        val giftCard = pendingGiftCard
        val request = pendingRequest
        if (failed == null || !failed.error.isTransient) {
            compensateAbandonedRedemption()
            clearPendingWork()
            _state.update { it.copy(status = CheckoutStatus.Editing) }
            return
        }
        when {
            giftCard != null -> runSplit(giftCard, request)
            request != null -> authorize(request)
            else -> _state.update { it.copy(status = CheckoutStatus.Editing) }
        }
    }

    private fun authorize(request: PaymentRequest) {
        viewModelScope.launch {
            useCases.processPayment(request).collect { paymentState ->
                applyStatus(paymentState.toCheckoutStatus())
            }
        }
    }

    private fun runSplit(giftCard: GiftCard, cardRequest: PaymentRequest?) {
        val redemption = checkNotNull(redemptionKey)
        val reversal = checkNotNull(reversalKey)
        viewModelScope.launch {
            useCases.processSplitPayment(
                total = _state.value.amount,
                giftCard = giftCard,
                cardRequest = cardRequest,
                redemptionKey = redemption,
                reversalKey = reversal,
            ).collect { event ->
                when (event) {
                    is SplitPaymentEvent.GiftCardRedeemed -> pendingRedemption = event.redemption

                    is SplitPaymentEvent.StateChanged -> {
                        val paymentState = event.state
                        when {
                            // Settled: the consumed balance is now part of the sale, nothing to reverse.
                            paymentState is PaymentState.Authorized ||
                                paymentState is PaymentState.Captured -> pendingRedemption = null

                            // Business failure: the use case already compensated the redemption.
                            paymentState is PaymentState.Failed && !paymentState.error.isTransient ->
                                pendingRedemption = null

                            // Transient failure / SCA pending: keep the redemption for retry or abandon.
                            else -> Unit
                        }
                        applyStatus(paymentState.toCheckoutStatus())
                    }
                }
            }
        }
    }

    /** Reverse a consumed-but-unsettled redemption when the user abandons the attempt. */
    private fun compensateAbandonedRedemption() {
        val redemption = pendingRedemption ?: return
        val key = reversalKey ?: IdempotencyKey.random()
        pendingRedemption = null
        viewModelScope.launch { useCases.reverseGiftCard(redemption.redemptionId, key) }
    }

    /** Demo "order dispatched": capture the held funds, reusing [captureKey] across retries. */
    private fun capture() {
        val current = _state.value.status as? CheckoutStatus.Authorized ?: return
        if (current.isWorking) return

        val key = captureKey ?: IdempotencyKey.random().also { captureKey = it }
        _state.update { it.copy(status = current.copy(isCapturing = true, captureError = null)) }

        viewModelScope.launch {
            val newStatus = when (val result = useCases.capturePayment(current.receipt, key)) {
                is PaymentState.Captured -> {
                    captureKey = null
                    CheckoutStatus.Captured(result.receipt)
                }

                is PaymentState.Failed ->
                    current.copy(isCapturing = false, captureError = result.error)

                // The capture use case only settles or fails; anything else leaves the receipt as-is.
                else -> current.copy(isCapturing = false)
            }
            applyStatus(newStatus)
        }
    }

    /** Demo "cancel the order": release the hold, reusing [voidKey] across retries. */
    private fun voidAuthorization() {
        val current = _state.value.status as? CheckoutStatus.Authorized ?: return
        if (current.isWorking) return

        val key = voidKey ?: IdempotencyKey.random().also { voidKey = it }
        _state.update { it.copy(status = current.copy(isVoiding = true, voidError = null)) }

        viewModelScope.launch {
            val newStatus = when (val result = useCases.voidAuthorization(current.receipt, key)) {
                is PaymentState.Voided -> {
                    voidKey = null
                    CheckoutStatus.Voided(result.receipt)
                }

                is PaymentState.Failed ->
                    current.copy(isVoiding = false, voidError = result.error)

                else -> current.copy(isVoiding = false)
            }
            applyStatus(newStatus)
        }
    }

    /**
     * Return the charge to the customer, tender by tender: the card portion through the PSP, the
     * gift-card portion by reversing its redemption (refund-to-origin). Each step reuses its own
     * key across retries, and each replays idempotently if the other one failed halfway.
     */
    private fun refund() {
        val current = _state.value.status as? CheckoutStatus.Captured ?: return
        if (current.isRefunding) return

        _state.update { it.copy(status = current.copy(isRefunding = true, refundError = null)) }

        viewModelScope.launch {
            val receipt = current.receipt
            val newStatus = if (receipt.method is PaymentMethod.GiftCard) {
                refundGiftCardOnly(current)
            } else {
                refundCardThenGiftCard(current)
            }
            applyStatus(newStatus)
        }
    }

    /** A payment fully covered by the gift card refunds by reversing its single redemption. */
    private suspend fun refundGiftCardOnly(current: CheckoutStatus.Captured): CheckoutStatus {
        val tender = current.receipt.giftCard
            ?: return current.copy(isRefunding = false, refundError = PaymentError.Unknown("missing gift card tender"))
        val key = refundReversalKey ?: IdempotencyKey.random().also { refundReversalKey = it }

        return when (val result = useCases.reverseGiftCard(tender.redemptionId, key)) {
            is ReversalResult.Success -> {
                refundReversalKey = null
                CheckoutStatus.Refunded(current.receipt.copy(refundedAt = result.reversedAt))
            }

            is ReversalResult.Failure ->
                current.copy(isRefunding = false, refundError = result.error)
        }
    }

    /** Card (and, for split payments, gift-card-to-origin) refund. */
    private suspend fun refundCardThenGiftCard(current: CheckoutStatus.Captured): CheckoutStatus {
        val key = refundKey ?: IdempotencyKey.random().also { refundKey = it }

        val refunded = when (val result = useCases.refundPayment(current.receipt, key)) {
            is PaymentState.Refunded -> result.receipt
            is PaymentState.Failed ->
                return current.copy(isRefunding = false, refundError = result.error)
            else -> return current.copy(isRefunding = false)
        }

        val tender = refunded.giftCard ?: run {
            refundKey = null
            return CheckoutStatus.Refunded(refunded)
        }

        // Split payment: also return the gift-card portion to its card.
        val reversalKey = refundReversalKey ?: IdempotencyKey.random().also { refundReversalKey = it }
        return when (val reversal = useCases.reverseGiftCard(tender.redemptionId, reversalKey)) {
            is ReversalResult.Success -> {
                refundKey = null
                refundReversalKey = null
                CheckoutStatus.Refunded(refunded)
            }

            // Card already refunded (idempotent to retry); surface the reversal failure inline.
            is ReversalResult.Failure ->
                current.copy(isRefunding = false, refundError = reversal.error)
        }
    }

    private fun verifyOtp(otp: String) {
        val request = pendingRequest ?: return
        val challenge = (_state.value.status as? CheckoutStatus.RequiresSca)?.challenge ?: return

        viewModelScope.launch {
            useCases.completeSca(request, otp).collect { paymentState ->
                val newStatus = when (paymentState) {
                    PaymentState.Idle,
                    PaymentState.Processing ->
                        CheckoutStatus.RequiresSca(challenge, isVerifying = true)

                    is PaymentState.Authorized,
                    is PaymentState.Captured,
                    is PaymentState.Refunded,
                    is PaymentState.Voided -> {
                        pendingRequest = null
                        paymentState.withPendingGiftCardTender().toCheckoutStatus()
                    }

                    is PaymentState.RequiresSca ->
                        CheckoutStatus.RequiresSca(paymentState.challenge)

                    is PaymentState.RequiresRedirect ->
                        CheckoutStatus.RequiresRedirect(paymentState.redirect)

                    is PaymentState.Failed ->
                        if (paymentState.error is PaymentError.ScaFailed) {
                            // Keep the challenge so the user can re-enter the code.
                            CheckoutStatus.RequiresSca(challenge, otpError = true)
                        } else {
                            // The card leg failed for good after the balance was consumed: compensate.
                            compensateAbandonedRedemption()
                            pendingRequest = null
                            CheckoutStatus.Failed(paymentState.error)
                        }
                }
                applyStatus(newStatus)
            }
        }
    }

    /** After SCA settles a split payment, stamp the gift-card tender onto the receipt. */
    private fun PaymentState.withPendingGiftCardTender(): PaymentState {
        val redemption = pendingRedemption ?: return this
        pendingRedemption = null
        val tender = GiftCardTender(redemption.redemptionId, redemption.amount)
        return when (this) {
            is PaymentState.Authorized -> PaymentState.Authorized(receipt.copy(giftCard = tender))
            is PaymentState.Captured -> PaymentState.Captured(receipt.copy(giftCard = tender))
            else -> this
        }
    }
}

private fun PaymentState.toCheckoutStatus(): CheckoutStatus = when (this) {
    PaymentState.Idle, PaymentState.Processing -> CheckoutStatus.Processing
    is PaymentState.RequiresSca -> CheckoutStatus.RequiresSca(challenge)
    is PaymentState.RequiresRedirect -> CheckoutStatus.RequiresRedirect(redirect)
    is PaymentState.Authorized -> CheckoutStatus.Authorized(receipt)
    is PaymentState.Captured -> CheckoutStatus.Captured(receipt)
    is PaymentState.Refunded -> CheckoutStatus.Refunded(receipt)
    is PaymentState.Voided -> CheckoutStatus.Voided(receipt)
    is PaymentState.Failed -> CheckoutStatus.Failed(error)
}
