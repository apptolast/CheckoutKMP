package com.apptolast.checkoutkmp.domain.usecase

import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.model.toPaymentState
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository

/**
 * Captures (actually charges) a previously authorized payment — in retail, the moment the order is
 * dispatched. One capture attempt = one [IdempotencyKey]; retrying a transient failure must reuse
 * the same key so the customer can never be charged twice.
 *
 * Pure domain guards: a receipt that is already captured (or further along, refunded) is returned
 * as-is without calling the PSP — replaying a completed capture is a no-op, not an error.
 */
class CapturePaymentUseCase(
    private val repository: PaymentRepository,
) {
    suspend operator fun invoke(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentState =
        when {
            receipt.refundedAt != null -> PaymentState.Refunded(receipt)
            receipt.capturedAt != null -> PaymentState.Captured(receipt)
            else -> repository.capture(receipt, idempotencyKey).toPaymentState()
        }
}
