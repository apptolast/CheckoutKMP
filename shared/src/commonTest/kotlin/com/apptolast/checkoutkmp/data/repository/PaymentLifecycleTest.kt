package com.apptolast.checkoutkmp.data.repository

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.Receipt
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
 * End-to-end lifecycle of a payment against the fake PSP: authorization holds funds, capture
 * charges, refund returns the charge — each step idempotent on its own key, and the charge timing
 * decided by the method, never by scattered conditionals.
 */
class PaymentLifecycleTest {

    private val psp = FakePsp(scenario = PaymentScenario.APPROVED)
    private val repo = PaymentRepositoryImpl(psp = psp, clock = FixedClock.default)

    private suspend fun authorizedReceipt(): Receipt =
        assertIs<PaymentResult.Authorized>(repo.authorize(Fixtures.request())).receipt

    @Test
    fun authorized_then_captured_then_refunded() = runTest {
        val authorized = authorizedReceipt()
        assertEquals(null, authorized.capturedAt)

        val captured = assertIs<PaymentResult.Captured>(
            repo.capture(authorized, IdempotencyKey.random()),
        ).receipt
        assertNotNull(captured.capturedAt, "capture must stamp the charge time")

        val refunded = assertIs<PaymentResult.Refunded>(
            repo.refund(captured, IdempotencyKey.random()),
        ).receipt
        assertNotNull(refunded.refundedAt, "refund must stamp the return time")

        assertEquals(1, psp.chargeCount)
        assertEquals(1, psp.captureCount)
        assertEquals(1, psp.refundCount)
    }

    @Test
    fun capturing_twice_with_the_same_key_charges_only_once() = runTest {
        val authorized = authorizedReceipt()
        val key = IdempotencyKey.random()

        assertIs<PaymentResult.Captured>(repo.capture(authorized, key))
        assertIs<PaymentResult.Captured>(repo.capture(authorized, key)) // idempotent replay

        assertEquals(1, psp.captureCount)
    }

    @Test
    fun capturing_again_with_a_new_key_is_declined() = runTest {
        val authorized = authorizedReceipt()
        assertIs<PaymentResult.Captured>(repo.capture(authorized, IdempotencyKey.random()))

        val second = repo.capture(authorized, IdempotencyKey.random())

        val error = assertIs<PaymentResult.Failed>(second).error
        assertIs<PaymentError.Declined>(error)
        assertEquals(1, psp.captureCount)
    }

    @Test
    fun refunding_twice_with_the_same_key_refunds_only_once() = runTest {
        val authorized = authorizedReceipt()
        val captured = assertIs<PaymentResult.Captured>(
            repo.capture(authorized, IdempotencyKey.random()),
        ).receipt
        val key = IdempotencyKey.random()

        assertIs<PaymentResult.Refunded>(repo.refund(captured, key))
        assertIs<PaymentResult.Refunded>(repo.refund(captured, key)) // idempotent replay

        assertEquals(1, psp.refundCount)
    }

    @Test
    fun refunding_an_uncaptured_payment_is_declined() = runTest {
        val authorized = authorizedReceipt()

        val result = repo.refund(authorized, IdempotencyKey.random())

        val error = assertIs<PaymentResult.Failed>(result).error
        assertIs<PaymentError.Declined>(error)
        assertEquals(0, psp.refundCount)
    }

    @Test
    fun an_immediate_capture_method_never_passes_through_authorized() = runTest {
        val request = Fixtures.request(method = Fixtures.walletMethod)
        assertIs<PaymentResult.RequiresRedirect>(repo.authorize(request))

        // Once the provider confirms, a wallet payment is Captured from the start.
        val result = repo.completeRedirect(request, RedirectReturn.APPROVED)

        val receipt = assertIs<PaymentResult.Captured>(result).receipt
        assertNotNull(receipt.capturedAt)
        assertEquals(1, psp.chargeCount)
        assertEquals(0, psp.captureCount, "no separate capture happens for immediate methods")
    }

    @Test
    fun an_immediate_capture_payment_can_be_refunded() = runTest {
        val request = Fixtures.request(method = Fixtures.walletMethod)
        assertIs<PaymentResult.RequiresRedirect>(repo.authorize(request))
        val receipt = assertIs<PaymentResult.Captured>(
            repo.completeRedirect(request, RedirectReturn.APPROVED),
        ).receipt

        val refunded = repo.refund(receipt, IdempotencyKey.random())

        assertNotNull(assertIs<PaymentResult.Refunded>(refunded).receipt.refundedAt)
    }

    @Test
    fun a_transient_failure_during_capture_maps_to_a_retriable_error() = runTest {
        val authorized = authorizedReceipt()
        psp.scenario = PaymentScenario.NETWORK_ERROR

        val result = repo.capture(authorized, IdempotencyKey.random())

        val error = assertIs<PaymentResult.Failed>(result).error
        assertTrue(error.isTransient)
        assertEquals(0, psp.captureCount, "a transport failure never captures")
    }
}
