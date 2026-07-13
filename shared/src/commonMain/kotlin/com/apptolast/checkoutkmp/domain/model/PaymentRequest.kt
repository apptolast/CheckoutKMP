package com.apptolast.checkoutkmp.domain.model

/**
 * An immutable request to charge [amount] using [method]. [idempotencyKey] ties together the
 * initial authorization, any SCA completion and any transient retries of the same attempt.
 */
data class PaymentRequest(
    val amount: Amount,
    val method: PaymentMethod,
    val idempotencyKey: IdempotencyKey,
    val reference: String? = null,
)
