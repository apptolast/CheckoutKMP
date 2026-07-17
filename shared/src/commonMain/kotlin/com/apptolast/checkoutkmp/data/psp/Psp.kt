package com.apptolast.checkoutkmp.data.psp

import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.RedirectReturn

/**
 * Low-level gateway to a Payment Service Provider. Returns **raw PSP responses** ([PspResponse])
 * for business outcomes and throws [PspException] for transport failures. Mapping these into the
 * domain [com.apptolast.checkoutkmp.domain.model.PaymentError] happens at the repository boundary.
 */
interface Psp {
    /** Authorize a charge (immediate-capture methods settle in this same call).
     *  Must be idempotent on [PaymentRequest.idempotencyKey]. */
    suspend fun authorize(request: PaymentRequest): PspResponse

    /** Resolve a pending 3D Secure challenge for [request] with the user-entered [otp]. */
    suspend fun completeSca(request: PaymentRequest, otp: String): PspResponse

    /**
     * Reconcile a pending redirect after the user returns claiming [returned]. The response is
     * decided by what the provider confirmed via **webhook**, not by the claim. Idempotent on
     * [PaymentRequest.idempotencyKey].
     */
    suspend fun completeRedirect(request: PaymentRequest, returned: RedirectReturn): PspResponse

    /** Capture a previously authorized payment. Must be idempotent on [idempotencyKey]. */
    suspend fun capture(pspPaymentId: String, idempotencyKey: IdempotencyKey): PspResponse

    /** Refund a previously captured payment. Must be idempotent on [idempotencyKey]. */
    suspend fun refund(pspPaymentId: String, idempotencyKey: IdempotencyKey): PspResponse
}

/** Raw, PSP-level outcome of a call. The repository maps these to domain results. */
sealed interface PspResponse {
    /** Funds held on the customer's card; a capture must follow to actually charge. */
    data class Authorized(val pspPaymentId: String, val authCode: String) : PspResponse

    /** The order was created at the provider; the user must approve it at [url]. */
    data class RedirectRequired(
        val redirectId: String,
        val url: String,
        val returnUrl: String,
    ) : PspResponse

    /** The charge is settled — either a completed capture or an immediate-capture authorization. */
    data class Captured(val pspPaymentId: String, val authCode: String) : PspResponse

    /** A captured charge was returned to the customer. */
    data class Refunded(val pspPaymentId: String) : PspResponse

    data class ScaRequired(
        val challengeId: String,
        val deliveryHint: String?,
        val otpLength: Int,
    ) : PspResponse

    data class Declined(val code: String, val message: String) : PspResponse
    data class ScaFailed(val reason: String) : PspResponse
}

/**
 * A transport-level failure talking to the PSP (no business decision was reached). These are the
 * only failures that may be safely retried with the same idempotency key.
 */
class PspException(
    val kind: Kind,
    message: String,
) : Exception(message) {
    enum class Kind { NETWORK, TIMEOUT, RATE_LIMITED }
}
