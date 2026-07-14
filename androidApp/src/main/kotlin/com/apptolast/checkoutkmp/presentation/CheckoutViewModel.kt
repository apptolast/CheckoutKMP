package com.apptolast.checkoutkmp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptolast.checkoutkmp.data.psp.PspScenarioController
import com.apptolast.checkoutkmp.data.tokenizer.CardTokenizer
import com.apptolast.checkoutkmp.data.tokenizer.RawCard
import com.apptolast.checkoutkmp.data.tokenizer.TokenizationResult
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.usecase.CompleteScaUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessPaymentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the checkout screen. Reduces [CheckoutIntent] + current state into a new
 * [CheckoutState] by driving the shared [ProcessPaymentUseCase] and [CompleteScaUseCase].
 *
 * The raw card only appears as a local `val` inside [submit]; it is tokenized and then out of scope.
 * The pending [PaymentRequest] kept for SCA is PAN-free, so retaining it is safe.
 */
class CheckoutViewModel(
    private val processPayment: ProcessPaymentUseCase,
    private val completeSca: CompleteScaUseCase,
    private val tokenizer: CardTokenizer,
    private val scenarioController: PspScenarioController,
    initialState: CheckoutState,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<CheckoutState> = _state.asStateFlow()

    /** The in-flight request, reused (same IdempotencyKey) to complete SCA. Never contains a PAN. */
    private var pendingRequest: PaymentRequest? = null

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

            CheckoutIntent.Retry -> retry()

            CheckoutIntent.Reset -> {
                pendingRequest = null
                _state.update { it.copy(status = CheckoutStatus.Editing) }
            }
        }
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
            pendingRequest = null
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

    private fun verifyOtp(otp: String) {
        val request = pendingRequest ?: return
        val challenge = (_state.value.status as? CheckoutStatus.RequiresSca)?.challenge ?: return

        viewModelScope.launch {
            completeSca(request, otp).collect { paymentState ->
                val newStatus = when (paymentState) {
                    PaymentState.Idle,
                    PaymentState.Processing ->
                        CheckoutStatus.RequiresSca(challenge, isVerifying = true)

                    is PaymentState.Approved -> {
                        pendingRequest = null
                        CheckoutStatus.Approved(paymentState.receipt)
                    }

                    is PaymentState.RequiresSca ->
                        CheckoutStatus.RequiresSca(paymentState.challenge)

                    is PaymentState.Failed ->
                        if (paymentState.error is PaymentError.ScaFailed) {
                            // Keep the challenge so the user can re-enter the code.
                            CheckoutStatus.RequiresSca(challenge, otpError = "Incorrect code, try again.")
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
    is PaymentState.Approved -> CheckoutStatus.Approved(receipt)
    is PaymentState.Failed -> CheckoutStatus.Failed(error)
}
