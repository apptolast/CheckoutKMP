package com.apptolast.checkoutkmp.domain.model

/**
 * The outcome of a single PSP call as returned by [com.apptolast.checkoutkmp.domain.repository.PaymentRepository].
 * The repository has already mapped any raw PSP error to a [PaymentError] at the boundary, so
 * this is a closed, domain-friendly result.
 */
sealed interface PaymentResult {
    data class Authorized(val receipt: Receipt) : PaymentResult
    data class RequiresSca(val challenge: ScaChallenge) : PaymentResult
    data class Failed(val error: PaymentError) : PaymentResult
}
