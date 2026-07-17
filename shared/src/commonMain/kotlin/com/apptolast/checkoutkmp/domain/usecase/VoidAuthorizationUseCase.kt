package com.apptolast.checkoutkmp.domain.usecase

import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.model.toPaymentState
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository

/**
 * Releases the hold of an authorized-but-uncaptured payment — in retail, the customer cancels the
 * order before it ships, so the funds are freed and **the card is never charged**. A void is not a
 * refund: refunds return a captured charge; voids release money that was only reserved.
 *
 * One void attempt = one [IdempotencyKey]; retrying a transient failure reuses the same key so the
 * hold can never be released twice.
 *
 * Pure domain guards: an already voided receipt replays as a no-op, and a captured receipt cannot
 * be voided any more — the charge happened, the state stays [PaymentState.Captured] (use the
 * refund instead). Everything else is the PSP's lifecycle decision.
 */
class VoidAuthorizationUseCase(
    private val repository: PaymentRepository,
) {
    suspend operator fun invoke(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentState =
        when {
            receipt.voidedAt != null -> PaymentState.Voided(receipt)
            receipt.capturedAt != null -> PaymentState.Captured(receipt)
            else -> repository.void(receipt, idempotencyKey).toPaymentState()
        }
}
