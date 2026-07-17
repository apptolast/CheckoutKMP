package com.apptolast.checkoutkmp.domain.usecase

import app.cash.turbine.test
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.model.RedirectReturn
import com.apptolast.checkoutkmp.support.FakePaymentRepository
import com.apptolast.checkoutkmp.support.Fixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CompleteRedirectUseCaseTest {

    @Test
    fun emits_processing_then_the_reconciled_outcome() = runTest {
        val repo = FakePaymentRepository(
            onCompleteRedirect = { _, _ -> PaymentResult.Captured(Fixtures.capturedReceipt) },
        )

        CompleteRedirectUseCase(repo).invoke(Fixtures.request(), RedirectReturn.APPROVED).test {
            assertEquals(PaymentState.Processing, awaitItem())
            // A wallet settles straight to Captured — immediate capture, never Authorized.
            assertEquals(PaymentState.Captured(Fixtures.capturedReceipt), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun an_approved_claim_can_still_fail_when_the_psp_says_so() = runTest {
        val error = PaymentError.Declined("webhook_rejected")
        val repo = FakePaymentRepository(onCompleteRedirect = { _, _ -> PaymentResult.Failed(error) })

        CompleteRedirectUseCase(repo).invoke(Fixtures.request(), RedirectReturn.APPROVED).test {
            assertEquals(PaymentState.Processing, awaitItem())
            assertEquals(PaymentState.Failed(error), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun reuses_the_original_idempotency_key_and_forwards_the_claim() = runTest {
        val key = IdempotencyKey.random()
        val repo = FakePaymentRepository(
            onCompleteRedirect = { _, _ -> PaymentResult.Failed(PaymentError.Cancelled) },
        )

        CompleteRedirectUseCase(repo).invoke(Fixtures.request(key), RedirectReturn.CANCELLED).test {
            awaitItem() // Processing
            awaitItem() // Failed(Cancelled)
            awaitComplete()
        }

        val (request, returned) = repo.completeRedirectCalls.single()
        assertEquals(key, request.idempotencyKey)
        assertEquals(RedirectReturn.CANCELLED, returned)
    }
}
