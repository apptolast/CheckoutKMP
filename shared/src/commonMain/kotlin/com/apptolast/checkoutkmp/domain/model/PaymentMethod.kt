package com.apptolast.checkoutkmp.domain.model

/**
 * The instrument used to pay. Sealed so more methods (wallets, etc.) can be added later.
 * Card payments carry a [CardToken] — already tokenized, never a raw PAN.
 */
sealed interface PaymentMethod {
    val label: String

    data class Card(val token: CardToken) : PaymentMethod {
        override val label: String get() = "${token.brand.displayName} ${token.masked}"
    }
}
