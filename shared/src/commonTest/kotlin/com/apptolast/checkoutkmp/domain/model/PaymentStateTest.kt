package com.apptolast.checkoutkmp.domain.model

import com.apptolast.checkoutkmp.support.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentStateTest {

    @Test
    fun maps_authorized_result_to_authorized() {
        val state = PaymentResult.Authorized(Fixtures.receipt).toPaymentState()
        assertEquals(PaymentState.Authorized(Fixtures.receipt), state)
    }

    @Test
    fun maps_captured_result_to_captured() {
        val state = PaymentResult.Captured(Fixtures.capturedReceipt).toPaymentState()
        assertEquals(PaymentState.Captured(Fixtures.capturedReceipt), state)
    }

    @Test
    fun maps_refunded_result_to_refunded() {
        val state = PaymentResult.Refunded(Fixtures.refundedReceipt).toPaymentState()
        assertEquals(PaymentState.Refunded(Fixtures.refundedReceipt), state)
    }

    @Test
    fun maps_voided_result_to_voided() {
        val state = PaymentResult.Voided(Fixtures.voidedReceipt).toPaymentState()
        assertEquals(PaymentState.Voided(Fixtures.voidedReceipt), state)
    }

    @Test
    fun maps_requires_sca_result_to_requires_sca() {
        val state = PaymentResult.RequiresSca(Fixtures.challenge).toPaymentState()
        assertEquals(PaymentState.RequiresSca(Fixtures.challenge), state)
    }

    @Test
    fun maps_failed_result_to_failed() {
        val error = PaymentError.Declined("insufficient_funds")
        assertEquals(PaymentState.Failed(error), PaymentResult.Failed(error).toPaymentState())
    }

    @Test
    fun only_settlement_outcomes_and_failures_are_settled() {
        assertTrue(PaymentState.Authorized(Fixtures.receipt).isSettled)
        assertTrue(PaymentState.Captured(Fixtures.capturedReceipt).isSettled)
        assertTrue(PaymentState.Refunded(Fixtures.refundedReceipt).isSettled)
        assertTrue(PaymentState.Voided(Fixtures.voidedReceipt).isSettled)
        assertTrue(PaymentState.Failed(PaymentError.Network).isSettled)

        assertFalse(PaymentState.Idle.isSettled)
        assertFalse(PaymentState.Processing.isSettled)
        assertFalse(PaymentState.RequiresSca(Fixtures.challenge).isSettled)
    }

    @Test
    fun only_network_timeout_and_ratelimited_are_transient() {
        assertTrue(PaymentError.Network.isTransient)
        assertTrue(PaymentError.Timeout.isTransient)
        assertTrue(PaymentError.RateLimited.isTransient)

        assertFalse(PaymentError.Declined("x").isTransient)
        assertFalse(PaymentError.InvalidCard("x").isTransient)
        assertFalse(PaymentError.ScaFailed("x").isTransient)
        assertFalse(PaymentError.Cancelled.isTransient)
        assertFalse(PaymentError.Unknown(null).isTransient)
    }
}
