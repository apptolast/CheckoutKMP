package com.apptolast.checkoutkmp.support

import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository

/**
 * Scriptable [PaymentRepository] test double. Each entry point returns whatever the supplied lambda
 * produces and records the calls so tests can assert idempotency-key reuse and call counts.
 */
class FakePaymentRepository(
    var onAuthorize: (PaymentRequest) -> PaymentResult = { error("authorize not scripted") },
    var onCompleteSca: (PaymentRequest, String) -> PaymentResult = { _, _ -> error("completeSca not scripted") },
) : PaymentRepository {

    val authorizeCalls = mutableListOf<PaymentRequest>()
    val completeScaCalls = mutableListOf<Pair<PaymentRequest, String>>()

    override suspend fun authorize(request: PaymentRequest): PaymentResult {
        authorizeCalls += request
        return onAuthorize(request)
    }

    override suspend fun completeSca(request: PaymentRequest, otp: String): PaymentResult {
        completeScaCalls += request to otp
        return onCompleteSca(request, otp)
    }
}
