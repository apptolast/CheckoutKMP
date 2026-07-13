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
}
