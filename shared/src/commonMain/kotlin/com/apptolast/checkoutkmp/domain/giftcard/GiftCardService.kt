package com.apptolast.checkoutkmp.domain.giftcard

import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.GiftCard
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import kotlin.time.Instant

/**
 * Domain-facing contract for the retailer's gift-card backend. Like the PSP repository, the
 * implementation (data layer) owns idempotency and maps its raw failures into the closed
 * [PaymentError] taxonomy at the boundary.
 *
 * Redemption and reversal are **separate operations with separate idempotency keys**: replaying
 * either returns the original outcome and never consumes or restores the balance twice.
 */
interface GiftCardService {
    /** Look up a card by [code] (whether it exists and its current balance). */
    suspend fun lookup(code: String): GiftCardLookup

    /** Consume [amount] from the card's balance. Idempotent on [idempotencyKey]. */
    suspend fun redeem(code: String, amount: Amount, idempotencyKey: IdempotencyKey): RedemptionResult

    /**
     * Compensation / refund-to-origin: undo a redemption, restoring its amount to the card.
     * Idempotent on [idempotencyKey]; a redemption can only be reversed once.
     */
    suspend fun reverse(redemptionId: String, idempotencyKey: IdempotencyKey): ReversalResult
}

/** Outcome of a gift-card lookup. */
sealed interface GiftCardLookup {
    data class Found(val card: GiftCard) : GiftCardLookup
    data object NotFound : GiftCardLookup
}

/** A successful balance consumption: the handle needed to reverse it, plus what was taken when. */
data class GiftCardRedemption(
    val redemptionId: String,
    val code: String,
    val amount: Amount,
    val redeemedAt: Instant,
)

/** Outcome of a redemption. Failures arrive already mapped to the domain [PaymentError] taxonomy. */
sealed interface RedemptionResult {
    data class Success(val redemption: GiftCardRedemption) : RedemptionResult
    data class Failure(val error: PaymentError) : RedemptionResult
}

/** Outcome of a reversal. [Success.reversedAt] stamps when the balance was restored. */
sealed interface ReversalResult {
    data class Success(val reversedAt: Instant) : ReversalResult
    data class Failure(val error: PaymentError) : ReversalResult
}
