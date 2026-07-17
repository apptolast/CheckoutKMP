package com.apptolast.checkoutkmp.domain.usecase

import com.apptolast.checkoutkmp.domain.giftcard.GiftCardService
import com.apptolast.checkoutkmp.domain.giftcard.ReversalResult
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey

/**
 * Returns a consumed gift-card balance to its card. Used as **compensation** when a split payment
 * is abandoned after the redemption (cancelled/failed 3D Secure, user gives up on a transient
 * failure) and as **refund-to-origin** for the gift-card tender of a settled payment.
 * Idempotent on [IdempotencyKey]; a redemption can only be reversed once.
 */
class ReverseGiftCardRedemptionUseCase(
    private val giftCards: GiftCardService,
) {
    suspend operator fun invoke(redemptionId: String, idempotencyKey: IdempotencyKey): ReversalResult =
        giftCards.reverse(redemptionId, idempotencyKey)
}
