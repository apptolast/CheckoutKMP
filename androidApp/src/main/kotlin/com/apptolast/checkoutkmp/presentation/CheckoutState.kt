package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.psp.PspScenario
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
    val scenario: PspScenario = PspScenario.APPROVED,
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

    /**
     * 3D Secure required. [isVerifying] is true while an OTP is being checked; [otpError] holds the
     * inline message after a wrong code so the user can retry without losing the challenge.
     */
    data class RequiresSca(
        val challenge: ScaChallenge,
        val otpError: String? = null,
        val isVerifying: Boolean = false,
    ) : CheckoutStatus

    /** Terminal success. */
    data class Approved(val receipt: Receipt) : CheckoutStatus

    /** Terminal failure. */
    data class Failed(val error: PaymentError) : CheckoutStatus
}
