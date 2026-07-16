package com.apptolast.checkoutkmp.domain.model

/**
 * A Strong Customer Authentication (3D Secure) challenge issued by the PSP. The user must
 * provide the OTP delivered to [deliveryHint] to complete authentication.
 */
data class ScaChallenge(
    val challengeId: String,
    val deliveryHint: String? = null,
    val otpLength: Int = DEFAULT_OTP_LENGTH,
) {
    init {
        require(challengeId.isNotBlank()) { "challengeId must not be blank" }
        require(otpLength in OTP_LENGTH_RANGE) { "Unrealistic OTP length: $otpLength" }
    }

    companion object {
        /** OTP length used by most issuers (and the demo). */
        const val DEFAULT_OTP_LENGTH = 6

        /** Plausibility bounds for a one-time code. */
        val OTP_LENGTH_RANGE = 3..10
    }
}
