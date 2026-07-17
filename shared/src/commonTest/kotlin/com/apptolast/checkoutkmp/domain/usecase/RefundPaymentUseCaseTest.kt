package com.apptolast.checkoutkmp.domain.usecase

import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.support.FakePaymentRepository
import com.apptolast.checkoutkmp.support.Fixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RefundPaymentUseCaseTest {

    @Test
    fun refunds_a_captured_receipt() = runTest {
        val repo = FakePaymentRepository(
            onRefund = { receipt, _ -> PaymentResult.Refunded(receipt.copy(refundedAt = Fixtures.refundedReceipt.refundedAt)) },
        )
        val key = IdempotencyKey.random()

        val state = RefundPaymentUseCase(repo)(Fixtures.capturedReceipt, key)

        assertEquals(PaymentState.Refunded(Fixtures.refundedReceipt), state)
        assertEquals(listOf(Fixtures.capturedReceipt to key), repo.refundCalls)
    }

    @Test
    fun refunding_an_already_refunded_receipt_is_a_noop() = runTest {
        val repo = FakePaymentRepository() // refund not scripted: calling it would fail the test

        val state = RefundPaymentUseCase(repo)(Fixtures.refundedReceipt, IdempotencyKey.random())

        assertEquals(PaymentState.Refunded(Fixtures.refundedReceipt), state)
        assertTrue(repo.refundCalls.isEmpty(), "an already refunded receipt must not hit the PSP again")
    }

    @Test
    fun refunding_an_uncaptured_receipt_is_decided_by_the_psp() = runTest {
        // The gateway is the source of truth for the lifecycle; the fake declines this downstream.
        val repo = FakePaymentRepository(
            onRefund = { _, _ -> PaymentResult.Failed(PaymentError.Declined("not_captured")) },
        )

        val state = RefundPaymentUseCase(repo)(Fixtures.receipt, IdempotencyKey.random())

        val failed = assertIs<PaymentState.Failed>(state)
        assertIs<PaymentError.Declined>(failed.error)
    }
}
