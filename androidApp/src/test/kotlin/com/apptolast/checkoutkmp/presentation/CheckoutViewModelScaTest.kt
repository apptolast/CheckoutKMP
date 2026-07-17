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
import com.apptolast.checkoutkmp.domain.simulation.DemoDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelScaTest {

    private val dispatcher = StandardTestDispatcher()
    private val validCard = RawCard(pan = "4242424242424242", expiry = CardExpiry(12, 2030), cvv = "123")

    private fun newViewModel(): Pair<FakePsp, CheckoutViewModel> {
        val psp = FakePsp()
        val repo = PaymentRepositoryImpl(psp = psp)
        val vm = CheckoutViewModel(
            useCases = checkoutUseCases(repo),
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

    // --- Resend cooldown: virtual time on the injected main dispatcher drives every tick ---

    private val cooldownSeconds = DemoDefaults.OTP_RESEND_COOLDOWN.inWholeSeconds.toInt()

    private fun scaStatus(vm: CheckoutViewModel): CheckoutStatus.RequiresSca =
        assertIs<CheckoutStatus.RequiresSca>(vm.state.value.status)

    @Test
    fun resend_countdown_starts_with_the_challenge_and_ticks_down_to_zero() = runTest {
        val (_, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.Submit(validCard))
        runCurrent()
        assertEquals(cooldownSeconds, scaStatus(vm).resendSecondsLeft)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(cooldownSeconds - 1, scaStatus(vm).resendSecondsLeft)

        advanceTimeBy((cooldownSeconds - 1).seconds)
        runCurrent()
        assertEquals(0, scaStatus(vm).resendSecondsLeft, "the resend action unlocks at zero")
    }

    @Test
    fun resend_is_ignored_while_the_cooldown_is_running() = runTest {
        val (_, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        runCurrent()

        vm.onIntent(CheckoutIntent.ResendOtp)
        runCurrent()

        val status = scaStatus(vm)
        assertFalse(status.otpResent, "no reissue may happen while the cooldown is ticking")
        assertEquals(cooldownSeconds, status.resendSecondsLeft, "the cooldown must not restart")
    }

    @Test
    fun resend_reissues_the_challenge_clears_the_error_and_restarts_the_cooldown() = runTest {
        val (_, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle() // challenge shown, cooldown fully drained to 0
        vm.onIntent(CheckoutIntent.SubmitOtp("000000"))
        advanceUntilIdle()
        val before = scaStatus(vm)
        assertTrue(before.otpError)
        assertEquals(0, before.resendSecondsLeft)

        vm.onIntent(CheckoutIntent.ResendOtp)
        runCurrent()

        val after = scaStatus(vm)
        assertTrue(after.otpResent, "a successful reissue announces itself")
        assertFalse(after.otpError, "a fresh delivery voids the stale wrong-code verdict")
        assertEquals(before.challenge, after.challenge, "same challenge — only the delivery repeats")
        assertEquals(cooldownSeconds, after.resendSecondsLeft, "the cooldown restarts in full")
    }

    @Test
    fun the_original_otp_still_authorizes_after_a_resend() = runTest {
        val (psp, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.ResendOtp)
        runCurrent()
        vm.onIntent(CheckoutIntent.SubmitOtp(DemoDefaults.SCA_OTP))
        advanceUntilIdle()

        assertIs<CheckoutStatus.Authorized>(vm.state.value.status)
        assertEquals(1, psp.chargeCount, "reissuing must never create a second charge")
    }

    @Test
    fun verifying_a_wrong_otp_does_not_reset_the_ticking_cooldown() = runTest {
        val (_, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        runCurrent() // cooldown at full value, still ticking

        vm.onIntent(CheckoutIntent.SubmitOtp("000000"))
        advanceTimeBy(1.seconds)
        runCurrent()

        val status = scaStatus(vm)
        assertTrue(status.otpError)
        assertEquals(cooldownSeconds - 1, status.resendSecondsLeft, "the countdown kept its pace")
    }
}
