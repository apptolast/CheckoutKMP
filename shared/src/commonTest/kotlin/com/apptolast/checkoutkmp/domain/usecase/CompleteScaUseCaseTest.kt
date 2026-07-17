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

class CompleteScaUseCaseTest {

    @Test
    fun correct_otp_completes_the_payment() = runTest {
        val repo = FakePaymentRepository(onCompleteSca = { _, _ -> PaymentResult.Authorized(Fixtures.receipt) })

        CompleteScaUseCase(repo).invoke(Fixtures.request(), otp = "123456").test {
            assertEquals(PaymentState.Processing, awaitItem())
            assertEquals(PaymentState.Authorized(Fixtures.receipt), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun wrong_otp_fails_with_sca_failed() = runTest {
        val error = PaymentError.ScaFailed("wrong_otp")
        val repo = FakePaymentRepository(onCompleteSca = { _, _ -> PaymentResult.Failed(error) })

        CompleteScaUseCase(repo).invoke(Fixtures.request(), otp = "000000").test {
            assertEquals(PaymentState.Processing, awaitItem())
            assertEquals(PaymentState.Failed(error), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun reuses_the_original_idempotency_key_and_forwards_the_otp() = runTest {
        val key = IdempotencyKey.random()
        val repo = FakePaymentRepository(onCompleteSca = { _, _ -> PaymentResult.Authorized(Fixtures.receipt) })

        CompleteScaUseCase(repo).invoke(Fixtures.request(key), otp = "654321").test {
            awaitItem() // Processing
            awaitItem() // Authorized
            awaitComplete()
        }

        val (request, otp) = repo.completeScaCalls.single()
        assertEquals(key, request.idempotencyKey)
        assertEquals("654321", otp)
    }
}
