package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.data.repository.PaymentRepositoryImpl
import com.apptolast.checkoutkmp.data.repository.RetryingPaymentRepository
import com.apptolast.checkoutkmp.data.tokenizer.FakeCardTokenizer
import com.apptolast.checkoutkmp.domain.tokenizer.RawCard
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.domain.model.PaymentError
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
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelRetryTest {

    private val dispatcher = StandardTestDispatcher()
    private val validCard = RawCard(pan = "4242424242424242", expiry = CardExpiry(12, 2030), cvv = "123")

    private fun viewModel(scenario: PaymentScenario): CheckoutViewModel {
        val psp = FakePsp()
        // Mirror production wiring, but retry instantly (no real backoff) under the test.
        val repo = RetryingPaymentRepository(PaymentRepositoryImpl(psp = psp), onDelay = {})
        return CheckoutViewModel(
            useCases = checkoutUseCases(repo),
            tokenizer = FakeCardTokenizer(),
            scenarioController = psp,
            initialState = CheckoutState(amount = Amount(4999, Currency.EUR), scenario = scenario),
        )
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun a_persistent_transient_failure_surfaces_after_retries() = runTest {
        val vm = viewModel(PaymentScenario.NETWORK_ERROR)

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        val status = assertIs<CheckoutStatus.Failed>(vm.state.value.status)
        assertEquals(PaymentError.Network, status.error)
    }

    @Test
    fun retrying_a_transient_failure_keeps_trying_the_same_way() = runTest {
        val vm = viewModel(PaymentScenario.NETWORK_ERROR)

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()
        vm.onIntent(CheckoutIntent.Retry)
        advanceUntilIdle()

        // Still a transient failure (scenario unchanged), i.e. retry re-ran the authorization.
        val status = assertIs<CheckoutStatus.Failed>(vm.state.value.status)
        assertEquals(PaymentError.Network, status.error)
    }

    @Test
    fun retrying_a_decline_returns_to_editing() = runTest {
        val vm = viewModel(PaymentScenario.DECLINED)

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()
        assertIs<CheckoutStatus.Failed>(vm.state.value.status)

        vm.onIntent(CheckoutIntent.Retry) // non-transient -> no auto-retry
        advanceUntilIdle()

        assertEquals(CheckoutStatus.Editing, vm.state.value.status)
    }
}
