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
 *
 * [createdAt] is when the receipt itself was issued (stamped at the data boundary by the same
 * injected clock as the settlement timestamps), shown to the user as the payment date.
 */
data class Receipt(
    val paymentId: String,
    val amount: Amount,
    val method: PaymentMethod,
    val createdAt: Instant,
    val authorizedAt: Instant,
    val authCode: String?,
    val capturedAt: Instant? = null,
    val refundedAt: Instant? = null,
    val voidedAt: Instant? = null,
    val giftCard: GiftCardTender? = null,
)

/** Where a payment sits in its settlement lifecycle, derived from the receipt's timestamps. */
enum class SettlementStatus {
    AUTHORIZED,
    CAPTURED,
    REFUNDED,
    VOIDED,
}

/** The receipt's current settlement stage (the latest step wins: refund/void over capture). */
val Receipt.settlement: SettlementStatus
    get() = when {
        refundedAt != null -> SettlementStatus.REFUNDED
        voidedAt != null -> SettlementStatus.VOIDED
        capturedAt != null -> SettlementStatus.CAPTURED
        else -> SettlementStatus.AUTHORIZED
    }

/** When the receipt last changed — what an order history sorts by. */
val Receipt.lastUpdatedAt: Instant
    get() = refundedAt ?: voidedAt ?: capturedAt ?: authorizedAt
