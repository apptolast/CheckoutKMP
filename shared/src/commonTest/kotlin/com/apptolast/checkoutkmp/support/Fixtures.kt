package com.apptolast.checkoutkmp.support

import com.apptolast.checkoutkmp.domain.giftcard.GiftCardRedemption
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.CardBrand
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.model.CardToken
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.domain.model.GiftCard
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.model.ScaChallenge
import kotlin.time.Instant

/** Reusable, valid domain objects for tests. */
object Fixtures {
    val expiry = CardExpiry(month = 12, year = 2030)

    val visaToken = CardToken(
        value = "tok_visa_test",
        brand = CardBrand.VISA,
        last4 = "4242",
        expiry = expiry,
    )

    val method = PaymentMethod.Card(visaToken)

    /** An immediate-capture method: charges at authorization, never passes through Authorized. */
    val walletMethod = PaymentMethod.Wallet(PaymentMethod.Wallet.Provider.PAYPAL)

    val amount = Amount(minorUnits = 1050, currency = Currency.EUR)

    fun request(
        key: IdempotencyKey = IdempotencyKey.random(),
        method: PaymentMethod = this.method,
    ): PaymentRequest = PaymentRequest(amount = amount, method = method, idempotencyKey = key)

    /** Authorized-only receipt: funds held, the customer has not been charged yet. */
    val receipt = Receipt(
        paymentId = "pay_123",
        amount = amount,
        method = method,
        authorizedAt = Instant.fromEpochSeconds(1_700_000_000),
        authCode = "AUTH42",
    )

    val capturedReceipt = receipt.copy(capturedAt = Instant.fromEpochSeconds(1_700_000_100))

    val refundedReceipt = capturedReceipt.copy(refundedAt = Instant.fromEpochSeconds(1_700_000_200))

    /** An authorization whose hold was released without charging. */
    val voidedReceipt = receipt.copy(voidedAt = Instant.fromEpochSeconds(1_700_000_150))

    val challenge = ScaChallenge(challengeId = "ch_1", deliveryHint = "•••• 90", otpLength = 6)

    /** Gift card that covers only part of [amount] (10.50 EUR total, 4.00 EUR balance). */
    val partialGiftCard = GiftCard(code = "GIFT4", balance = Amount(minorUnits = 400, currency = Currency.EUR))

    /** Gift card whose balance covers all of [amount]. */
    val coveringGiftCard = GiftCard(code = "GIFT20", balance = Amount(minorUnits = 2000, currency = Currency.EUR))

    fun redemption(amount: Amount, code: String = partialGiftCard.code): GiftCardRedemption =
        GiftCardRedemption(
            redemptionId = "gcr_1",
            code = code,
            amount = amount,
            redeemedAt = Instant.fromEpochSeconds(1_700_000_050),
        )
}
