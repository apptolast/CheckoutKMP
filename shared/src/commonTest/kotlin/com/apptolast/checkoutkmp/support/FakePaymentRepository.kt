package com.apptolast.checkoutkmp.support

import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.model.RedirectReturn
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository

/**
 * Scriptable [PaymentRepository] test double. Each entry point returns whatever the supplied lambda
 * produces and records the calls so tests can assert idempotency-key reuse and call counts.
 */
class FakePaymentRepository(
    var onAuthorize: (PaymentRequest) -> PaymentResult = { error("authorize not scripted") },
    var onCompleteSca: (PaymentRequest, String) -> PaymentResult = { _, _ -> error("completeSca not scripted") },
    var onResendSca: (PaymentRequest) -> PaymentResult = { error("resendSca not scripted") },
    var onCompleteRedirect: (PaymentRequest, RedirectReturn) -> PaymentResult = { _, _ -> error("completeRedirect not scripted") },
    var onCapture: (Receipt, IdempotencyKey) -> PaymentResult = { _, _ -> error("capture not scripted") },
    var onVoid: (Receipt, IdempotencyKey) -> PaymentResult = { _, _ -> error("void not scripted") },
    var onRefund: (Receipt, IdempotencyKey) -> PaymentResult = { _, _ -> error("refund not scripted") },
) : PaymentRepository {

    val authorizeCalls = mutableListOf<PaymentRequest>()
    val completeScaCalls = mutableListOf<Pair<PaymentRequest, String>>()
    val resendScaCalls = mutableListOf<PaymentRequest>()
    val completeRedirectCalls = mutableListOf<Pair<PaymentRequest, RedirectReturn>>()
    val captureCalls = mutableListOf<Pair<Receipt, IdempotencyKey>>()
    val voidCalls = mutableListOf<Pair<Receipt, IdempotencyKey>>()
    val refundCalls = mutableListOf<Pair<Receipt, IdempotencyKey>>()

    override suspend fun authorize(request: PaymentRequest): PaymentResult {
        authorizeCalls += request
        return onAuthorize(request)
    }

    override suspend fun completeSca(request: PaymentRequest, otp: String): PaymentResult {
        completeScaCalls += request to otp
        return onCompleteSca(request, otp)
    }

    override suspend fun resendSca(request: PaymentRequest): PaymentResult {
        resendScaCalls += request
        return onResendSca(request)
    }

    override suspend fun completeRedirect(request: PaymentRequest, returned: RedirectReturn): PaymentResult {
        completeRedirectCalls += request to returned
        return onCompleteRedirect(request, returned)
    }

    override suspend fun capture(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentResult {
        captureCalls += receipt to idempotencyKey
        return onCapture(receipt, idempotencyKey)
    }

    override suspend fun void(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentResult {
        voidCalls += receipt to idempotencyKey
        return onVoid(receipt, idempotencyKey)
    }

    override suspend fun refund(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentResult {
        refundCalls += receipt to idempotencyKey
        return onRefund(receipt, idempotencyKey)
    }
}
