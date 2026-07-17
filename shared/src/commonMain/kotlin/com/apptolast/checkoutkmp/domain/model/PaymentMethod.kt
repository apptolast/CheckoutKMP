package com.apptolast.checkoutkmp.domain.model

/**
 * The instrument used to pay. Sealed so more methods (gift cards, redirect flows) can be added later.
 * Card payments carry a [CardToken] — already tokenized, never a raw PAN.
 *
 * [capturesImmediately] models **when the customer is charged**: cards are authorized at checkout
 * (funds held) and captured when the order is dispatched, while wallet-style methods charge in one
 * step, so their payments go straight to [PaymentState.Captured] and never pass through
 * [PaymentState.Authorized]. It is a property of the method — never an `if` scattered per flow.
 *
 * The payment method also conditions the **business after the sale**, not just the charge:
 * [afterSales] declares which post-sale operations the method supports (the retail detail that,
 * e.g., a size change does not exist for orders paid with PayPal).
 */
sealed interface PaymentMethod {
    val label: String

    /** True when the method charges at authorization time; false for authorize-then-capture. */
    val capturesImmediately: Boolean

    /** True when paying requires approving on the provider's page (redirect + webhook), like
     *  [requiresRedirect] wallets; false for methods settled in-app. A property of the method. */
    val requiresRedirect: Boolean

    /** Which post-sale operations this method supports. */
    val afterSales: AfterSalesPolicy

    data class Card(val token: CardToken) : PaymentMethod {
        override val label: String get() = "${token.brand.displayName} ${token.masked}"
        override val capturesImmediately: Boolean get() = false
        override val requiresRedirect: Boolean get() = false
        override val afterSales: AfterSalesPolicy get() =
            AfterSalesPolicy(canChangeSize = true, canRefundToOrigin = true)
    }

    /**
     * An external wallet (PayPal, Bizum): the user approves on the provider's page (redirect) and
     * the PSP confirms via webhook. Charges in one step — there is no separate capture.
     */
    data class Wallet(val provider: Provider) : PaymentMethod {
        override val label: String get() = provider.displayName
        override val capturesImmediately: Boolean get() = true
        override val requiresRedirect: Boolean get() = true

        /** The retailer cannot re-settle a wallet order with a different amount, so no size change. */
        override val afterSales: AfterSalesPolicy get() =
            AfterSalesPolicy(canChangeSize = false, canRefundToOrigin = true)

        enum class Provider(val displayName: String) {
            PAYPAL("PayPal"),
            BIZUM("Bizum"),
        }
    }

    /** A prepaid gift card: the balance is consumed at redemption, so it charges in one step. */
    data class GiftCard(val code: String) : PaymentMethod {
        override val label: String get() = code
        override val capturesImmediately: Boolean get() = true
        override val requiresRedirect: Boolean get() = false

        /** Refund-to-origin = reversing the redemption back onto the gift card. */
        override val afterSales: AfterSalesPolicy get() =
            AfterSalesPolicy(canChangeSize = true, canRefundToOrigin = true)
    }
}

/**
 * Post-sale operations a payment method admits. Declared per method — the medium of payment
 * conditions the business (returns desk, size changes), not only the charge itself.
 */
data class AfterSalesPolicy(
    /** Whether the order can be exchanged for a different size instead of refund + repurchase. */
    val canChangeSize: Boolean,
    /** Whether money can be returned to the original tender (vs store credit only). */
    val canRefundToOrigin: Boolean,
)
