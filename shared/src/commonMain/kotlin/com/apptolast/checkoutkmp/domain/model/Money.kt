package com.apptolast.checkoutkmp.domain.model

/**
 * ISO-4217 currencies supported by the demo, with the number of fraction digits
 * used to interpret [Amount.minorUnits].
 */
enum class Currency(val code: String, val fractionDigits: Int) {
    EUR("EUR", 2),
    USD("USD", 2),
    GBP("GBP", 2),
    JPY("JPY", 0),
}

/**
 * A monetary amount stored in **minor units** (cents for EUR/USD, whole yen for JPY) to
 * avoid floating-point rounding errors. Amounts are non-negative.
 */
data class Amount(
    val minorUnits: Long,
    val currency: Currency,
) {
    init {
        require(minorUnits >= 0) { "Amount cannot be negative: $minorUnits" }
    }

    /** Human-readable value, e.g. `10.50` for 1050 EUR, `1050` for 1050 JPY. */
    fun format(): String {
        if (currency.fractionDigits == 0) return minorUnits.toString()
        val divisor = pow10(currency.fractionDigits)
        val major = minorUnits / divisor
        val fraction = (minorUnits % divisor).toString().padStart(currency.fractionDigits, '0')
        return "$major.$fraction"
    }

    /** Value plus currency code, e.g. `10.50 EUR`. */
    fun formatWithCurrency(): String = "${format()} ${currency.code}"

    /** Same-currency addition. */
    operator fun plus(other: Amount): Amount {
        requireSameCurrency(other)
        return Amount(minorUnits + other.minorUnits, currency)
    }

    /** Same-currency subtraction; the result must stay non-negative (amounts cannot go below zero). */
    operator fun minus(other: Amount): Amount {
        requireSameCurrency(other)
        return Amount(minorUnits - other.minorUnits, currency)
    }

    /** The smaller of two same-currency amounts. */
    fun coerceAtMost(other: Amount): Amount {
        requireSameCurrency(other)
        return if (minorUnits <= other.minorUnits) this else other
    }

    val isZero: Boolean get() = minorUnits == 0L

    private fun requireSameCurrency(other: Amount) =
        require(currency == other.currency) { "Currency mismatch: $currency vs ${other.currency}" }

    companion object {
        /** Build from major + minor units, e.g. `Amount.of(10, 50, EUR)` == 1050 minor units. */
        fun of(major: Long, minor: Long = 0, currency: Currency): Amount {
            require(minor >= 0) { "Minor part cannot be negative" }
            val divisor = pow10(currency.fractionDigits)
            require(minor < divisor || currency.fractionDigits == 0) {
                "Minor part $minor out of range for ${currency.code}"
            }
            return Amount(major * divisor + minor, currency)
        }
    }
}

private fun pow10(exp: Int): Long {
    var result = 1L
    repeat(exp) { result *= 10 }
    return result
}
