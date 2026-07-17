package com.apptolast.checkoutkmp.support

import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository

/**
 * Scriptable [PaymentRepository] test double. Each entry point returns whatever the supplied lambda
 * produces and records the calls so tests can assert idempotency-key reuse and call counts.
 */
class FakePaymentRepository(
    var onAuthorize: (PaymentRequest) -> PaymentResult = { error("authorize not scripted") },
    var onCompleteSca: (PaymentRequest, String) -> PaymentResult = { _, _ -> error("completeSca not scripted") },
    var onCapture: (Receipt, IdempotencyKey) -> PaymentResult = { _, _ -> error("capture not scripted") },
    var onRefund: (Receipt, IdempotencyKey) -> PaymentResult = { _, _ -> error("refund not scripted") },
) : PaymentRepository {

    val authorizeCalls = mutableListOf<PaymentRequest>()
    val completeScaCalls = mutableListOf<Pair<PaymentRequest, String>>()
    val captureCalls = mutableListOf<Pair<Receipt, IdempotencyKey>>()
    val refundCalls = mutableListOf<Pair<Receipt, IdempotencyKey>>()

    override suspend fun authorize(request: PaymentRequest): PaymentResult {
        authorizeCalls += request
        return onAuthorize(request)
    }

    override suspend fun completeSca(request: PaymentRequest, otp: String): PaymentResult {
        completeScaCalls += request to otp
        return onCompleteSca(request, otp)
    }

    override suspend fun capture(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentResult {
        captureCalls += receipt to idempotencyKey
        return onCapture(receipt, idempotencyKey)
    }

    override suspend fun refund(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentResult {
        refundCalls += receipt to idempotencyKey
        return onRefund(receipt, idempotencyKey)
    }
}
