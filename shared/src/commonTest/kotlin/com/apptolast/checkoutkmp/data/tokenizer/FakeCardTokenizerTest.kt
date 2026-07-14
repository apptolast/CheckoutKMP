package com.apptolast.checkoutkmp.data.tokenizer

import com.apptolast.checkoutkmp.domain.model.CardBrand
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.tokenizer.RawCard
import com.apptolast.checkoutkmp.domain.tokenizer.TokenizationResult
import com.apptolast.checkoutkmp.support.FixedClock
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FakeCardTokenizerTest {

    private val tokenizer = FakeCardTokenizer(clock = FixedClock.default, timeZone = TimeZone.UTC)
    private val future = CardExpiry(12, 2030)

    @Test
    fun tokenizes_a_valid_card_into_a_pci_safe_token() {
        val result = tokenizer.tokenize(RawCard(pan = "4242 4242 4242 4242", expiry = future, cvv = "123"))

        val token = assertIs<TokenizationResult.Success>(result).token
        assertEquals(CardBrand.VISA, token.brand)
        assertEquals("4242", token.last4)
        assertEquals("•••• 4242", token.masked)
    }

    @Test
    fun token_never_contains_the_pan() {
        val pan = "4242424242424242"
        val result = tokenizer.tokenize(RawCard(pan = pan, expiry = future, cvv = "123"))

        val token = assertIs<TokenizationResult.Success>(result).token
        assertTrue(token.value.startsWith("tok_"))
        assertFalse(token.value.contains(pan))
        // Only the last four digits are ever exposed.
        assertFalse(token.masked.contains(pan))
        assertEquals("4242", pan.takeLast(4))
    }

    @Test
    fun rejects_a_number_that_fails_luhn() {
        val result = tokenizer.tokenize(RawCard(pan = "4242424242424241", expiry = future, cvv = "123"))
        assertIs<TokenizationResult.Failure>(result)
    }

    @Test
    fun rejects_an_expired_card() {
        // Clock is frozen at 2026-07-14, so 06/2026 is expired.
        val result = tokenizer.tokenize(RawCard(pan = "4242424242424242", expiry = CardExpiry(6, 2026), cvv = "123"))
        assertIs<TokenizationResult.Failure>(result)
    }

    @Test
    fun rejects_a_too_short_number() {
        val result = tokenizer.tokenize(RawCard(pan = "4242", expiry = future, cvv = "123"))
        assertIs<TokenizationResult.Failure>(result)
    }

    @Test
    fun rejects_an_invalid_cvv() {
        val result = tokenizer.tokenize(RawCard(pan = "4242424242424242", expiry = future, cvv = "12"))
        assertIs<TokenizationResult.Failure>(result)
    }
}
