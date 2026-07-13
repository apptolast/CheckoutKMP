package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.model.ScaChallenge

/** The payment methods the user can pick in the demo. */
enum class MethodOption(val label: String) {
    CARD("Credit / debit card"),
}

/**
 * Immutable MVI state for the checkout screen.
 *
 * **Golden rule:** this exposed state never contains the PAN or CVV. Sensitive card input lives only
 * in the composable's local field state and is passed transiently through [CheckoutIntent.Submit].
 */
data class CheckoutState(
    val amount: Amount,
    val method: MethodOption = MethodOption.CARD,
    val status: CheckoutStatus = CheckoutStatus.Editing,
) {
    val isProcessing: Boolean get() = status is CheckoutStatus.Processing
}

/** Where the checkout is in its lifecycle. Mirrors the domain [com.apptolast.checkoutkmp.domain.model.PaymentState]. */
sealed interface CheckoutStatus {
    /** Filling in the form. */
    data object Editing : CheckoutStatus

    /** A payment call is in flight. */
    data object Processing : CheckoutStatus

    /** 3D Secure required; the challenge UI is built in phase 5. */
    data class RequiresSca(val challenge: ScaChallenge) : CheckoutStatus

    /** Terminal success. */
    data class Approved(val receipt: Receipt) : CheckoutStatus

    /** Terminal failure. */
    data class Failed(val error: PaymentError) : CheckoutStatus
}
