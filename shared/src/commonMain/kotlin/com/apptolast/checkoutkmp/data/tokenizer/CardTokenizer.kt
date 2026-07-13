package com.apptolast.checkoutkmp.data.tokenizer

import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.model.CardToken
import com.apptolast.checkoutkmp.domain.model.PaymentError

/** Raw card input entered by the user. Lives only long enough to be tokenized, never persisted. */
data class RawCard(
    val pan: String,
    val expiry: CardExpiry,
    val cvv: String,
)

/** Outcome of tokenization: a PCI-safe [CardToken] or an [PaymentError.InvalidCard]. */
sealed interface TokenizationResult {
    data class Success(val token: CardToken) : TokenizationResult
    data class Failure(val error: PaymentError.InvalidCard) : TokenizationResult
}

/**
 * Exchanges raw card data for a PCI-safe [CardToken].
 *
 * **Golden rule:** the PAN and CVV are validated and then discarded — never logged, never stored,
 * never placed in the returned token. Only [CardToken.last4] and the brand survive.
 */
interface CardTokenizer {
    fun tokenize(card: RawCard): TokenizationResult
}
