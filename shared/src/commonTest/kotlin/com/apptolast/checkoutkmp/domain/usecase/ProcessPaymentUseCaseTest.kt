package com.apptolast.checkoutkmp.domain.usecase

import app.cash.turbine.test
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.support.Fixtures
import com.apptolast.checkoutkmp.support.FakePaymentRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessPaymentUseCaseTest {

    @Test
    fun emits_processing_then_approved() = runTest {
        val repo = FakePaymentRepository(onAuthorize = { PaymentResult.Authorized(Fixtures.receipt) })

        ProcessPaymentUseCase(repo).invoke(Fixtures.request()).test {
            assertEquals(PaymentState.Processing, awaitItem())
            assertEquals(PaymentState.Approved(Fixtures.receipt), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun emits_processing_then_requires_sca() = runTest {
        val repo = FakePaymentRepository(onAuthorize = { PaymentResult.RequiresSca(Fixtures.challenge) })

        ProcessPaymentUseCase(repo).invoke(Fixtures.request()).test {
            assertEquals(PaymentState.Processing, awaitItem())
            assertEquals(PaymentState.RequiresSca(Fixtures.challenge), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun emits_processing_then_failed_when_declined() = runTest {
        val error = PaymentError.Declined("insufficient_funds")
        val repo = FakePaymentRepository(onAuthorize = { PaymentResult.Failed(error) })

        ProcessPaymentUseCase(repo).invoke(Fixtures.request()).test {
            assertEquals(PaymentState.Processing, awaitItem())
            assertEquals(PaymentState.Failed(error), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun emits_processing_then_failed_on_transient_network_error() = runTest {
        val repo = FakePaymentRepository(onAuthorize = { PaymentResult.Failed(PaymentError.Network) })

        ProcessPaymentUseCase(repo).invoke(Fixtures.request()).test {
            assertEquals(PaymentState.Processing, awaitItem())
            assertEquals(PaymentState.Failed(PaymentError.Network), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun forwards_the_request_with_its_idempotency_key_once() = runTest {
        val key = IdempotencyKey.random()
        val repo = FakePaymentRepository(onAuthorize = { PaymentResult.Authorized(Fixtures.receipt) })

        ProcessPaymentUseCase(repo).invoke(Fixtures.request(key)).test {
            awaitItem() // Processing
            awaitItem() // Approved
            awaitComplete()
        }

        assertEquals(1, repo.authorizeCalls.size)
        assertEquals(key, repo.authorizeCalls.single().idempotencyKey)
    }
}
