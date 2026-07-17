package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.data.repository.PaymentRepositoryImpl
import com.apptolast.checkoutkmp.data.tokenizer.FakeCardTokenizer
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.RedirectReturn
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
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
import kotlin.test.assertNotNull

/**
 * Redirect wallet flow from the ViewModel: order creation, the simulated provider return, and the
 * reconciliation where the webhook — not the user's claim — decides the outcome.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelRedirectTest {

    private val dispatcher = StandardTestDispatcher()

    private fun newViewModel(
        scenario: PaymentScenario = PaymentScenario.APPROVED,
    ): Pair<FakePsp, CheckoutViewModel> {
        val psp = FakePsp(scenario = scenario)
        val repo = PaymentRepositoryImpl(psp = psp)
        val vm = CheckoutViewModel(
            useCases = checkoutUseCases(repo),
            tokenizer = FakeCardTokenizer(),
            scenarioController = psp,
            initialState = CheckoutState(amount = Amount(4999, Currency.EUR), scenario = scenario),
        )
        return psp to vm
    }

    private fun CheckoutViewModel.startPaypalPayment() {
        onIntent(CheckoutIntent.SelectMethod(MethodOption.PAYPAL))
        onIntent(CheckoutIntent.SubmitWallet)
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun paying_with_a_wallet_lands_on_the_redirect_screen_without_charging() = runTest {
        val (psp, vm) = newViewModel()

        vm.startPaypalPayment()
        advanceUntilIdle()

        assertIs<CheckoutStatus.RequiresRedirect>(vm.state.value.status)
        assertEquals(0, psp.chargeCount)
    }

    @Test
    fun approved_return_confirmed_by_webhook_settles_to_captured() = runTest {
        val (psp, vm) = newViewModel()
        vm.startPaypalPayment()
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.CompleteRedirect(RedirectReturn.APPROVED))
        advanceUntilIdle()

        // Immediate capture: a wallet never passes through Authorized.
        val status = assertIs<CheckoutStatus.Captured>(vm.state.value.status)
        assertNotNull(status.receipt.capturedAt)
        assertIs<PaymentMethod.Wallet>(status.receipt.method)
        assertEquals(1, psp.chargeCount)
    }

    @Test
    fun cancelling_at_the_provider_fails_with_cancelled() = runTest {
        val (psp, vm) = newViewModel()
        vm.startPaypalPayment()
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.CompleteRedirect(RedirectReturn.CANCELLED))
        advanceUntilIdle()

        val status = assertIs<CheckoutStatus.Failed>(vm.state.value.status)
        assertEquals(PaymentError.Cancelled, status.error)
        assertEquals(0, psp.chargeCount)
    }

    @Test
    fun approved_return_rejected_by_webhook_fails_and_never_charges() = runTest {
        // The webhook (recorded when the provider order is created) says REJECTED...
        val (psp, vm) = newViewModel(scenario = PaymentScenario.DECLINED)
        vm.startPaypalPayment()
        advanceUntilIdle()
        assertIs<CheckoutStatus.RequiresRedirect>(vm.state.value.status)

        // ...even though the user comes back claiming the payment went through.
        vm.onIntent(CheckoutIntent.CompleteRedirect(RedirectReturn.APPROVED))
        advanceUntilIdle()

        val status = assertIs<CheckoutStatus.Failed>(vm.state.value.status)
        assertIs<PaymentError.Declined>(status.error)
        assertEquals(0, psp.chargeCount, "an unconfirmed payment must never charge")
    }
}
