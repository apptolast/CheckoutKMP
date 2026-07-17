package com.apptolast.checkoutkmp.domain.model

/**
 * The instrument used to pay. Sealed so more methods (gift cards, redirect flows) can be added later.
 * Card payments carry a [CardToken] — already tokenized, never a raw PAN.
 *
 * [capturesImmediately] models **when the customer is charged**: cards are authorized at checkout
 * (funds held) and captured when the order is dispatched, while wallet-style methods charge in one
 * step, so their payments go straight to [PaymentState.Captured] and never pass through
 * [PaymentState.Authorized]. It is a property of the method — never an `if` scattered per flow.
 */
sealed interface PaymentMethod {
    val label: String

    /** True when the method charges at authorization time; false for authorize-then-capture. */
    val capturesImmediately: Boolean

    /** True when paying requires approving on the provider's page (redirect + webhook), like
     *  [requiresRedirect] wallets; false for methods settled in-app. A property of the method. */
    val requiresRedirect: Boolean

    data class Card(val token: CardToken) : PaymentMethod {
        override val label: String get() = "${token.brand.displayName} ${token.masked}"
        override val capturesImmediately: Boolean get() = false
        override val requiresRedirect: Boolean get() = false
    }

    /**
     * An external wallet (PayPal, Bizum): the user approves on the provider's page (redirect) and
     * the PSP confirms via webhook. Charges in one step — there is no separate capture.
     */
    data class Wallet(val provider: Provider) : PaymentMethod {
        override val label: String get() = provider.displayName
        override val capturesImmediately: Boolean get() = true
        override val requiresRedirect: Boolean get() = true

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
    }
}
