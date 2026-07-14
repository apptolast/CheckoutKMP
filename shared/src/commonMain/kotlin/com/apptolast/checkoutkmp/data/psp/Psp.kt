package com.apptolast.checkoutkmp.data.psp

import com.apptolast.checkoutkmp.domain.model.PaymentRequest

/**
 * Low-level gateway to a Payment Service Provider. Returns **raw PSP responses** ([PspResponse])
 * for business outcomes and throws [PspException] for transport failures. Mapping these into the
 * domain [com.apptolast.checkoutkmp.domain.model.PaymentError] happens at the repository boundary.
 */
interface Psp {
    /** Authorize a charge. Must be idempotent on [PaymentRequest.idempotencyKey]. */
    suspend fun authorize(request: PaymentRequest): PspResponse

    /** Resolve a pending 3D Secure challenge for [request] with the user-entered [otp]. */
    suspend fun completeSca(request: PaymentRequest, otp: String): PspResponse
}

/** Raw, PSP-level outcome of a call. The repository maps these to domain results. */
sealed interface PspResponse {
    data class Approved(val pspPaymentId: String, val authCode: String) : PspResponse
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

/**
 * Behaviour the [FakePsp] should simulate, wired via DI or toggled in the UI for the demo.
 * [NETWORK_ERROR], [TIMEOUT] and [RATE_LIMITED] are transport failures (transient) that the retry
 * decorator may retry; [DECLINED] is a business decision that must not be retried.
 */
enum class PspScenario { APPROVED, NEEDS_SCA, DECLINED, NETWORK_ERROR, TIMEOUT, RATE_LIMITED }
