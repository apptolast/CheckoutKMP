package com.apptolast.checkoutkmp.domain.repository

import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentResult

/**
 * Domain-facing contract for talking to a PSP. Implementations (data layer) own idempotency,
 * latency and the PSP→[com.apptolast.checkoutkmp.domain.model.PaymentError] mapping at the boundary,
 * and always return a closed [PaymentResult] rather than throwing for expected payment outcomes.
 */
interface PaymentRepository {
    /** Authorize a payment. Idempotent on [PaymentRequest.idempotencyKey]. */
    suspend fun authorize(request: PaymentRequest): PaymentResult

    /** Complete a pending 3D Secure challenge for [request] with the user-supplied [otp]. */
    suspend fun completeSca(request: PaymentRequest, otp: String): PaymentResult
}
