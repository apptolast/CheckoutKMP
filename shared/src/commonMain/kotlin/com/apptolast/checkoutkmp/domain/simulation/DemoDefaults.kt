package com.apptolast.checkoutkmp.domain.simulation

import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.CardBrand
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.model.CardToken
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.domain.model.GiftCardTender
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.Receipt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Fixed demo credentials shared by the simulated PSP and the UI hints, so the value the user is
 * told to type always matches what the fake gateway accepts. This is a **demo/testing seam**, not
 * production data — no real secret ever lives here.
 */
object DemoDefaults {
    /** OTP accepted by the simulated 3D Secure challenge (also shown as a hint in the demo UI). */
    const val SCA_OTP = "123456"

    /** Cooldown before the 3D Secure code can be re-sent (mirrors real issuer throttling). */
    val OTP_RESEND_COOLDOWN: Duration = 30.seconds

    /** Demo gift card that covers only part of the demo order total. */
    const val GIFT_CARD_PARTIAL = "GIFT25"

    /** Demo gift card whose balance covers the whole demo order total. */
    const val GIFT_CARD_FULL = "GIFT100"

    /** Gift cards preloaded into the fake store: code → balance (also shown as a demo UI hint). */
    val giftCards: Map<String, Amount> = mapOf(
        GIFT_CARD_PARTIAL to Amount.of(major = 25, currency = Currency.EUR),
        GIFT_CARD_FULL to Amount.of(major = 100, currency = Currency.EUR),
    )

    private val demoVisa = PaymentMethod.Card(
        CardToken(value = "tok_demo_visa", brand = CardBrand.VISA, last4 = "4242", expiry = CardExpiry(month = 12, year = 2030)),
    )

    private val demoMastercard = PaymentMethod.Card(
        CardToken(value = "tok_demo_mc", brand = CardBrand.MASTERCARD, last4 = "5454", expiry = CardExpiry(month = 8, year = 2029)),
    )

    private val demoPaypal = PaymentMethod.Wallet(PaymentMethod.Wallet.Provider.PAYPAL)

    /**
     * A handful of already-settled orders preloaded into the session [OrderHistory][com.apptolast.checkoutkmp.domain.history.OrderHistory]
     * so the history screen is not empty on first open — the same demo/testing seam as the fake PSP
     * and gift-card store, never real order data. Timestamps are anchored to [now] so the list always
     * reads as recent, and one order per settlement outcome exercises every badge the UI can show:
     * awaiting capture (Authorized), charged (Captured), returned (Refunded), a released hold (Voided),
     * and a gift-card split. Receipts are PCI-safe by construction, so no PAN lives here either.
     *
     * Returned most-recently-updated first, matching how the history sorts.
     */
    fun demoOrders(now: Instant): List<Receipt> = listOf(
        // A card hold awaiting dispatch: authorized, not yet captured.
        Receipt(
            paymentId = "pay_demo_authorized",
            amount = Amount.of(major = 74, minor = 95, currency = Currency.EUR),
            method = demoVisa,
            createdAt = now - 6.hours,
            authorizedAt = now - 6.hours,
            authCode = "AUTH-3B9F",
        ),
        // A delivered order: authorized and then captured (customer charged).
        Receipt(
            paymentId = "pay_demo_captured",
            amount = Amount.of(major = 49, minor = 90, currency = Currency.EUR),
            method = demoMastercard,
            createdAt = now - 2.days,
            authorizedAt = now - 2.days,
            authCode = "AUTH-8F2A",
            capturedAt = now - 2.days + 3.hours,
        ),
        // A wallet order: charged in one step (wallets never pass through Authorized).
        Receipt(
            paymentId = "pay_demo_wallet",
            amount = Amount.of(major = 18, minor = 0, currency = Currency.EUR),
            method = demoPaypal,
            createdAt = now - 4.days,
            authorizedAt = now - 4.days,
            authCode = "AUTH-5D1C",
            capturedAt = now - 4.days,
        ),
        // A returned order: captured and then refunded to origin.
        Receipt(
            paymentId = "pay_demo_refunded",
            amount = Amount.of(major = 120, minor = 0, currency = Currency.EUR),
            method = demoVisa,
            createdAt = now - 6.days,
            authorizedAt = now - 6.days,
            authCode = "AUTH-1C7D",
            capturedAt = now - 6.days + 1.hours,
            refundedAt = now - 5.days,
        ),
        // A cancelled-before-dispatch order: the hold was voided without ever charging.
        Receipt(
            paymentId = "pay_demo_voided",
            amount = Amount.of(major = 33, minor = 50, currency = Currency.EUR),
            method = demoMastercard,
            createdAt = now - 7.days,
            authorizedAt = now - 7.days,
            authCode = "AUTH-9E4B",
            voidedAt = now - 6.days - 12.hours,
        ),
        // A split payment: GIFT25 covered 25.00, the card was charged the 5.00 remainder.
        Receipt(
            paymentId = "pay_demo_split",
            amount = Amount.of(major = 5, minor = 0, currency = Currency.EUR),
            method = demoVisa,
            createdAt = now - 9.days,
            authorizedAt = now - 9.days,
            authCode = "AUTH-2A6E",
            capturedAt = now - 9.days + 2.hours,
            giftCard = GiftCardTender(redemptionId = "gcr_demo_split", amount = Amount.of(major = 25, currency = Currency.EUR)),
        ),
    )
}
