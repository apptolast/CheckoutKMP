package com.apptolast.checkoutkmp.data.tokenizer

import com.apptolast.checkoutkmp.domain.model.CardBrand
import com.apptolast.checkoutkmp.domain.model.CardRules
import com.apptolast.checkoutkmp.domain.model.CardToken
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.tokenizer.CardTokenizer
import com.apptolast.checkoutkmp.domain.tokenizer.RawCard
import com.apptolast.checkoutkmp.domain.tokenizer.TokenizationResult
import com.apptolast.checkoutkmp.domain.usecase.Luhn
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.uuid.Uuid

/**
 * Local, in-memory [CardTokenizer] for the demo. Validates the card, then mints an **opaque** token
 * that has no derivable relationship to the PAN. The PAN/CVV are used only inside [tokenize] and
 * are never logged, stored, or embedded in the result.
 *
 * The failure [reason]s are diagnostic codes, not user-facing copy — the UI shows its own localized
 * message for [PaymentError.InvalidCard].
 */
class FakeCardTokenizer(
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : CardTokenizer {

    override fun tokenize(card: RawCard): TokenizationResult {
        val pan = card.pan.filterNot { it.isWhitespace() }

        if (pan.length !in CardRules.PAN_LENGTHS || pan.any { !it.isDigit() }) {
            return failure("invalid_length")
        }
        if (!Luhn.isValid(pan)) {
            return failure("luhn_failed")
        }
        if (card.expiry.isExpired(clock.todayIn(timeZone))) {
            return failure("expired")
        }
        if (card.cvv.length !in CardRules.CVV_LENGTHS || card.cvv.any { !it.isDigit() }) {
            return failure("invalid_cvv")
        }

        val token = CardToken(
            value = "tok_${Uuid.random()}", // opaque; unrelated to the PAN
            brand = CardBrand.detect(pan),
            last4 = pan.takeLast(CardRules.LAST4_LENGTH),
            expiry = card.expiry,
        )
        return TokenizationResult.Success(token)
    }

    private fun failure(reason: String) =
        TokenizationResult.Failure(PaymentError.InvalidCard(reason))
}
