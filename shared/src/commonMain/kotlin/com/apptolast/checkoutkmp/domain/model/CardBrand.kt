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
        fun detect(number: String): CardBrand {
            val digits = number.filter { it.isDigit() }
            if (digits.isEmpty()) return UNKNOWN
            val p2 = digits.take(2).toIntOrNull()
            val p4 = digits.take(4).toIntOrNull()
            return when {
                digits.startsWith("4") -> VISA
                p2 in 51..55 -> MASTERCARD
                p4 != null && p4 in 2221..2720 -> MASTERCARD
                p2 == 34 || p2 == 37 -> AMEX
                digits.startsWith("6011") || p2 == 65 -> DISCOVER
                else -> UNKNOWN
            }
        }
    }
}
