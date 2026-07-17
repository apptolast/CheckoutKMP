package com.apptolast.checkoutkmp.domain.model

/**
 * Card network brand. [detect] infers the brand from the card number prefix (IIN/BIN ranges).
 * Only the brand is ever surfaced to UI/state — never the PAN.
 */
enum class CardBrand(val displayName: String) {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    AMEX("American Express"),
    DISCOVER("Discover"),
    UNKNOWN("Card"),
    ;

    companion object {
        // Issuer Identification Number (IIN/BIN) prefixes per network, matched against the leading
        // digits of the card number. Named here rather than inlined so the ranges stay auditable.
        private const val VISA_PREFIX = "4"
        private val MASTERCARD_IIN = 51..55
        private val MASTERCARD_2SERIES = 2221..2720
        private const val AMEX_PREFIX_1 = 34
        private const val AMEX_PREFIX_2 = 37
        private const val DISCOVER_PREFIX = "6011"
        private const val DISCOVER_IIN = 65

        /** Number of leading digits inspected to classify a card (the widest prefix we match). */
        private const val PREFIX_DIGITS = 4
        private const val SHORT_PREFIX_DIGITS = 2

        fun detect(number: String): CardBrand {
            val digits = number.filter { it.isDigit() }
            if (digits.isEmpty()) return UNKNOWN
            val p2 = digits.take(SHORT_PREFIX_DIGITS).toIntOrNull()
            val p4 = digits.take(PREFIX_DIGITS).toIntOrNull()
            return when {
                digits.startsWith(VISA_PREFIX) -> VISA
                p2 in MASTERCARD_IIN -> MASTERCARD
                p4 != null && p4 in MASTERCARD_2SERIES -> MASTERCARD
                p2 == AMEX_PREFIX_1 || p2 == AMEX_PREFIX_2 -> AMEX
                digits.startsWith(DISCOVER_PREFIX) || p2 == DISCOVER_IIN -> DISCOVER
                else -> UNKNOWN
            }
        }
    }
}
