package com.apptolast.checkoutkmp.domain.model

import kotlinx.datetime.Instant

/**
 * Proof of a successful (authorized) payment. PCI-safe: it carries only the [maskedCard] and
 * [brand], never the PAN.
 */
data class Receipt(
    val paymentId: String,
    val amount: Amount,
    val brand: CardBrand,
    val maskedCard: String,
    val authorizedAt: Instant,
    val authCode: String,
)
