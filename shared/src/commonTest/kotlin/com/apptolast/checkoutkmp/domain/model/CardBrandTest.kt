package com.apptolast.checkoutkmp.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CardBrandTest {

    @Test
    fun detects_visa() {
        assertEquals(CardBrand.VISA, CardBrand.detect("4242424242424242"))
    }

    @Test
    fun detects_mastercard_classic_and_2_series() {
        assertEquals(CardBrand.MASTERCARD, CardBrand.detect("5555555555554444"))
        assertEquals(CardBrand.MASTERCARD, CardBrand.detect("2223003122003222"))
    }

    @Test
    fun detects_amex() {
        assertEquals(CardBrand.AMEX, CardBrand.detect("378282246310005"))
        assertEquals(CardBrand.AMEX, CardBrand.detect("341111111111111"))
    }

    @Test
    fun detects_discover() {
        assertEquals(CardBrand.DISCOVER, CardBrand.detect("6011111111111117"))
        assertEquals(CardBrand.DISCOVER, CardBrand.detect("6500000000000002"))
    }

    @Test
    fun ignores_spaces_and_falls_back_to_unknown() {
        assertEquals(CardBrand.VISA, CardBrand.detect("4242 4242 4242 4242"))
        assertEquals(CardBrand.UNKNOWN, CardBrand.detect("9999999999999999"))
        assertEquals(CardBrand.UNKNOWN, CardBrand.detect(""))
    }
}
