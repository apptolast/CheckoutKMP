package com.apptolast.checkoutkmp.domain.model

/**
 * The payment state machine surfaced to the UI.
 *
 * ```
 * Idle ──► Processing ──► Approved
 *                    ├──► RequiresSca ──(completeSca)──► Processing ──► Approved
 *                    │                                              └──► Failed
 *                    └──► Failed        (Declined / InvalidCard / Network / Timeout /
 *                                        RateLimited / ScaFailed / Cancelled / Unknown)
 * ```
 *
 * [Approved] and [Failed] are terminal. A terminal [Failed] whose error [PaymentError.isTransient]
 * is true may be retried by re-running the use case with the same [IdempotencyKey].
 */
sealed interface PaymentState {
    /** Nothing in flight; the resting state before an attempt starts. */
    data object Idle : PaymentState

    /** A PSP call is in flight (authorization or SCA completion). */
    data object Processing : PaymentState

    /** The PSP requires 3D Secure; the user must complete [challenge]. */
    data class RequiresSca(val challenge: ScaChallenge) : PaymentState

    /** Terminal success. */
    data class Approved(val receipt: Receipt) : PaymentState

    /** Terminal failure carrying the classified [error]. */
    data class Failed(val error: PaymentError) : PaymentState

    val isTerminal: Boolean get() = this is Approved || this is Failed
}

/** Maps a repository [PaymentResult] into the corresponding [PaymentState]. */
fun PaymentResult.toPaymentState(): PaymentState = when (this) {
    is PaymentResult.Authorized -> PaymentState.Approved(receipt)
    is PaymentResult.RequiresSca -> PaymentState.RequiresSca(challenge)
    is PaymentResult.Failed -> PaymentState.Failed(error)
}
