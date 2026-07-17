package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.giftcard.FakeGiftCardStore
import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.data.repository.PaymentRepositoryImpl
import com.apptolast.checkoutkmp.data.tokenizer.FakeCardTokenizer
import com.apptolast.checkoutkmp.domain.tokenizer.RawCard
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.model.Currency
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
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * End-to-end golden rule guard: a PAN typed into the form must never end up in the exposed MVI
 * state (which is what could be logged or restored) — through the whole retail lifecycle:
 * authorization, capture, refund and split payments. Only the masked last four may appear.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoldenRuleStateTest {

    private val dispatcher = StandardTestDispatcher()
    private val pan = "4111111111111111"
    private val validCard = RawCard(pan = pan, expiry = CardExpiry(12, 2030), cvv = "123")

    private fun newViewModel(): CheckoutViewModel {
        val psp = FakePsp(scenario = PaymentScenario.APPROVED)
        val repo = PaymentRepositoryImpl(psp = psp)
        return CheckoutViewModel(
            useCases = checkoutUseCases(repo, giftCards = FakeGiftCardStore()),
            tokenizer = FakeCardTokenizer(),
            scenarioController = psp,
            initialState = CheckoutState(amount = Amount(4999, Currency.EUR)),
        )
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun exposed_state_never_contains_the_pan_after_paying() = runTest {
        val vm = newViewModel()

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        assertIs<CheckoutStatus.Authorized>(vm.state.value.status)
        assertNoPan(vm.state.value.toString())
    }

    @Test
    fun exposed_state_never_contains_the_pan_through_capture_and_refund() = runTest {
        val vm = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.Capture)
        advanceUntilIdle()
        assertIs<CheckoutStatus.Captured>(vm.state.value.status)
        assertNoPan(vm.state.value.toString())

        vm.onIntent(CheckoutIntent.Refund)
        advanceUntilIdle()
        assertIs<CheckoutStatus.Refunded>(vm.state.value.status)
        assertNoPan(vm.state.value.toString())
    }

    @Test
    fun exposed_state_never_contains_the_pan_in_a_split_payment() = runTest {
        val vm = newViewModel()
        vm.onIntent(CheckoutIntent.ApplyGiftCard("GIFT25"))
        advanceUntilIdle()
        assertNoPan(vm.state.value.toString())

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        assertIs<CheckoutStatus.Authorized>(vm.state.value.status)
        assertNoPan(vm.state.value.toString())
    }

    // Payment/redemption ids embed hyphenated UUIDs whose random hex can hold digit runs that are
    // not card data. Scrub them before the heuristic so it only flags a genuine PAN (this exact
    // false positive made the plain \d{12,} check flaky ~0.4% of runs).
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    private fun assertNoPan(rendered: String) {
        assertFalse(rendered.contains(pan), "PAN leaked into state: $rendered")
        val scrubbed = uuid.replace(rendered, "<uuid>")
        assertFalse(Regex("\\d{12,}").containsMatchIn(scrubbed), "Long digit run in state: $rendered")
    }
}
