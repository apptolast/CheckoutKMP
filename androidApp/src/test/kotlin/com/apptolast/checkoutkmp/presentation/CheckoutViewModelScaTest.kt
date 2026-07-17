package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.data.repository.PaymentRepositoryImpl
import com.apptolast.checkoutkmp.data.tokenizer.FakeCardTokenizer
import com.apptolast.checkoutkmp.domain.tokenizer.RawCard
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.usecase.CapturePaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.CompleteScaUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.RefundPaymentUseCase
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
import kotlin.test.assertTrue

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
            capturePayment = CapturePaymentUseCase(repo),
            refundPayment = RefundPaymentUseCase(repo),
            tokenizer = FakeCardTokenizer(),
            scenarioController = psp,
            // State is the source of truth; init syncs this onto the PSP.
            initialState = CheckoutState(amount = Amount(4999, Currency.EUR), scenario = PaymentScenario.NEEDS_SCA),
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
    fun correct_otp_authorizes_the_payment() = runTest {
        val (_, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()
        vm.onIntent(CheckoutIntent.SubmitOtp("123456"))
        advanceUntilIdle()

        // A card completes SCA into Authorized: funds held, charge deferred to capture.
        assertIs<CheckoutStatus.Authorized>(vm.state.value.status)
    }

    @Test
    fun wrong_otp_keeps_the_challenge_with_an_error() = runTest {
        val (_, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()
        vm.onIntent(CheckoutIntent.SubmitOtp("000000"))
        advanceUntilIdle()

        val status = assertIs<CheckoutStatus.RequiresSca>(vm.state.value.status)
        assertTrue(status.otpError, "a wrong OTP should flag an error on the retained challenge")
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

        vm.onIntent(CheckoutIntent.SelectScenario(PaymentScenario.DECLINED))

        assertEquals(PaymentScenario.DECLINED, psp.scenario)
        assertEquals(PaymentScenario.DECLINED, vm.state.value.scenario)
    }
}
