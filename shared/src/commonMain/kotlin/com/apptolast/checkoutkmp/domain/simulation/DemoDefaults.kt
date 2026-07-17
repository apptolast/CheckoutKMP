package com.apptolast.checkoutkmp.domain.simulation

import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.Currency
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
}
