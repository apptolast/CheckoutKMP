package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.psp.PspScenario
import com.apptolast.checkoutkmp.domain.tokenizer.RawCard

/** User intents for the checkout screen (the "I" in MVI). */
sealed interface CheckoutIntent {

    /** Choose a payment method. */
    data class SelectMethod(val option: MethodOption) : CheckoutIntent

    /** Pick which behaviour the fake PSP should simulate on the next payment (demo control). */
    data class SelectScenario(val scenario: PspScenario) : CheckoutIntent

    /**
     * Submit the card for payment. [card] carries the raw PAN/CVV **transiently** — the ViewModel
     * tokenizes it and discards it immediately; it never enters [CheckoutState] or any log.
     */
    data class Submit(val card: RawCard) : CheckoutIntent

    /** Submit the 3D Secure OTP for the pending challenge. */
    data class SubmitOtp(val otp: String) : CheckoutIntent

    /** The user cancelled the 3D Secure challenge. */
    data object CancelSca : CheckoutIntent

    /** Retry a transient failure, reusing the same pending request (same IdempotencyKey). */
    data object Retry : CheckoutIntent

    /** Return to the editing state after a terminal result. */
    data object Reset : CheckoutIntent
}
