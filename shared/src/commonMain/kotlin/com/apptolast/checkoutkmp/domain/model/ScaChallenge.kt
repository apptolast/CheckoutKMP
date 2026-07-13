package com.apptolast.checkoutkmp.domain.model

/**
 * A Strong Customer Authentication (3D Secure) challenge issued by the PSP. The user must
 * provide the OTP delivered to [deliveryHint] to complete authentication.
 */
data class ScaChallenge(
    val challengeId: String,
    val deliveryHint: String? = null,
    val otpLength: Int = 6,
) {
    init {
        require(challengeId.isNotBlank()) { "challengeId must not be blank" }
        require(otpLength in 3..10) { "Unrealistic OTP length: $otpLength" }
    }
}
