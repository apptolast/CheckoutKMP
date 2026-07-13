package com.apptolast.checkoutkmp.domain.model

import com.apptolast.checkoutkmp.support.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentStateTest {

    @Test
    fun maps_authorized_result_to_approved() {
        val state = PaymentResult.Authorized(Fixtures.receipt).toPaymentState()
        assertEquals(PaymentState.Approved(Fixtures.receipt), state)
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
    fun only_approved_and_failed_are_terminal() {
        assertTrue(PaymentState.Approved(Fixtures.receipt).isTerminal)
        assertTrue(PaymentState.Failed(PaymentError.Network).isTerminal)
        assertFalse(PaymentState.Idle.isTerminal)
        assertFalse(PaymentState.Processing.isTerminal)
        assertFalse(PaymentState.RequiresSca(Fixtures.challenge).isTerminal)
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
