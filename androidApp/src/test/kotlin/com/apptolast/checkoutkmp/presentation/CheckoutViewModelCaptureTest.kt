package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.data.repository.PaymentRepositoryImpl
import com.apptolast.checkoutkmp.data.tokenizer.FakeCardTokenizer
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.model.Currency
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Drives the retail settlement flow from the ViewModel: authorized → captured → refunded. */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelCaptureTest {

    private val dispatcher = StandardTestDispatcher()
    private val validCard = RawCard(pan = "4242424242424242", expiry = CardExpiry(12, 2030), cvv = "123")

    private fun newViewModel(): Pair<FakePsp, CheckoutViewModel> {
        val psp = FakePsp()
        val repo = PaymentRepositoryImpl(psp = psp)
        val vm = CheckoutViewModel(
            useCases = checkoutUseCases(repo),
            tokenizer = FakeCardTokenizer(),
            scenarioController = psp,
            initialState = CheckoutState(amount = Amount(4999, Currency.EUR)),
        )
        return psp to vm
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun a_card_payment_lands_on_authorized_not_charged() = runTest {
        val (_, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        val status = assertIs<CheckoutStatus.Authorized>(vm.state.value.status)
        assertEquals(null, status.receipt.capturedAt)
    }

    @Test
    fun simulating_dispatch_captures_the_payment() = runTest {
        val (psp, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.Capture)
        advanceUntilIdle()

        val status = assertIs<CheckoutStatus.Captured>(vm.state.value.status)
        assertNotNull(status.receipt.capturedAt)
        assertEquals(1, psp.captureCount)
    }

    @Test
    fun refunding_a_captured_payment_lands_on_refunded() = runTest {
        val (psp, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()
        vm.onIntent(CheckoutIntent.Capture)
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.Refund)
        advanceUntilIdle()

        val status = assertIs<CheckoutStatus.Refunded>(vm.state.value.status)
        assertNotNull(status.receipt.refundedAt)
        assertEquals(1, psp.refundCount)
    }

    @Test
    fun a_failed_capture_keeps_the_receipt_with_an_inline_error() = runTest {
        val (psp, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        psp.scenario = PaymentScenario.NETWORK_ERROR
        vm.onIntent(CheckoutIntent.Capture)
        advanceUntilIdle()

        // The receipt stays on screen so tapping again retries with the same capture key.
        val status = assertIs<CheckoutStatus.Authorized>(vm.state.value.status)
        assertNotNull(status.captureError)
        assertEquals(0, psp.captureCount)
    }

    @Test
    fun retrying_a_failed_capture_charges_exactly_once() = runTest {
        val (psp, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        psp.scenario = PaymentScenario.NETWORK_ERROR
        vm.onIntent(CheckoutIntent.Capture)
        advanceUntilIdle()
        psp.scenario = PaymentScenario.APPROVED
        vm.onIntent(CheckoutIntent.Capture) // retry reuses the same capture IdempotencyKey
        advanceUntilIdle()

        assertIs<CheckoutStatus.Captured>(vm.state.value.status)
        assertEquals(1, psp.captureCount)
    }

    @Test
    fun cancelling_the_order_voids_the_authorization_without_charging() = runTest {
        val (psp, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.Void)
        advanceUntilIdle()

        val status = assertIs<CheckoutStatus.Voided>(vm.state.value.status)
        assertNotNull(status.receipt.voidedAt)
        assertEquals(1, psp.voidCount)
        assertEquals(0, psp.captureCount, "a cancelled order must never charge the customer")
    }

    @Test
    fun retrying_a_failed_void_releases_exactly_once() = runTest {
        val (psp, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        psp.scenario = PaymentScenario.NETWORK_ERROR
        vm.onIntent(CheckoutIntent.Void)
        advanceUntilIdle()
        // The receipt stays on screen with an inline error; retry reuses the same void key.
        val failed = assertIs<CheckoutStatus.Authorized>(vm.state.value.status)
        assertNotNull(failed.voidError)

        psp.scenario = PaymentScenario.APPROVED
        vm.onIntent(CheckoutIntent.Void)
        advanceUntilIdle()

        assertIs<CheckoutStatus.Voided>(vm.state.value.status)
        assertEquals(1, psp.voidCount)
    }
}
