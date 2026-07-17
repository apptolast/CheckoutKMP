package com.apptolast.checkoutkmp.security

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
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
import com.apptolast.checkoutkmp.domain.model.PaymentResult
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

        // Drive a full retail lifecycle so every state that carries a receipt gets checked.
        val repo = PaymentRepositoryImpl(FakePsp(scenario = PaymentScenario.APPROVED))
        val authorized = repo.authorize(request)
        val receipt = assertIs<PaymentResult.Authorized>(authorized).receipt
        val captured = repo.capture(receipt, IdempotencyKey.random())
        val refunded = repo.refund(
            assertIs<PaymentResult.Captured>(captured).receipt,
            IdempotencyKey.random(),
        )
        val challenge = PaymentRepositoryImpl(FakePsp(scenario = PaymentScenario.NEEDS_SCA)).authorize(request)

        val states = listOf(
            PaymentState.Idle,
            PaymentState.Processing,
            authorized.toPaymentState(),
            captured.toPaymentState(),
            refunded.toPaymentState(),
            challenge.toPaymentState(),
        )
        states.forEach { assertNoPan(it.toString()) }
    }

    // Opaque tokens and idempotency keys are hyphenated UUIDs; their random hex can hold digit runs
    // that are not card data. Strip them before the heuristic so it only flags a genuine PAN.
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    private fun assertNoPan(text: String) {
        assertFalse(text.contains(pan), "PAN leaked in: $text")
        // Also guard against any long digit run that could be a card number (UUIDs scrubbed first).
        val scrubbed = uuid.replace(text, "<uuid>")
        assertFalse(Regex("\\d{12,}").containsMatchIn(scrubbed), "Long digit run (possible PAN) in: $text")
    }
}
