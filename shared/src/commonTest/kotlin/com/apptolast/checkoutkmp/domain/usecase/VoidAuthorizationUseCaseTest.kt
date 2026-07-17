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

class VoidAuthorizationUseCaseTest {

    @Test
    fun voids_an_authorized_receipt() = runTest {
        val repo = FakePaymentRepository(
            onVoid = { receipt, _ -> PaymentResult.Voided(receipt.copy(voidedAt = Fixtures.voidedReceipt.voidedAt)) },
        )
        val key = IdempotencyKey.random()

        val state = VoidAuthorizationUseCase(repo)(Fixtures.receipt, key)

        assertEquals(PaymentState.Voided(Fixtures.voidedReceipt), state)
        assertEquals(listOf(Fixtures.receipt to key), repo.voidCalls)
    }

    @Test
    fun voiding_an_already_voided_receipt_is_a_noop() = runTest {
        val repo = FakePaymentRepository() // void not scripted: calling it would fail the test

        val state = VoidAuthorizationUseCase(repo)(Fixtures.voidedReceipt, IdempotencyKey.random())

        assertEquals(PaymentState.Voided(Fixtures.voidedReceipt), state)
        assertTrue(repo.voidCalls.isEmpty(), "an already voided receipt must not hit the PSP again")
    }

    @Test
    fun a_captured_receipt_cannot_be_voided_and_stays_captured() = runTest {
        val repo = FakePaymentRepository()

        // The charge already happened: the path back is a refund, never a void.
        val state = VoidAuthorizationUseCase(repo)(Fixtures.capturedReceipt, IdempotencyKey.random())

        assertEquals(PaymentState.Captured(Fixtures.capturedReceipt), state)
        assertTrue(repo.voidCalls.isEmpty())
    }

    @Test
    fun a_failed_void_surfaces_the_error() = runTest {
        val repo = FakePaymentRepository(
            onVoid = { _, _ -> PaymentResult.Failed(PaymentError.Network) },
        )

        val state = VoidAuthorizationUseCase(repo)(Fixtures.receipt, IdempotencyKey.random())

        assertEquals(PaymentState.Failed(PaymentError.Network), state)
    }
}
