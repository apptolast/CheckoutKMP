package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.giftcard.FakeGiftCardStore
import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.data.repository.PaymentRepositoryImpl
import com.apptolast.checkoutkmp.data.tokenizer.FakeCardTokenizer
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.domain.tokenizer.RawCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end split payment (gift card + card) from the ViewModel against the real fakes:
 * coverage rules, remainder charging and — the interesting part — compensation when the card leg
 * fails after the balance was consumed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelGiftCardTest {

    private val dispatcher = StandardTestDispatcher()
    private val validCard = RawCard(pan = "4242424242424242", expiry = CardExpiry(12, 2030), cvv = "123")
    private val total = Amount(4999, Currency.EUR)
    private val balance = Amount(2500, Currency.EUR)

    private fun newViewModel(
        scenario: PaymentScenario = PaymentScenario.APPROVED,
    ): Triple<FakePsp, FakeGiftCardStore, CheckoutViewModel> {
        val psp = FakePsp(scenario = scenario)
        val store = FakeGiftCardStore(initialCards = mapOf("GIFT25" to balance, "GIFT100" to Amount(10_000, Currency.EUR)))
        val repo = PaymentRepositoryImpl(psp = psp)
        val vm = CheckoutViewModel(
            useCases = checkoutUseCases(repo, giftCards = store),
            tokenizer = FakeCardTokenizer(),
            scenarioController = psp,
            initialState = CheckoutState(amount = total, scenario = scenario),
        )
        return Triple(psp, store, vm)
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun applying_a_known_code_exposes_balance_and_remainder() = runTest {
        val (_, _, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.ApplyGiftCard("gift25"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(balance, state.giftCard?.balance)
        assertEquals(Amount(2499, Currency.EUR), state.plan.remainder)
    }

    @Test
    fun applying_an_unknown_code_flags_not_found() = runTest {
        val (_, _, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.ApplyGiftCard("NOPE"))
        advanceUntilIdle()

        assertTrue(vm.state.value.giftCardNotFound)
    }

    @Test
    fun a_covering_gift_card_pays_without_card_or_psp() = runTest {
        val (psp, store, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT100"))
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.SubmitGiftCardOnly)
        advanceUntilIdle()

        val status = assertIs<CheckoutStatus.Captured>(vm.state.value.status)
        assertIs<PaymentMethod.GiftCard>(status.receipt.method)
        assertEquals(0, psp.chargeCount, "no card charge may happen when the gift card covers the total")
        assertEquals(Amount(10_000 - 4999, Currency.EUR), store.balanceOf("GIFT100"))
    }

    @Test
    fun a_partial_gift_card_charges_only_the_remainder_on_the_card() = runTest {
        val (_, store, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT25"))
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        val status = assertIs<CheckoutStatus.Authorized>(vm.state.value.status)
        assertEquals(Amount(2499, Currency.EUR), status.receipt.amount) // the card leg = remainder
        assertEquals(balance, status.receipt.giftCard?.amount)
        assertEquals(Amount(0, Currency.EUR), store.balanceOf("GIFT25"))
    }

    @Test
    fun a_declined_card_restores_the_gift_card_balance() = runTest {
        val (_, store, vm) = newViewModel(scenario = PaymentScenario.DECLINED)
        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT25"))
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        assertIs<CheckoutStatus.Failed>(vm.state.value.status)
        // Compensation: the consumed balance came back after the card was declined.
        assertEquals(balance, store.balanceOf("GIFT25"))
        assertEquals(1, store.redemptionCount)
        assertEquals(1, store.reversalCount)
    }

    @Test
    fun cancelling_sca_after_redemption_restores_the_balance() = runTest {
        val (_, store, vm) = newViewModel(scenario = PaymentScenario.NEEDS_SCA)
        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT25"))
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()
        assertIs<CheckoutStatus.RequiresSca>(vm.state.value.status)
        assertEquals(Amount(0, Currency.EUR), store.balanceOf("GIFT25"), "balance held during SCA")

        vm.onIntent(CheckoutIntent.CancelSca)
        advanceUntilIdle()

        assertIs<CheckoutStatus.Failed>(vm.state.value.status)
        assertEquals(balance, store.balanceOf("GIFT25"))
    }

    @Test
    fun completing_sca_settles_the_split_and_keeps_the_balance_consumed() = runTest {
        val (_, store, vm) = newViewModel(scenario = PaymentScenario.NEEDS_SCA)
        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT25"))
        advanceUntilIdle()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.SubmitOtp("123456"))
        advanceUntilIdle()

        val status = assertIs<CheckoutStatus.Authorized>(vm.state.value.status)
        assertNotNull(status.receipt.giftCard, "the receipt must record the gift-card tender after SCA")
        assertEquals(Amount(0, Currency.EUR), store.balanceOf("GIFT25"))
        assertEquals(0, store.reversalCount)
    }

    @Test
    fun retrying_a_transient_split_failure_replays_the_redemption_idempotently() = runTest {
        val (_, store, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT25"))
        advanceUntilIdle()

        // The card leg fails in transit after the balance was consumed...
        vm.onIntent(CheckoutIntent.SelectScenario(PaymentScenario.NETWORK_ERROR))
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()
        assertIs<CheckoutStatus.Failed>(vm.state.value.status)
        assertEquals(Amount(0, Currency.EUR), store.balanceOf("GIFT25"), "no compensation for transients")

        // ...and the retry re-runs the whole saga with the same keys: the redemption replays,
        // only the card charge actually retries.
        vm.onIntent(CheckoutIntent.SelectScenario(PaymentScenario.APPROVED))
        vm.onIntent(CheckoutIntent.Retry)
        advanceUntilIdle()

        val status = assertIs<CheckoutStatus.Authorized>(vm.state.value.status)
        assertNotNull(status.receipt.giftCard)
        assertEquals(1, store.redemptionCount, "the balance must not be consumed twice")
        assertEquals(0, store.reversalCount)
    }

    @Test
    fun abandoning_a_transient_split_failure_restores_the_balance() = runTest {
        val (_, store, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT25"))
        advanceUntilIdle()
        vm.onIntent(CheckoutIntent.SelectScenario(PaymentScenario.NETWORK_ERROR))
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()
        assertIs<CheckoutStatus.Failed>(vm.state.value.status)

        // Giving up on the attempt compensates the consumed-but-unsettled redemption.
        vm.onIntent(CheckoutIntent.Reset)
        advanceUntilIdle()

        assertEquals(CheckoutStatus.Editing, vm.state.value.status)
        assertEquals(balance, store.balanceOf("GIFT25"))
        assertEquals(1, store.reversalCount)
    }

    @Test
    fun applying_is_busy_while_the_lookup_runs_and_clears_when_it_finishes() = runTest {
        val (_, _, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT25"))

        // Busy from the moment the intent arrives, before the lookup coroutine has run.
        assertTrue(vm.state.value.isApplyingGiftCard)
        advanceUntilIdle()

        assertFalse(vm.state.value.isApplyingGiftCard)
        assertEquals(balance, vm.state.value.giftCard?.balance)
    }

    @Test
    fun a_failed_lookup_clears_busy_and_flags_not_found() = runTest {
        val (_, _, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.ApplyGiftCard("NOPE"))
        assertTrue(vm.state.value.isApplyingGiftCard)
        advanceUntilIdle()

        assertFalse(vm.state.value.isApplyingGiftCard)
        assertTrue(vm.state.value.giftCardNotFound)
    }

    @Test
    fun apply_intents_are_ignored_while_a_lookup_is_in_flight() = runTest {
        val (_, _, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT25"))
        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT100"))
        advanceUntilIdle()

        assertEquals("GIFT25", vm.state.value.giftCard?.code, "the second tap must be a no-op")
    }

    @Test
    fun clear_gift_card_error_resets_a_not_found_verdict() = runTest {
        val (_, _, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.ApplyGiftCard("NOPE"))
        advanceUntilIdle()
        assertTrue(vm.state.value.giftCardNotFound)

        // Dispatched by the UI as soon as the user edits the code again.
        vm.onIntent(CheckoutIntent.ClearGiftCardError)

        assertFalse(vm.state.value.giftCardNotFound)
    }

    @Test
    fun reapplying_clears_the_previous_not_found_verdict_immediately() = runTest {
        val (_, _, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.ApplyGiftCard("NOPE"))
        advanceUntilIdle()
        assertTrue(vm.state.value.giftCardNotFound)

        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT25"))

        // The stale verdict is gone the moment the new lookup starts, not when it lands.
        assertFalse(vm.state.value.giftCardNotFound)
        advanceUntilIdle()
        assertEquals(balance, vm.state.value.giftCard?.balance)
    }

    @Test
    fun switching_to_a_wallet_keeps_the_gift_card_and_flags_it_ignored() = runTest {
        val (_, _, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT25"))
        advanceUntilIdle()
        assertFalse(vm.state.value.walletIgnoresGiftCard)

        vm.onIntent(CheckoutIntent.SelectMethod(MethodOption.PAYPAL))

        // The wallet pays the full total; the UI announces that the gift card is not used.
        val state = vm.state.value
        assertTrue(state.walletIgnoresGiftCard)
        assertNotNull(state.giftCard, "the applied card must survive the method switch")

        // Switching back to card resumes the split exactly as the user left it.
        vm.onIntent(CheckoutIntent.SelectMethod(MethodOption.CARD))
        assertFalse(vm.state.value.walletIgnoresGiftCard)
        assertEquals(Amount(2499, Currency.EUR), vm.state.value.plan.remainder)
    }

    @Test
    fun refunding_a_split_payment_returns_both_tenders() = runTest {
        val (psp, store, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT25"))
        advanceUntilIdle()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()
        vm.onIntent(CheckoutIntent.Capture)
        advanceUntilIdle()
        assertIs<CheckoutStatus.Captured>(vm.state.value.status)

        vm.onIntent(CheckoutIntent.Refund)
        advanceUntilIdle()

        assertIs<CheckoutStatus.Refunded>(vm.state.value.status)
        assertEquals(1, psp.refundCount) // card portion via the PSP
        assertEquals(balance, store.balanceOf("GIFT25")) // gift portion back to its card
    }
}
