package com.apptolast.checkoutkmp.domain.model

/**
 * The outcome of a single PSP call as returned by [com.apptolast.checkoutkmp.domain.repository.PaymentRepository].
 * The repository has already mapped any raw PSP error to a [PaymentError] at the boundary, so
 * this is a closed, domain-friendly result.
 */
sealed interface PaymentResult {
    /** Funds held; the charge happens at capture. */
    data class Authorized(val receipt: Receipt) : PaymentResult

    /** The customer was charged (a completed capture or an immediate-capture authorization). */
    data class Captured(val receipt: Receipt) : PaymentResult

    /** A captured charge was returned to the customer. */
    data class Refunded(val receipt: Receipt) : PaymentResult

    data class RequiresSca(val challenge: ScaChallenge) : PaymentResult
    data class RequiresRedirect(val redirect: RedirectChallenge) : PaymentResult
    data class Failed(val error: PaymentError) : PaymentResult
}
