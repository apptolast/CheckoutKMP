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

    data class Card(val token: CardToken) : PaymentMethod {
        override val label: String get() = "${token.brand.displayName} ${token.masked}"
        override val capturesImmediately: Boolean get() = false
    }

    /** An external wallet (PayPal, Bizum). Charges in one step — there is no separate capture. */
    data class Wallet(val provider: Provider) : PaymentMethod {
        override val label: String get() = provider.displayName
        override val capturesImmediately: Boolean get() = true

        enum class Provider(val displayName: String) {
            PAYPAL("PayPal"),
            BIZUM("Bizum"),
        }
    }
}
