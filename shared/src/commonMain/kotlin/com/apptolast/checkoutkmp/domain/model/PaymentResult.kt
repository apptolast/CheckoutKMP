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

    /** The authorization hold was released without charging. */
    data class Voided(val receipt: Receipt) : PaymentResult

    /** The PSP requires 3D Secure; the user must complete [challenge] before the payment settles. */
    data class RequiresSca(val challenge: ScaChallenge) : PaymentResult

    /** The method needs approval on the provider's page; the user must complete [redirect]. */
    data class RequiresRedirect(val redirect: RedirectChallenge) : PaymentResult

    /** The call did not settle; [error] is the mapped domain reason (may be transient). */
    data class Failed(val error: PaymentError) : PaymentResult
}
