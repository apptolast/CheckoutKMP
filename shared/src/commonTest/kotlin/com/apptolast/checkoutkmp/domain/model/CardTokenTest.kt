package com.apptolast.checkoutkmp.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CardTokenTest {

    private val expiry = CardExpiry(12, 2030)

    @Test
    fun masks_using_only_last4() {
        val token = CardToken("tok_abc", CardBrand.VISA, "4242", expiry)
        assertEquals("•••• 4242", token.masked)
    }

    @Test
    fun rejects_blank_token_value() {
        assertFailsWith<IllegalArgumentException> {
            CardToken(" ", CardBrand.VISA, "4242", expiry)
        }
    }

    @Test
    fun rejects_malformed_last4() {
        assertFailsWith<IllegalArgumentException> {
            CardToken("tok_abc", CardBrand.VISA, "42", expiry)
        }
        assertFailsWith<IllegalArgumentException> {
            CardToken("tok_abc", CardBrand.VISA, "42x2", expiry)
        }
    }
}
