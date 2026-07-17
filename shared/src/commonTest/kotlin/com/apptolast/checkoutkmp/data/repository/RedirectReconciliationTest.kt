package com.apptolast.checkoutkmp.data.repository

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.RedirectReturn
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.support.FixedClock
import com.apptolast.checkoutkmp.support.Fixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Redirect methods (PayPal/Bizum) reconciled against the PSP's **webhook**: the user's return is
 * a claim, the provider's confirmation is the truth. The case that matters is the approved return
 * whose webhook was rejected — trusting the deep link would ship an unpaid order.
 */
class RedirectReconciliationTest {

    private val psp = FakePsp(scenario = PaymentScenario.APPROVED)
    private val repo = PaymentRepositoryImpl(psp = psp, clock = FixedClock.default)

    private suspend fun startRedirect(request: PaymentRequest = Fixtures.request(method = Fixtures.walletMethod)): PaymentRequest {
        assertIs<PaymentResult.RequiresRedirect>(repo.authorize(request))
        return request
    }

    @Test
    fun authorize_creates_the_provider_order_without_charging() = runTest {
        val result = repo.authorize(Fixtures.request(method = Fixtures.walletMethod))

        val redirect = assertIs<PaymentResult.RequiresRedirect>(result).redirect
        assertTrue(redirect.url.startsWith("https://"), "the approval page is the provider's")
        assertTrue(redirect.returnUrl.isNotBlank(), "there must be a deep link to come back to")
        assertEquals(0, psp.chargeCount, "creating the order charges nothing")
    }

    @Test
    fun authorizing_twice_with_the_same_key_creates_one_order() = runTest {
        val request = Fixtures.request(method = Fixtures.walletMethod)

        val first = assertIs<PaymentResult.RequiresRedirect>(repo.authorize(request)).redirect
        val second = assertIs<PaymentResult.RequiresRedirect>(repo.authorize(request)).redirect

        assertEquals(first.redirectId, second.redirectId)
    }

    @Test
    fun approved_return_confirmed_by_webhook_settles_to_captured() = runTest {
        val request = startRedirect()

        val result = repo.completeRedirect(request, RedirectReturn.APPROVED)

        val receipt = assertIs<PaymentResult.Captured>(result).receipt
        assertNotNull(receipt.capturedAt)
        assertEquals(Fixtures.walletMethod, receipt.method)
        assertEquals(1, psp.chargeCount)
    }

    @Test
    fun approved_return_with_rejected_webhook_is_declined() = runTest {
        // The provider order is created while the demo knob says DECLINED: the webhook that the
        // client never sees records a rejection.
        psp.scenario = PaymentScenario.DECLINED
        val request = startRedirect()

        // The user still comes back claiming success — the webhook record wins.
        val result = repo.completeRedirect(request, RedirectReturn.APPROVED)

        val error = assertIs<PaymentResult.Failed>(result).error
        assertIs<PaymentError.Declined>(error)
        assertEquals(0, psp.chargeCount, "an unconfirmed payment must never charge")
    }

    @Test
    fun user_cancelling_at_the_provider_maps_to_cancelled() = runTest {
        val request = startRedirect()

        val result = repo.completeRedirect(request, RedirectReturn.CANCELLED)

        assertEquals(PaymentError.Cancelled, assertIs<PaymentResult.Failed>(result).error)
        assertEquals(0, psp.chargeCount)
    }

    @Test
    fun provider_reported_failure_is_declined() = runTest {
        val request = startRedirect()

        val result = repo.completeRedirect(request, RedirectReturn.FAILED)

        val error = assertIs<PaymentResult.Failed>(result).error
        assertIs<PaymentError.Declined>(error)
    }

    @Test
    fun replaying_the_completion_with_the_same_key_charges_only_once() = runTest {
        val request = startRedirect()

        val first = assertIs<PaymentResult.Captured>(repo.completeRedirect(request, RedirectReturn.APPROVED))
        val second = assertIs<PaymentResult.Captured>(repo.completeRedirect(request, RedirectReturn.APPROVED))

        assertEquals(first.receipt.paymentId, second.receipt.paymentId)
        assertEquals(1, psp.chargeCount)
    }
}
