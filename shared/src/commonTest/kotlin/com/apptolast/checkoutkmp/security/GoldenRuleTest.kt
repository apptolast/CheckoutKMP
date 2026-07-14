package com.apptolast.checkoutkmp.security

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.data.psp.PspScenario
import com.apptolast.checkoutkmp.data.repository.PaymentRepositoryImpl
import com.apptolast.checkoutkmp.data.tokenizer.FakeCardTokenizer
import com.apptolast.checkoutkmp.domain.tokenizer.RawCard
import com.apptolast.checkoutkmp.domain.tokenizer.TokenizationResult
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.model.toPaymentState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Golden rule guard: the PAN must never appear in a token, a request, a receipt, or any state that
 * could be logged or persisted. Only the masked last four digits are allowed to surface.
 */
class GoldenRuleTest {

    private val pan = "4111111111111111"
    private val last4 = "1111"

    private fun tokenize() =
        assertIs<TokenizationResult.Success>(
            FakeCardTokenizer().tokenize(RawCard(pan = pan, expiry = CardExpiry(12, 2030), cvv = "123")),
        ).token

    @Test
    fun token_and_request_never_expose_the_pan() {
        val token = tokenize()
        val request = PaymentRequest(
            amount = Amount(4999, Currency.EUR),
            method = PaymentMethod.Card(token),
            idempotencyKey = IdempotencyKey.random(),
        )

        // The opaque token and everything built from it must not contain the PAN...
        assertNoPan(token.toString())
        assertNoPan(token.value)
        assertNoPan(request.toString())
        // ...but the masked last four are allowed.
        assertTrue(token.masked.contains(last4))
    }

    @Test
    fun no_payment_state_exposes_the_pan() = runTest {
        val token = tokenize()
        val request = PaymentRequest(
            amount = Amount(4999, Currency.EUR),
            method = PaymentMethod.Card(token),
            idempotencyKey = IdempotencyKey.random(),
        )

        val approved = PaymentRepositoryImpl(FakePsp(scenario = PspScenario.APPROVED)).authorize(request)
        val challenge = PaymentRepositoryImpl(FakePsp(scenario = PspScenario.NEEDS_SCA)).authorize(request)

        val states = listOf(
            PaymentState.Idle,
            PaymentState.Processing,
            approved.toPaymentState(),
            challenge.toPaymentState(),
        )
        states.forEach { assertNoPan(it.toString()) }
    }

    private fun assertNoPan(text: String) {
        assertFalse(text.contains(pan), "PAN leaked in: $text")
        // Also guard against any long digit run that could be a card number.
        assertFalse(Regex("\\d{12,}").containsMatchIn(text), "Long digit run (possible PAN) in: $text")
    }
}
