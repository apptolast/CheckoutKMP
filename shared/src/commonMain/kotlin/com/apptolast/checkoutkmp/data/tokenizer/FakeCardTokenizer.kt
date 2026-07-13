package com.apptolast.checkoutkmp.data.tokenizer

import com.apptolast.checkoutkmp.domain.model.CardBrand
import com.apptolast.checkoutkmp.domain.model.CardToken
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.usecase.Luhn
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.uuid.Uuid

/**
 * Local, in-memory [CardTokenizer] for the demo. Validates the card, then mints an **opaque** token
 * that has no derivable relationship to the PAN. The PAN/CVV are used only inside [tokenize] and
 * are never logged, stored, or embedded in the result.
 */
class FakeCardTokenizer(
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : CardTokenizer {

    override fun tokenize(card: RawCard): TokenizationResult {
        val pan = card.pan.filterNot { it.isWhitespace() }

        if (pan.length !in 12..19 || pan.any { !it.isDigit() }) {
            return failure("Card number must be 12–19 digits")
        }
        if (!Luhn.isValid(pan)) {
            return failure("Card number failed the Luhn check")
        }
        if (card.expiry.isExpired(clock.todayIn(timeZone))) {
            return failure("Card has expired")
        }
        if (card.cvv.length !in 3..4 || card.cvv.any { !it.isDigit() }) {
            return failure("Invalid security code")
        }

        val token = CardToken(
            value = "tok_${Uuid.random()}", // opaque; unrelated to the PAN
            brand = CardBrand.detect(pan),
            last4 = pan.takeLast(4),
            expiry = card.expiry,
        )
        return TokenizationResult.Success(token)
    }

    private fun failure(reason: String) =
        TokenizationResult.Failure(PaymentError.InvalidCard(reason))
}
