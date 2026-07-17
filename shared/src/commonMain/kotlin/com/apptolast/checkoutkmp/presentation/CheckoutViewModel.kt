package com.apptolast.checkoutkmp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptolast.checkoutkmp.domain.simulation.PaymentSimulator
import com.apptolast.checkoutkmp.domain.tokenizer.CardTokenizer
import com.apptolast.checkoutkmp.domain.tokenizer.RawCard
import com.apptolast.checkoutkmp.domain.tokenizer.TokenizationResult
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.usecase.CapturePaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.CompleteScaUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.RefundPaymentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the checkout screen. Reduces [CheckoutIntent] + current state into a new
 * [CheckoutState] by driving the shared use cases (authorize, SCA, capture, refund).
 *
 * The raw card only appears as a local `val` inside [submit]; it is tokenized and then out of scope.
 * The pending [PaymentRequest] kept for SCA is PAN-free, so retaining it is safe.
 *
 * Idempotency: the authorization key lives in [pendingRequest]; capture and refund each get their
 * **own** key ([captureKey] / [refundKey]) created on the first attempt and reused while retrying,
 * so no operation can ever run twice against the PSP.
 */
class CheckoutViewModel(
    private val processPayment: ProcessPaymentUseCase,
    private val completeSca: CompleteScaUseCase,
    private val capturePayment: CapturePaymentUseCase,
    private val refundPayment: RefundPaymentUseCase,
    private val tokenizer: CardTokenizer,
    private val scenarioController: PaymentSimulator,
    initialState: CheckoutState,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<CheckoutState> = _state.asStateFlow()

    /** The in-flight request, reused (same IdempotencyKey) to complete SCA. Never contains a PAN. */
    private var pendingRequest: PaymentRequest? = null

    /** Key of the current capture attempt; reused on retry, cleared once the capture succeeds. */
    private var captureKey: IdempotencyKey? = null

    /** Key of the current refund attempt; reused on retry, cleared once the refund succeeds. */
    private var refundKey: IdempotencyKey? = null

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

            is CheckoutIntent.Submit -> submit(intent.card)
            is CheckoutIntent.SubmitOtp -> verifyOtp(intent.otp)

            CheckoutIntent.CancelSca -> {
                pendingRequest = null
                _state.update { it.copy(status = CheckoutStatus.Failed(PaymentError.Cancelled)) }
            }

            CheckoutIntent.Capture -> capture()
            CheckoutIntent.Refund -> refund()

            CheckoutIntent.Retry -> retry()

            CheckoutIntent.Reset -> {
                clearPendingWork()
                _state.update { it.copy(status = CheckoutStatus.Editing) }
            }
        }
    }

    private fun clearPendingWork() {
        pendingRequest = null
        captureKey = null
        refundKey = null
    }

    private fun submit(card: RawCard) {
        // Tokenize first; the raw card never leaves this scope.
        when (val tokenized = tokenizer.tokenize(card)) {
            is TokenizationResult.Failure ->
                _state.update { it.copy(status = CheckoutStatus.Failed(tokenized.error)) }

            is TokenizationResult.Success -> {
                val request = PaymentRequest(
                    amount = _state.value.amount,
                    method = PaymentMethod.Card(tokenized.token),
                    idempotencyKey = IdempotencyKey.random(),
                )
                clearPendingWork()
                pendingRequest = request
                authorize(request)
            }
        }
    }

    /**
     * Re-run a transient failure with the **same** pending request/IdempotencyKey. Non-transient
     * failures (Declined, InvalidCard) have no safe automatic retry, so we return to editing.
     */
    private fun retry() {
        val failed = _state.value.status as? CheckoutStatus.Failed
        val request = pendingRequest
        if (failed != null && failed.error.isTransient && request != null) {
            authorize(request)
        } else {
            clearPendingWork()
            _state.update { it.copy(status = CheckoutStatus.Editing) }
        }
    }

    private fun authorize(request: PaymentRequest) {
        viewModelScope.launch {
            processPayment(request).collect { paymentState ->
                _state.update { it.copy(status = paymentState.toCheckoutStatus()) }
            }
        }
    }

    /** Demo "order dispatched": capture the held funds, reusing [captureKey] across retries. */
    private fun capture() {
        val current = _state.value.status as? CheckoutStatus.Authorized ?: return
        if (current.isCapturing) return

        val key = captureKey ?: IdempotencyKey.random().also { captureKey = it }
        _state.update { it.copy(status = current.copy(isCapturing = true, captureError = null)) }

        viewModelScope.launch {
            val newStatus = when (val result = capturePayment(current.receipt, key)) {
                is PaymentState.Captured -> {
                    captureKey = null
                    CheckoutStatus.Captured(result.receipt)
                }

                is PaymentState.Failed ->
                    current.copy(isCapturing = false, captureError = result.error)

                // The capture use case only settles or fails; anything else leaves the receipt as-is.
                else -> current.copy(isCapturing = false)
            }
            _state.update { it.copy(status = newStatus) }
        }
    }

    /** Return the captured charge to the customer, reusing [refundKey] across retries. */
    private fun refund() {
        val current = _state.value.status as? CheckoutStatus.Captured ?: return
        if (current.isRefunding) return

        val key = refundKey ?: IdempotencyKey.random().also { refundKey = it }
        _state.update { it.copy(status = current.copy(isRefunding = true, refundError = null)) }

        viewModelScope.launch {
            val newStatus = when (val result = refundPayment(current.receipt, key)) {
                is PaymentState.Refunded -> {
                    refundKey = null
                    CheckoutStatus.Refunded(result.receipt)
                }

                is PaymentState.Failed ->
                    current.copy(isRefunding = false, refundError = result.error)

                else -> current.copy(isRefunding = false)
            }
            _state.update { it.copy(status = newStatus) }
        }
    }

    private fun verifyOtp(otp: String) {
        val request = pendingRequest ?: return
        val challenge = (_state.value.status as? CheckoutStatus.RequiresSca)?.challenge ?: return

        viewModelScope.launch {
            completeSca(request, otp).collect { paymentState ->
                val newStatus = when (paymentState) {
                    PaymentState.Idle,
                    PaymentState.Processing ->
                        CheckoutStatus.RequiresSca(challenge, isVerifying = true)

                    is PaymentState.Authorized,
                    is PaymentState.Captured,
                    is PaymentState.Refunded -> {
                        pendingRequest = null
                        paymentState.toCheckoutStatus()
                    }

                    is PaymentState.RequiresSca ->
                        CheckoutStatus.RequiresSca(paymentState.challenge)

                    is PaymentState.Failed ->
                        if (paymentState.error is PaymentError.ScaFailed) {
                            // Keep the challenge so the user can re-enter the code.
                            CheckoutStatus.RequiresSca(challenge, otpError = true)
                        } else {
                            pendingRequest = null
                            CheckoutStatus.Failed(paymentState.error)
                        }
                }
                _state.update { it.copy(status = newStatus) }
            }
        }
    }
}

private fun PaymentState.toCheckoutStatus(): CheckoutStatus = when (this) {
    PaymentState.Idle, PaymentState.Processing -> CheckoutStatus.Processing
    is PaymentState.RequiresSca -> CheckoutStatus.RequiresSca(challenge)
    is PaymentState.Authorized -> CheckoutStatus.Authorized(receipt)
    is PaymentState.Captured -> CheckoutStatus.Captured(receipt)
    is PaymentState.Refunded -> CheckoutStatus.Refunded(receipt)
    is PaymentState.Failed -> CheckoutStatus.Failed(error)
}
