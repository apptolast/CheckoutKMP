package com.apptolast.checkoutkmp.domain.model

import kotlin.time.Instant

/**
 * Proof of a payment and its settlement lifecycle. PCI-safe: [method] carries at most a token and
 * a masked card, never the PAN.
 *
 * [capturedAt]/[refundedAt] record when each settlement step happened: both `null` means the funds
 * are only authorized (held), [capturedAt] set means the customer was actually charged, and
 * [refundedAt] set means the charge was returned. [voidedAt] set means the hold was released
 * without ever charging (mutually exclusive with capture).
 *
 * [giftCard] records the gift-card tender of a split payment (or the whole payment when [method]
 * is [PaymentMethod.GiftCard]); its redemption id is the handle used to return that balance.
 * [authCode] is `null` for tenders that have no issuer authorization code (gift cards).
 */
data class Receipt(
    val paymentId: String,
    val amount: Amount,
    val method: PaymentMethod,
    val authorizedAt: Instant,
    val authCode: String?,
    val capturedAt: Instant? = null,
    val refundedAt: Instant? = null,
    val voidedAt: Instant? = null,
    val giftCard: GiftCardTender? = null,
)
