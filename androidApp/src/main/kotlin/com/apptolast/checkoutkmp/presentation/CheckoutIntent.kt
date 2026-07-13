package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.tokenizer.RawCard

/** User intents for the checkout screen (the "I" in MVI). */
sealed interface CheckoutIntent {

    /** Choose a payment method. */
    data class SelectMethod(val option: MethodOption) : CheckoutIntent

    /**
     * Submit the card for payment. [card] carries the raw PAN/CVV **transiently** — the ViewModel
     * tokenizes it and discards it immediately; it never enters [CheckoutState] or any log.
     */
    data class Submit(val card: RawCard) : CheckoutIntent

    /** Return to the editing state after a terminal result. */
    data object Reset : CheckoutIntent
}
