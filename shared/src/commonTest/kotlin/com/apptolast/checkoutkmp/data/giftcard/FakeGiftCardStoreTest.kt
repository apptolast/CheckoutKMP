package com.apptolast.checkoutkmp.data.giftcard

import com.apptolast.checkoutkmp.domain.giftcard.GiftCardLookup
import com.apptolast.checkoutkmp.domain.giftcard.RedemptionResult
import com.apptolast.checkoutkmp.domain.giftcard.ReversalResult
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.support.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FakeGiftCardStoreTest {

    private val eur50 = Amount(5000, Currency.EUR)
    private val store = FakeGiftCardStore(
        initialCards = mapOf("GIFT50" to eur50),
        clock = FixedClock.default,
    )

    private suspend fun redeemed(amount: Amount, key: IdempotencyKey = IdempotencyKey.random()) =
        assertIs<RedemptionResult.Success>(store.redeem("GIFT50", amount, key)).redemption

    @Test
    fun lookup_normalizes_the_code_and_reports_the_balance() = runTest {
        val found = assertIs<GiftCardLookup.Found>(store.lookup("  gift50 "))
        assertEquals("GIFT50", found.card.code)
        assertEquals(eur50, found.card.balance)

        assertIs<GiftCardLookup.NotFound>(store.lookup("NOPE"))
    }

    @Test
    fun redeeming_deducts_the_balance() = runTest {
        redeemed(Amount(2000, Currency.EUR))

        assertEquals(Amount(3000, Currency.EUR), store.balanceOf("GIFT50"))
        assertEquals(1, store.redemptionCount)
    }

    @Test
    fun redeeming_twice_with_the_same_key_consumes_only_once() = runTest {
        val key = IdempotencyKey.random()

        val first = redeemed(Amount(2000, Currency.EUR), key)
        val second = redeemed(Amount(2000, Currency.EUR), key) // idempotent replay

        assertEquals(first.redemptionId, second.redemptionId)
        assertEquals(1, store.redemptionCount)
        assertEquals(Amount(3000, Currency.EUR), store.balanceOf("GIFT50"))
    }

    @Test
    fun redeeming_more_than_the_balance_is_declined_and_consumes_nothing() = runTest {
        val result = store.redeem("GIFT50", Amount(6000, Currency.EUR), IdempotencyKey.random())

        val error = assertIs<RedemptionResult.Failure>(result).error
        assertEquals(PaymentError.Declined("gift_card_insufficient_balance"), error)
        assertEquals(eur50, store.balanceOf("GIFT50"))
        assertEquals(0, store.redemptionCount)
    }

    @Test
    fun redeeming_an_unknown_code_is_declined() = runTest {
        val result = store.redeem("NOPE", Amount(100, Currency.EUR), IdempotencyKey.random())

        val error = assertIs<RedemptionResult.Failure>(result).error
        assertEquals(PaymentError.Declined("gift_card_not_found"), error)
    }

    @Test
    fun reversing_restores_the_balance() = runTest {
        val redemption = redeemed(Amount(2000, Currency.EUR))

        assertIs<ReversalResult.Success>(store.reverse(redemption.redemptionId, IdempotencyKey.random()))

        assertEquals(eur50, store.balanceOf("GIFT50"))
        assertEquals(1, store.reversalCount)
    }

    @Test
    fun reversing_twice_with_the_same_key_restores_only_once() = runTest {
        val redemption = redeemed(Amount(2000, Currency.EUR))
        val key = IdempotencyKey.random()

        assertIs<ReversalResult.Success>(store.reverse(redemption.redemptionId, key))
        assertIs<ReversalResult.Success>(store.reverse(redemption.redemptionId, key)) // replay

        assertEquals(eur50, store.balanceOf("GIFT50"))
        assertEquals(1, store.reversalCount)
    }

    @Test
    fun a_redemption_can_only_be_reversed_once() = runTest {
        val redemption = redeemed(Amount(2000, Currency.EUR))
        assertIs<ReversalResult.Success>(store.reverse(redemption.redemptionId, IdempotencyKey.random()))

        val second = store.reverse(redemption.redemptionId, IdempotencyKey.random()) // new key

        val error = assertIs<ReversalResult.Failure>(second).error
        assertEquals(PaymentError.Declined("already_reversed"), error)
        assertEquals(eur50, store.balanceOf("GIFT50"), "the balance must not be restored twice")
    }

    @Test
    fun reversing_an_unknown_redemption_fails() = runTest {
        val result = store.reverse("gcr_nope", IdempotencyKey.random())

        assertIs<ReversalResult.Failure>(result)
    }
}
