package com.apptolast.checkoutkmp.domain.model

/**
 * The full taxonomy of payment failures. The data layer maps raw PSP responses to exactly one of
 * these at the boundary (phase 7), so the domain and UI only ever reason about [PaymentError].
 *
 * [isTransient] drives the retry policy: only transient errors may be retried (with the same
 * [IdempotencyKey]). Business declines such as [Declined] and [InvalidCard] must never be retried.
 */
sealed interface PaymentError {
    val isTransient: Boolean

    /** The issuer/PSP refused the charge (insufficient funds, fraud rules, etc.). */
    data class Declined(val reason: String) : PaymentError {
        override val isTransient: Boolean get() = false
    }

    /** The card details are invalid (failed Luhn, expired, bad CVV). */
    data class InvalidCard(val reason: String) : PaymentError {
        override val isTransient: Boolean get() = false
    }

    /** Network connectivity problem reaching the PSP. */
    data object Network : PaymentError {
        override val isTransient: Boolean get() = true
    }

    /** The PSP did not respond within the deadline. */
    data object Timeout : PaymentError {
        override val isTransient: Boolean get() = true
    }

    /** Too many requests; back off and retry. */
    data object RateLimited : PaymentError {
        override val isTransient: Boolean get() = true
    }

    /** 3D Secure authentication failed (wrong OTP, challenge expired). */
    data class ScaFailed(val reason: String) : PaymentError {
        override val isTransient: Boolean get() = false
    }

    /** The user aborted the flow (e.g. cancelled the SCA challenge). */
    data object Cancelled : PaymentError {
        override val isTransient: Boolean get() = false
    }

    /** Anything not otherwise classified. */
    data class Unknown(val message: String?) : PaymentError {
        override val isTransient: Boolean get() = false
    }
}
