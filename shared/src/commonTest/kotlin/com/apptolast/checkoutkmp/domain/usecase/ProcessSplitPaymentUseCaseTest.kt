package com.apptolast.checkoutkmp.domain.usecase

import app.cash.turbine.test
import com.apptolast.checkoutkmp.domain.giftcard.RedemptionResult
import com.apptolast.checkoutkmp.domain.giftcard.ReversalResult
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.domain.model.GiftCardTender
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.support.FakeGiftCardService
import com.apptolast.checkoutkmp.support.FakePaymentRepository
import com.apptolast.checkoutkmp.support.Fixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class ProcessSplitPaymentUseCaseTest {

    private val total = Fixtures.amount // 10.50 EUR
    private val remainder = Amount(650, Currency.EUR) // total - partial gift card (4.00)

    private fun useCase(giftCards: FakeGiftCardService, repo: FakePaymentRepository) =
        ProcessSplitPaymentUseCase(giftCards, repo)

    private fun redeemOk() = { code: String, amount: Amount, _: IdempotencyKey ->
        RedemptionResult.Success(Fixtures.redemption(amount, code))
    }

    @Test
    fun full_coverage_settles_at_once_without_card_or_sca() = runTest {
        val giftCards = FakeGiftCardService(onRedeem = redeemOk())
        val repo = FakePaymentRepository() // authorize not scripted: calling it would fail the test

        val events = mutableListOf<SplitPaymentEvent>()
        useCase(giftCards, repo)(
            total = total,
            giftCard = Fixtures.coveringGiftCard,
            cardRequest = null,
            redemptionKey = IdempotencyKey.random(),
            reversalKey = IdempotencyKey.random(),
        ).test {
            assertEquals(SplitPaymentEvent.StateChanged(PaymentState.Processing), awaitItem())
            events += awaitItem() // GiftCardRedeemed
            events += awaitItem() // Captured
            awaitComplete()
        }

        assertIs<SplitPaymentEvent.GiftCardRedeemed>(events[0])
        val captured = assertIs<SplitPaymentEvent.StateChanged>(events[1]).state
        val receipt = assertIs<PaymentState.Captured>(captured).receipt
        assertIs<PaymentMethod.GiftCard>(receipt.method)
        assertEquals(total, receipt.amount)
        assertEquals(total, receipt.giftCard?.amount) // capped at the total, not the full balance
        assertEquals(null, receipt.authCode)
        assertTrue(repo.authorizeCalls.isEmpty(), "a covering gift card must not touch the card/PSP at all")
        assertEquals(listOf(total), giftCards.redeemCalls.map { it.second })
    }

    @Test
    fun partial_coverage_redeems_first_then_charges_the_remainder() = runTest {
        val giftCards = FakeGiftCardService(onRedeem = redeemOk())
        val repo = FakePaymentRepository(onAuthorize = { request ->
            PaymentResult.Authorized(Fixtures.receipt.copy(amount = request.amount))
        })
        val cardRequest = Fixtures.request().copy(amount = remainder)

        var settled: PaymentState? = null
        useCase(giftCards, repo)(
            total = total,
            giftCard = Fixtures.partialGiftCard,
            cardRequest = cardRequest,
            redemptionKey = IdempotencyKey.random(),
            reversalKey = IdempotencyKey.random(),
        ).test {
            awaitItem() // Processing
            awaitItem() // GiftCardRedeemed
            settled = assertIs<SplitPaymentEvent.StateChanged>(awaitItem()).state
            awaitComplete()
        }

        // Order of application: the gift card was consumed before the card was charged.
        assertEquals(Amount(400, Currency.EUR), giftCards.redeemCalls.single().second)
        assertEquals(remainder, repo.authorizeCalls.single().amount)

        val receipt = assertIs<PaymentState.Authorized>(settled).receipt
        assertEquals(GiftCardTender("gcr_1", Amount(400, Currency.EUR)), receipt.giftCard)
    }

    @Test
    fun declined_card_reverses_the_redemption_before_surfacing_the_failure() = runTest {
        var reversedAtCall = -1
        var calls = 0
        val giftCards = FakeGiftCardService(
            onRedeem = redeemOk(),
            onReverse = { _, _ ->
                reversedAtCall = ++calls
                ReversalResult.Success(reversedAt = Instant.fromEpochSeconds(1_700_000_060))
            },
        )
        val error = PaymentError.Declined("insufficient_funds")
        val repo = FakePaymentRepository(onAuthorize = { PaymentResult.Failed(error) })
        val reversalKey = IdempotencyKey.random()

        var last: PaymentState? = null
        useCase(giftCards, repo)(
            total = total,
            giftCard = Fixtures.partialGiftCard,
            cardRequest = Fixtures.request().copy(amount = remainder),
            redemptionKey = IdempotencyKey.random(),
            reversalKey = reversalKey,
        ).test {
            awaitItem() // Processing
            awaitItem() // GiftCardRedeemed
            last = assertIs<SplitPaymentEvent.StateChanged>(awaitItem()).state
            awaitComplete()
        }

        // Compensation: the consumed balance was returned, with the attempt's own reversal key.
        assertEquals(listOf("gcr_1" to reversalKey), giftCards.reverseCalls)
        assertEquals(1, reversedAtCall, "the reversal must happen before the failure is emitted")
        assertEquals(PaymentState.Failed(error), last)
    }

    @Test
    fun transient_card_failure_keeps_the_redemption_for_an_idempotent_retry() = runTest {
        val giftCards = FakeGiftCardService(onRedeem = redeemOk())
        val repo = FakePaymentRepository(onAuthorize = { PaymentResult.Failed(PaymentError.Network) })

        useCase(giftCards, repo)(
            total = total,
            giftCard = Fixtures.partialGiftCard,
            cardRequest = Fixtures.request().copy(amount = remainder),
            redemptionKey = IdempotencyKey.random(),
            reversalKey = IdempotencyKey.random(),
        ).test {
            awaitItem() // Processing
            awaitItem() // GiftCardRedeemed
            assertEquals(
                SplitPaymentEvent.StateChanged(PaymentState.Failed(PaymentError.Network)),
                awaitItem(),
            )
            awaitComplete()
        }

        // No compensation: re-running the saga with the same keys replays the redemption and
        // retries only the card charge.
        assertTrue(giftCards.reverseCalls.isEmpty())
    }

    @Test
    fun failed_redemption_surfaces_without_touching_the_card() = runTest {
        val error = PaymentError.Declined("gift_card_insufficient_balance")
        val giftCards = FakeGiftCardService(onRedeem = { _, _, _ -> RedemptionResult.Failure(error) })
        val repo = FakePaymentRepository()

        useCase(giftCards, repo)(
            total = total,
            giftCard = Fixtures.partialGiftCard,
            cardRequest = Fixtures.request().copy(amount = remainder),
            redemptionKey = IdempotencyKey.random(),
            reversalKey = IdempotencyKey.random(),
        ).test {
            awaitItem() // Processing
            assertEquals(SplitPaymentEvent.StateChanged(PaymentState.Failed(error)), awaitItem())
            awaitComplete()
        }

        assertTrue(repo.authorizeCalls.isEmpty())
        assertTrue(giftCards.reverseCalls.isEmpty())
    }

    @Test
    fun sca_challenge_keeps_the_redemption_pending() = runTest {
        val giftCards = FakeGiftCardService(onRedeem = redeemOk())
        val repo = FakePaymentRepository(onAuthorize = { PaymentResult.RequiresSca(Fixtures.challenge) })

        useCase(giftCards, repo)(
            total = total,
            giftCard = Fixtures.partialGiftCard,
            cardRequest = Fixtures.request().copy(amount = remainder),
            redemptionKey = IdempotencyKey.random(),
            reversalKey = IdempotencyKey.random(),
        ).test {
            awaitItem() // Processing
            awaitItem() // GiftCardRedeemed
            assertEquals(
                SplitPaymentEvent.StateChanged(PaymentState.RequiresSca(Fixtures.challenge)),
                awaitItem(),
            )
            awaitComplete()
        }

        // The challenge may still succeed — compensation is the caller's decision on abandon.
        assertTrue(giftCards.reverseCalls.isEmpty())
    }
}
