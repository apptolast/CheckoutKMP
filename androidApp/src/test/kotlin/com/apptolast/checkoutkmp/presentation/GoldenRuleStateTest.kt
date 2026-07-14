package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.data.psp.PspScenario
import com.apptolast.checkoutkmp.data.repository.PaymentRepositoryImpl
import com.apptolast.checkoutkmp.data.tokenizer.FakeCardTokenizer
import com.apptolast.checkoutkmp.domain.tokenizer.RawCard
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.domain.usecase.CompleteScaUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessPaymentUseCase
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

/**
 * End-to-end golden rule guard: a PAN typed into the form must never end up in the exposed MVI
 * state (which is what could be logged or restored). Only the masked last four may appear.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoldenRuleStateTest {

    private val dispatcher = StandardTestDispatcher()
    private val pan = "4111111111111111"

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun exposed_state_never_contains_the_pan_after_paying() = runTest {
        val psp = FakePsp(scenario = PspScenario.APPROVED)
        val repo = PaymentRepositoryImpl(psp = psp)
        val vm = CheckoutViewModel(
            processPayment = ProcessPaymentUseCase(repo),
            completeSca = CompleteScaUseCase(repo),
            tokenizer = FakeCardTokenizer(),
            scenarioController = psp,
            initialState = CheckoutState(amount = Amount(4999, Currency.EUR)),
        )

        vm.onIntent(CheckoutIntent.Submit(RawCard(pan = pan, expiry = CardExpiry(12, 2030), cvv = "123")))
        advanceUntilIdle()

        val rendered = vm.state.value.toString()
        assertFalse(rendered.contains(pan), "PAN leaked into state: $rendered")
        assertFalse(Regex("\\d{12,}").containsMatchIn(rendered), "Long digit run in state: $rendered")
    }
}
