package com.apptolast.checkoutkmp.domain.model

/**
 * Single source of truth for card-field validation bounds. Shared by the tokenizer (which enforces
 * them) and the UI (which validates live and formats), so the rules can't drift out of sync.
 */
object CardRules {
    /** Acceptable PAN length: from the shortest cards (some Maestro) to 19-digit numbers. */
    val PAN_LENGTHS = 12..19

    /** Acceptable CVV/CVC length: 3 for most networks, 4 for American Express. */
    val CVV_LENGTHS = 3..4

    /** Number of trailing digits kept visible when masking a card (e.g. `•••• 4242`). */
    const val LAST4_LENGTH = 4

    /** Card expiry is entered as MMYY: two month digits followed by two year digits. */
    const val EXPIRY_MONTH_DIGITS = 2
    const val EXPIRY_YEAR_DIGITS = 2
    const val EXPIRY_TOTAL_DIGITS = EXPIRY_MONTH_DIGITS + EXPIRY_YEAR_DIGITS

    /** Valid calendar-month range (1 = January … 12 = December). */
    val MONTH_RANGE = 1..12
}
