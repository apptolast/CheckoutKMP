package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.model.ScaChallenge

/** The payment methods the user can pick in the demo. The UI resolves the localized label. */
enum class MethodOption {
    CARD,
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
    val scenario: PaymentScenario = PaymentScenario.APPROVED,
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
     * 3D Secure required. [isVerifying] is true while an OTP is being checked; [otpError] is true
     * after a wrong code so the UI can show a localized message and let the user retry without
     * losing the challenge. The message text itself lives in the UI layer, not in this state.
     */
    data class RequiresSca(
        val challenge: ScaChallenge,
        val otpError: Boolean = false,
        val isVerifying: Boolean = false,
    ) : CheckoutStatus

    /**
     * Funds held; the customer has NOT been charged yet. [isCapturing] is true while the demo
     * "order dispatched" capture is in flight; [captureError] carries a failed capture so the UI
     * can show it inline and let the user retry (same capture IdempotencyKey) without leaving
     * the receipt.
     */
    data class Authorized(
        val receipt: Receipt,
        val isCapturing: Boolean = false,
        val captureError: PaymentError? = null,
    ) : CheckoutStatus

    /** The customer has been charged. [isRefunding]/[refundError] mirror the capture flags. */
    data class Captured(
        val receipt: Receipt,
        val isRefunding: Boolean = false,
        val refundError: PaymentError? = null,
    ) : CheckoutStatus

    /** The charge was returned to the customer. Terminal. */
    data class Refunded(val receipt: Receipt) : CheckoutStatus

    /** Terminal failure. */
    data class Failed(val error: PaymentError) : CheckoutStatus
}
