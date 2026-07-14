package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.data.psp.PspScenario
import com.apptolast.checkoutkmp.data.repository.PaymentRepositoryImpl
import com.apptolast.checkoutkmp.data.tokenizer.FakeCardTokenizer
import com.apptolast.checkoutkmp.data.tokenizer.RawCard
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.domain.model.PaymentError
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
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelScaTest {

    private val dispatcher = StandardTestDispatcher()
    private val validCard = RawCard(pan = "4242424242424242", expiry = CardExpiry(12, 2030), cvv = "123")

    private fun newViewModel(): Pair<FakePsp, CheckoutViewModel> {
        val psp = FakePsp()
        val repo = PaymentRepositoryImpl(psp = psp)
        val vm = CheckoutViewModel(
            processPayment = ProcessPaymentUseCase(repo),
            completeSca = CompleteScaUseCase(repo),
            tokenizer = FakeCardTokenizer(),
            scenarioController = psp,
            // State is the source of truth; init syncs this onto the PSP.
            initialState = CheckoutState(amount = Amount(4999, Currency.EUR), scenario = PspScenario.NEEDS_SCA),
        )
        return psp to vm
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun needs_sca_scenario_moves_to_the_challenge() = runTest {
        val (_, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        assertIs<CheckoutStatus.RequiresSca>(vm.state.value.status)
    }

    @Test
    fun correct_otp_approves_the_payment() = runTest {
        val (_, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()
        vm.onIntent(CheckoutIntent.SubmitOtp("123456"))
        advanceUntilIdle()

        assertIs<CheckoutStatus.Approved>(vm.state.value.status)
    }

    @Test
    fun wrong_otp_keeps_the_challenge_with_an_error() = runTest {
        val (_, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()
        vm.onIntent(CheckoutIntent.SubmitOtp("000000"))
        advanceUntilIdle()

        val status = assertIs<CheckoutStatus.RequiresSca>(vm.state.value.status)
        assertEquals("Incorrect code, try again.", status.otpError)
    }

    @Test
    fun cancelling_sca_fails_with_cancelled() = runTest {
        val (_, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()
        vm.onIntent(CheckoutIntent.CancelSca)

        val status = assertIs<CheckoutStatus.Failed>(vm.state.value.status)
        assertEquals(PaymentError.Cancelled, status.error)
    }

    @Test
    fun selecting_a_scenario_updates_the_psp() = runTest {
        val (psp, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.SelectScenario(PspScenario.DECLINED))

        assertEquals(PspScenario.DECLINED, psp.scenario)
        assertEquals(PspScenario.DECLINED, vm.state.value.scenario)
    }
}
