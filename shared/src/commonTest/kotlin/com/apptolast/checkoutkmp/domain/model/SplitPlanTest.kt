package com.apptolast.checkoutkmp.domain.model

import com.apptolast.checkoutkmp.support.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The tender split: gift card applied first (capped at its balance), the card pays the rest. */
class SplitPlanTest {

    private val total = Fixtures.amount // 10.50 EUR

    @Test
    fun no_gift_card_leaves_the_whole_total_on_the_card() {
        val plan = planSplit(total, giftCard = null)

        assertEquals(Amount(0, Currency.EUR), plan.giftCardPortion)
        assertEquals(total, plan.remainder)
        assertFalse(plan.coversTotal)
    }

    @Test
    fun partial_balance_is_consumed_first_and_the_card_pays_the_remainder() {
        val plan = planSplit(total, Fixtures.partialGiftCard) // 4.00 EUR balance

        assertEquals(Amount(400, Currency.EUR), plan.giftCardPortion)
        assertEquals(Amount(650, Currency.EUR), plan.remainder)
        assertFalse(plan.coversTotal)
    }

    @Test
    fun covering_balance_is_capped_at_the_total_and_needs_no_card() {
        val plan = planSplit(total, Fixtures.coveringGiftCard) // 20.00 EUR balance

        assertEquals(total, plan.giftCardPortion) // only the total is consumed, not the full balance
        assertTrue(plan.remainder.isZero)
        assertTrue(plan.coversTotal)
    }

    @Test
    fun exact_balance_covers_the_total() {
        val plan = planSplit(total, GiftCard(code = "GIFT-EXACT", balance = total))

        assertEquals(total, plan.giftCardPortion)
        assertTrue(plan.coversTotal)
    }

    @Test
    fun mixed_currencies_are_rejected() {
        val usdCard = GiftCard(code = "GIFT-USD", balance = Amount(1000, Currency.USD))

        assertFailsWith<IllegalArgumentException> { planSplit(total, usdCard) }
    }
}
