package com.apptolast.checkoutkmp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptolast.checkoutkmp.data.tokenizer.CardTokenizer
import com.apptolast.checkoutkmp.data.tokenizer.RawCard
import com.apptolast.checkoutkmp.data.tokenizer.TokenizationResult
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.usecase.ProcessPaymentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI ViewModel for the checkout screen. Reduces [CheckoutIntent] + current state into a new
 * [CheckoutState] by driving the shared [ProcessPaymentUseCase].
 *
 * The raw card only appears as a local `val` inside [submit]; it is tokenized and then out of scope.
 * Nothing sensitive is ever written to [state].
 */
class CheckoutViewModel(
    private val processPayment: ProcessPaymentUseCase,
    private val tokenizer: CardTokenizer,
    initialState: CheckoutState,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<CheckoutState> = _state.asStateFlow()

    fun onIntent(intent: CheckoutIntent) {
        when (intent) {
            is CheckoutIntent.SelectMethod -> _state.update { it.copy(method = intent.option) }
            is CheckoutIntent.Submit -> submit(intent.card)
            CheckoutIntent.Reset -> _state.update { it.copy(status = CheckoutStatus.Editing) }
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
                viewModelScope.launch {
                    processPayment(request).collect { paymentState ->
                        _state.update { it.copy(status = paymentState.toCheckoutStatus()) }
                    }
                }
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
