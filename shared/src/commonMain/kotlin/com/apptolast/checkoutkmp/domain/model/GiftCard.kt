package com.apptolast.checkoutkmp.domain.model

/**
 * A retail gift card tender: an opaque code with a prepaid [balance]. Not card data in the PCI
 * sense (no PAN/CVV), so the code itself may live in state and on the receipt.
 */
data class GiftCard(
    val code: String,
    val balance: Amount,
)

/**
 * The gift-card part of a (possibly split) payment as recorded on the [Receipt]:
 * which redemption consumed the balance and for how much. [redemptionId] is the handle used to
 * reverse the redemption (compensation or refund-to-origin).
 */
data class GiftCardTender(
    val redemptionId: String,
    val amount: Amount,
)

/**
 * How an order total splits across tenders. **Order of application:** the gift card is consumed
 * first, and only the [remainder] is charged to the primary method (card). When [coversTotal],
 * no card — and therefore no 3D Secure — is needed at all.
 */
data class SplitPlan(
    val total: Amount,
    val giftCardPortion: Amount,
    val remainder: Amount,
) {
    val coversTotal: Boolean get() = remainder.isZero
}

/** Computes the tender split for [total]: gift card (capped at its balance) first, card after. */
fun planSplit(total: Amount, giftCard: GiftCard?): SplitPlan {
    val portion = giftCard?.balance?.coerceAtMost(total) ?: Amount(0, total.currency)
    return SplitPlan(total = total, giftCardPortion = portion, remainder = total - portion)
}
