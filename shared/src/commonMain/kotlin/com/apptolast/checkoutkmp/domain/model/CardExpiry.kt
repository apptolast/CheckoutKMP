package com.apptolast.checkoutkmp.domain.model

import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Card expiry as month + 4-digit year. A card is valid **through the last day of its
 * expiry month**, so April 2026 stops being valid on 1 May 2026.
 */
data class CardExpiry(
    val month: Int,
    val year: Int,
) {
    init {
        require(month in CardRules.MONTH_RANGE) { "Invalid month: $month" }
        require(year in YEAR_RANGE) { "Invalid year: $year" }
    }

    /** Pure, testable check against a reference date. */
    fun isExpired(today: LocalDate): Boolean =
        today.year > year || (today.year == year && today.month.ordinal + 1 > month)

    /** Convenience overload using the system clock. */
    fun isExpiredNow(
        clock: Clock = Clock.System,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Boolean = isExpired(clock.now().toLocalDateTime(timeZone).date)

    /** `MM/YY`, e.g. `04/26`. */
    fun format(): String =
        month.toString().padStart(CardRules.EXPIRY_MONTH_DIGITS, '0') + "/" +
            (year % YEARS_PER_CENTURY).toString().padStart(CardRules.EXPIRY_YEAR_DIGITS, '0')

    companion object {
        /** Two-digit years are mapped into the 21st century (e.g. `26` → `2026`). */
        private const val CENTURY_BASE = 2000
        private const val YEARS_PER_CENTURY = 100
        private val YEAR_RANGE = CENTURY_BASE..(CENTURY_BASE + YEARS_PER_CENTURY - 1)

        /**
         * Parse a `MM/YY` or `MMYY` string. Two-digit years map to 20YY.
         * Returns null if the format is invalid; does not check expiry.
         */
        fun parse(raw: String): CardExpiry? {
            val digits = raw.filter { it.isDigit() }
            if (digits.length != CardRules.EXPIRY_TOTAL_DIGITS) return null
            val month = digits.substring(0, CardRules.EXPIRY_MONTH_DIGITS).toIntOrNull() ?: return null
            val yy = digits.substring(CardRules.EXPIRY_MONTH_DIGITS, CardRules.EXPIRY_TOTAL_DIGITS).toIntOrNull()
                ?: return null
            if (month !in CardRules.MONTH_RANGE) return null
            return CardExpiry(month, CENTURY_BASE + yy)
        }
    }
}
