package com.apptolast.checkoutkmp.domain.model

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Card expiry as month + 4-digit year. A card is valid **through the last day of its
 * expiry month**, so April 2026 stops being valid on 1 May 2026.
 */
data class CardExpiry(
    val month: Int,
    val year: Int,
) {
    init {
        require(month in 1..12) { "Invalid month: $month" }
        require(year in 2000..2099) { "Invalid year: $year" }
    }

    /** Pure, testable check against a reference date. */
    fun isExpired(today: LocalDate): Boolean =
        today.year > year || (today.year == year && today.monthNumber > month)

    /** Convenience overload using the system clock. */
    fun isExpiredNow(
        clock: Clock = Clock.System,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Boolean = isExpired(clock.todayIn(timeZone))

    /** `MM/YY`, e.g. `04/26`. */
    fun format(): String =
        month.toString().padStart(2, '0') + "/" + (year % 100).toString().padStart(2, '0')

    companion object {
        /**
         * Parse a `MM/YY` or `MMYY` string. Two-digit years map to 20YY.
         * Returns null if the format is invalid; does not check expiry.
         */
        fun parse(raw: String): CardExpiry? {
            val digits = raw.filter { it.isDigit() }
            if (digits.length != 4) return null
            val month = digits.substring(0, 2).toIntOrNull() ?: return null
            val yy = digits.substring(2, 4).toIntOrNull() ?: return null
            if (month !in 1..12) return null
            return CardExpiry(month, 2000 + yy)
        }
    }
}
