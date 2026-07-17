package com.apptolast.checkoutkmp.domain.repository

import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.Receipt

/**
 * Domain-facing contract for talking to a PSP. Implementations (data layer) own idempotency,
 * latency and the PSP→[com.apptolast.checkoutkmp.domain.model.PaymentError] mapping at the boundary,
 * and always return a closed [PaymentResult] rather than throwing for expected payment outcomes.
 *
 * Authorization, capture and refund are **separate operations with separate idempotency keys**:
 * retrying any one of them reuses its own key, so none of them can be executed twice.
 */
interface PaymentRepository {
    /** Authorize a payment (hold funds; immediate-capture methods charge in the same step).
     *  Idempotent on [PaymentRequest.idempotencyKey]. */
    suspend fun authorize(request: PaymentRequest): PaymentResult

    /** Complete a pending 3D Secure challenge for [request] with the user-supplied [otp]. */
    suspend fun completeSca(request: PaymentRequest, otp: String): PaymentResult

    /** Capture (actually charge) a previously authorized payment. Idempotent on [idempotencyKey]. */
    suspend fun capture(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentResult

    /** Refund a previously captured payment. Idempotent on [idempotencyKey]. */
    suspend fun refund(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentResult
}
