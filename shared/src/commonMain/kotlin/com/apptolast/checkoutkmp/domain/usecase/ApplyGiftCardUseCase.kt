package com.apptolast.checkoutkmp.domain.usecase

import com.apptolast.checkoutkmp.domain.giftcard.GiftCardLookup
import com.apptolast.checkoutkmp.domain.giftcard.GiftCardService

/**
 * Looks up a gift card by the code the user typed, so the checkout can show its balance and plan
 * the split before anything is charged. Applying a card consumes nothing — the balance is only
 * touched at redemption time.
 */
class ApplyGiftCardUseCase(
    private val giftCards: GiftCardService,
) {
    suspend operator fun invoke(code: String): GiftCardLookup = giftCards.lookup(code)
}
