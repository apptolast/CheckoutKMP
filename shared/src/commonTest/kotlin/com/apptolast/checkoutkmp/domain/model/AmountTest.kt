package com.apptolast.checkoutkmp.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AmountTest {

    @Test
    fun formats_two_decimal_currencies() {
        assertEquals("10.50", Amount(1050, Currency.EUR).format())
        assertEquals("0.09", Amount(9, Currency.USD).format())
        assertEquals("100.00", Amount(10000, Currency.GBP).format())
    }

    @Test
    fun formats_zero_decimal_currencies() {
        assertEquals("1050", Amount(1050, Currency.JPY).format())
    }

    @Test
    fun format_with_currency_appends_code() {
        assertEquals("10.50 EUR", Amount(1050, Currency.EUR).formatWithCurrency())
    }

    @Test
    fun of_builds_from_major_and_minor() {
        assertEquals(1050, Amount.of(major = 10, minor = 50, currency = Currency.EUR).minorUnits)
        assertEquals(10, Amount.of(major = 10, currency = Currency.JPY).minorUnits)
    }

    @Test
    fun rejects_negative_amounts() {
        assertFailsWith<IllegalArgumentException> { Amount(-1, Currency.EUR) }
    }

    @Test
    fun adds_and_subtracts_same_currency_amounts() {
        assertEquals(Amount(1500, Currency.EUR), Amount(1050, Currency.EUR) + Amount(450, Currency.EUR))
        assertEquals(Amount(600, Currency.EUR), Amount(1050, Currency.EUR) - Amount(450, Currency.EUR))
    }

    @Test
    fun subtraction_cannot_go_below_zero() {
        assertFailsWith<IllegalArgumentException> { Amount(100, Currency.EUR) - Amount(200, Currency.EUR) }
    }

    @Test
    fun arithmetic_rejects_mixed_currencies() {
        assertFailsWith<IllegalArgumentException> { Amount(100, Currency.EUR) + Amount(100, Currency.USD) }
        assertFailsWith<IllegalArgumentException> { Amount(100, Currency.EUR) - Amount(50, Currency.USD) }
        assertFailsWith<IllegalArgumentException> { Amount(100, Currency.EUR).coerceAtMost(Amount(50, Currency.USD)) }
    }

    @Test
    fun coerce_at_most_caps_at_the_smaller_amount() {
        assertEquals(Amount(50, Currency.EUR), Amount(100, Currency.EUR).coerceAtMost(Amount(50, Currency.EUR)))
        assertEquals(Amount(50, Currency.EUR), Amount(50, Currency.EUR).coerceAtMost(Amount(100, Currency.EUR)))
    }

    @Test
    fun is_zero_only_for_zero_minor_units() {
        assertEquals(true, Amount(0, Currency.EUR).isZero)
        assertEquals(false, Amount(1, Currency.EUR).isZero)
    }
}
