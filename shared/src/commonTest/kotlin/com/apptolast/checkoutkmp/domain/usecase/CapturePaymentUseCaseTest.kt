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
import kotlin.test.assertTrue

class CapturePaymentUseCaseTest {

    @Test
    fun captures_an_authorized_receipt() = runTest {
        val repo = FakePaymentRepository(
            onCapture = { receipt, _ -> PaymentResult.Captured(receipt.copy(capturedAt = Fixtures.capturedReceipt.capturedAt)) },
        )
        val key = IdempotencyKey.random()

        val state = CapturePaymentUseCase(repo)(Fixtures.receipt, key)

        assertEquals(PaymentState.Captured(Fixtures.capturedReceipt), state)
        assertEquals(listOf(Fixtures.receipt to key), repo.captureCalls)
    }

    @Test
    fun capturing_an_already_captured_receipt_is_a_noop() = runTest {
        val repo = FakePaymentRepository() // capture not scripted: calling it would fail the test

        val state = CapturePaymentUseCase(repo)(Fixtures.capturedReceipt, IdempotencyKey.random())

        assertEquals(PaymentState.Captured(Fixtures.capturedReceipt), state)
        assertTrue(repo.captureCalls.isEmpty(), "an already captured receipt must not hit the PSP again")
    }

    @Test
    fun capturing_a_refunded_receipt_returns_refunded_without_calling_the_psp() = runTest {
        val repo = FakePaymentRepository()

        val state = CapturePaymentUseCase(repo)(Fixtures.refundedReceipt, IdempotencyKey.random())

        assertEquals(PaymentState.Refunded(Fixtures.refundedReceipt), state)
        assertTrue(repo.captureCalls.isEmpty())
    }

    @Test
    fun a_failed_capture_surfaces_the_error() = runTest {
        val repo = FakePaymentRepository(
            onCapture = { _, _ -> PaymentResult.Failed(PaymentError.Network) },
        )

        val state = CapturePaymentUseCase(repo)(Fixtures.receipt, IdempotencyKey.random())

        assertEquals(PaymentState.Failed(PaymentError.Network), state)
    }
}
