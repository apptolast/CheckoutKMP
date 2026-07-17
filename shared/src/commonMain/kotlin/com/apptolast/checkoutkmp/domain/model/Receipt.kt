package com.apptolast.checkoutkmp.domain.model

import kotlin.time.Instant

/**
 * Proof of a payment and its settlement lifecycle. PCI-safe: [method] carries at most a token and
 * a masked card, never the PAN.
 *
 * [capturedAt]/[refundedAt] record when each settlement step happened: both `null` means the funds
 * are only authorized (held), [capturedAt] set means the customer was actually charged, and
 * [refundedAt] set means the charge was returned.
 */
data class Receipt(
    val paymentId: String,
    val amount: Amount,
    val method: PaymentMethod,
    val authorizedAt: Instant,
    val authCode: String,
    val capturedAt: Instant? = null,
    val refundedAt: Instant? = null,
)
