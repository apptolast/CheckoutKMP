package com.apptolast.checkoutkmp.domain.model

/**
 * A PCI-safe reference to a card, produced by the tokenizer (data layer).
 *
 * **Golden rule:** [value] is an opaque PSP token, NEVER the PAN. Only [last4] (for masking)
 * and [brand] are display-safe. The full card number, CVV and expiry-in-clear never live here.
 */
data class CardToken(
    val value: String,
    val brand: CardBrand,
    val last4: String,
    val expiry: CardExpiry,
) {
    init {
        require(value.isNotBlank()) { "Token value must not be blank" }
        require(last4.length == 4 && last4.all { it.isDigit() }) { "last4 must be 4 digits" }
    }

    /** Display-safe masked representation, e.g. `•••• 4242`. */
    val masked: String get() = "•••• $last4"
}
