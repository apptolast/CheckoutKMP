package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.GiftCard
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.model.RedirectChallenge
import com.apptolast.checkoutkmp.domain.model.ScaChallenge
import com.apptolast.checkoutkmp.domain.model.SplitPlan
import com.apptolast.checkoutkmp.domain.model.planSplit

/** The payment methods the user can pick in the demo. The UI resolves the localized label. */
enum class MethodOption {
    CARD,
    PAYPAL,
    BIZUM,
}

/**
 * Immutable MVI state for the checkout screen.
 *
 * **Golden rule:** this exposed state never contains the PAN or CVV. Sensitive card input lives only
 * in the composable's local field state and is passed transiently through [CheckoutIntent.Submit].
 * (A gift-card code is not card data in the PCI sense, so [giftCard] may live here.)
 */
data class CheckoutState(
    val amount: Amount,
    val method: MethodOption = MethodOption.CARD,
    val scenario: PaymentScenario = PaymentScenario.APPROVED,
    val giftCard: GiftCard? = null,
    val giftCardNotFound: Boolean = false,
    val isApplyingGiftCard: Boolean = false,
    val status: CheckoutStatus = CheckoutStatus.Editing,
) {
    val isProcessing: Boolean get() = status is CheckoutStatus.Processing

    /** Tender split for the current total: gift card first, the card pays [SplitPlan.remainder]. */
    val plan: SplitPlan get() = planSplit(amount, giftCard)

    /**
     * True when a wallet method is selected while a gift card is applied. Wallets pay the full
     * total (they don't split tenders), so the UI must say so explicitly instead of silently
     * charging more than the "remaining to pay" the user last saw. The gift card is deliberately
     * kept applied: switching back to card resumes the split exactly as the user left it.
     */
    val walletIgnoresGiftCard: Boolean get() = method != MethodOption.CARD && giftCard != null
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
     * The user must approve the payment on the provider's page (simulated redirect).
     * [isConfirming] is true while the return is being reconciled with the PSP (webhook).
     */
    data class RequiresRedirect(
        val redirect: RedirectChallenge,
        val isConfirming: Boolean = false,
    ) : CheckoutStatus

    /**
     * Funds held; the customer has NOT been charged yet. [isCapturing]/[isVoiding] are true while
     * the demo "order dispatched" capture or the "cancel order" void is in flight;
     * [captureError]/[voidError] carry a failed attempt so the UI can show it inline and let the
     * user retry (same per-operation IdempotencyKey) without leaving the receipt.
     */
    data class Authorized(
        val receipt: Receipt,
        val isCapturing: Boolean = false,
        val captureError: PaymentError? = null,
        val isVoiding: Boolean = false,
        val voidError: PaymentError? = null,
    ) : CheckoutStatus {
        /** True while either merchant operation is in flight. */
        val isWorking: Boolean get() = isCapturing || isVoiding
    }

    /** The customer has been charged. [isRefunding]/[refundError] mirror the capture flags. */
    data class Captured(
        val receipt: Receipt,
        val isRefunding: Boolean = false,
        val refundError: PaymentError? = null,
    ) : CheckoutStatus

    /** The charge was returned to the customer. Terminal. */
    data class Refunded(val receipt: Receipt) : CheckoutStatus

    /** The hold was released without ever charging (order cancelled). Terminal. */
    data class Voided(val receipt: Receipt) : CheckoutStatus

    /** Terminal failure. */
    data class Failed(val error: PaymentError) : CheckoutStatus
}
