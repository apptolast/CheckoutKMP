package com.apptolast.checkoutkmp.domain.model

/**
 * A pending redirect flow (PayPal, Bizum): the PSP created the order and the user must approve it
 * on the provider's [url], then come back through [returnUrl] (deep link).
 *
 * The return is only a **hint**: the state that counts is what the provider confirms to the PSP
 * asynchronously (webhook). Completing the redirect therefore *reconciles* against the PSP instead
 * of trusting the browser's return.
 */
data class RedirectChallenge(
    val redirectId: String,
    /** Display name of the provider the user must approve at (a proper noun, e.g. "PayPal"). */
    val provider: String,
    val url: String,
    val returnUrl: String,
)

/** What the user's return (deep link) claims happened on the provider's page. A claim, not truth. */
enum class RedirectReturn {
    APPROVED,
    CANCELLED,
    FAILED,
}
