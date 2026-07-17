package com.apptolast.checkoutkmp.domain.model

/**
 * The payment state machine surfaced to the UI.
 *
 * ```
 * Idle ──► Processing ──► Authorized ──(capture)──► Captured ──(refund)──► Refunded
 *                    ├──► Captured      (immediate-capture methods skip Authorized)
 *                    ├──► RequiresSca ──(completeSca)──► Processing ──► Authorized / Captured
 *                    │                                              └──► Failed
 *                    ├──► RequiresRedirect ──(completeRedirect: reconciled against the PSP's
 *                    │                        webhook, NOT the user's return)──► Captured / Failed
 *                    └──► Failed        (Declined / InvalidCard / Network / Timeout /
 *                                        RateLimited / ScaFailed / Cancelled / Unknown)
 * ```
 *
 * Retail split between **authorization** (funds held at checkout) and **capture** (the real charge
 * when the order is dispatched): [Authorized] can still become [Captured], and [Captured] can be
 * [Refunded] — but only through an explicit merchant operation, never automatically. A [Failed]
 * whose error [PaymentError.isTransient] is true may be retried with the same [IdempotencyKey].
 */
sealed interface PaymentState {
    /** Nothing in flight; the resting state before an attempt starts. */
    data object Idle : PaymentState

    /** A PSP call is in flight (authorization or SCA completion). */
    data object Processing : PaymentState

    /** The PSP requires 3D Secure; the user must complete [challenge]. */
    data class RequiresSca(val challenge: ScaChallenge) : PaymentState

    /** The method needs approval on the provider's page; the user must complete [redirect]. */
    data class RequiresRedirect(val redirect: RedirectChallenge) : PaymentState

    /** Funds are held on the customer's card; the charge happens at capture (order dispatch). */
    data class Authorized(val receipt: Receipt) : PaymentState

    /** The customer has actually been charged (deferred capture or an immediate-capture method). */
    data class Captured(val receipt: Receipt) : PaymentState

    /** A captured charge was returned to the customer. */
    data class Refunded(val receipt: Receipt) : PaymentState

    /** Terminal failure carrying the classified [error]. */
    data class Failed(val error: PaymentError) : PaymentState

    /**
     * True once the attempt has reached an outcome and nothing is in flight. Settled is not final:
     * [Authorized] and [Captured] still accept the capture/refund merchant operations.
     */
    val isSettled: Boolean
        get() = this is Authorized || this is Captured || this is Refunded || this is Failed
}

/** Maps a repository [PaymentResult] into the corresponding [PaymentState]. */
fun PaymentResult.toPaymentState(): PaymentState = when (this) {
    is PaymentResult.Authorized -> PaymentState.Authorized(receipt)
    is PaymentResult.Captured -> PaymentState.Captured(receipt)
    is PaymentResult.Refunded -> PaymentState.Refunded(receipt)
    is PaymentResult.RequiresSca -> PaymentState.RequiresSca(challenge)
    is PaymentResult.RequiresRedirect -> PaymentState.RequiresRedirect(redirect)
    is PaymentResult.Failed -> PaymentState.Failed(error)
}
