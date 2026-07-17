package com.apptolast.checkoutkmp.domain.usecase

import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.model.toPaymentState
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository

/**
 * Refunds a previously captured payment. One refund attempt = one [IdempotencyKey]; retrying a
 * transient failure reuses the same key so the customer can never be refunded twice.
 *
 * Pure domain guard: an already refunded receipt is returned as-is without calling the PSP.
 * Refunding a payment that was never captured is a PSP-level decision (the fake declines it) —
 * the source of truth for the lifecycle stays at the gateway.
 */
class RefundPaymentUseCase(
    private val repository: PaymentRepository,
) {
    suspend operator fun invoke(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentState =
        when {
            receipt.refundedAt != null -> PaymentState.Refunded(receipt)
            // A voided authorization never charged anything, so there is nothing to refund.
            receipt.voidedAt != null -> PaymentState.Voided(receipt)
            else -> repository.refund(receipt, idempotencyKey).toPaymentState()
        }
}
