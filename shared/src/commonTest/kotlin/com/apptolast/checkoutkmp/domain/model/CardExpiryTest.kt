package com.apptolast.checkoutkmp.domain.model

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardExpiryTest {

    private val today = LocalDate(2026, 7, 14)

    @Test
    fun valid_through_the_last_day_of_the_expiry_month() {
        // Same month as today -> still valid.
        assertFalse(CardExpiry(7, 2026).isExpired(today))
        // Future month/year -> valid.
        assertFalse(CardExpiry(8, 2026).isExpired(today))
        assertFalse(CardExpiry(1, 2027).isExpired(today))
    }

    @Test
    fun expired_once_the_month_has_passed() {
        assertTrue(CardExpiry(6, 2026).isExpired(today))
        assertTrue(CardExpiry(12, 2025).isExpired(today))
    }

    @Test
    fun parse_accepts_mm_slash_yy_and_mmyy() {
        assertEquals(CardExpiry(4, 2026), CardExpiry.parse("04/26"))
        assertEquals(CardExpiry(4, 2026), CardExpiry.parse("0426"))
        assertEquals(CardExpiry(12, 2030), CardExpiry.parse("12/30"))
    }

    @Test
    fun parse_rejects_invalid_input() {
        assertNull(CardExpiry.parse("13/26")) // month out of range
        assertNull(CardExpiry.parse("4/26"))  // not 4 digits
        assertNull(CardExpiry.parse("abcd"))
        assertNull(CardExpiry.parse(""))
    }

    @Test
    fun format_renders_mm_slash_yy() {
        assertEquals("04/26", CardExpiry(4, 2026).format())
    }

    @Test
    fun constructor_validates_ranges() {
        assertFailsWith<IllegalArgumentException> { CardExpiry(0, 2026) }
        assertFailsWith<IllegalArgumentException> { CardExpiry(13, 2026) }
        assertFailsWith<IllegalArgumentException> { CardExpiry(6, 1999) }
    }
}
