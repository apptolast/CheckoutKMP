package com.apptolast.checkoutkmp.domain.usecase

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LuhnTest {

    @Test
    fun accepts_valid_card_numbers() {
        // Well-known PSP test numbers, all Luhn-valid.
        assertTrue(Luhn.isValid("4242424242424242")) // Visa
        assertTrue(Luhn.isValid("4111111111111111")) // Visa
        assertTrue(Luhn.isValid("5555555555554444")) // Mastercard
        assertTrue(Luhn.isValid("378282246310005"))  // Amex
        assertTrue(Luhn.isValid("6011111111111117")) // Discover
    }

    @Test
    fun ignores_spaces() {
        assertTrue(Luhn.isValid("4242 4242 4242 4242"))
    }

    @Test
    fun rejects_numbers_that_fail_checksum() {
        assertFalse(Luhn.isValid("4242424242424241"))
        assertFalse(Luhn.isValid("1234567890123456"))
    }

    @Test
    fun rejects_non_digits_and_too_short() {
        assertFalse(Luhn.isValid(""))
        assertFalse(Luhn.isValid("4"))
        assertFalse(Luhn.isValid("4242-4242"))
        assertFalse(Luhn.isValid("abcd"))
    }
}
