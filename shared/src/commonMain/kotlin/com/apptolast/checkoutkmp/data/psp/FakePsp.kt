package com.apptolast.checkoutkmp.data.psp

import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.RedirectReturn
import com.apptolast.checkoutkmp.domain.simulation.DemoDefaults
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.domain.simulation.PaymentSimulator
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * In-memory PSP simulator for the demo and tests. It owns the payment **lifecycle**
 * (authorized → captured → refunded) and enforces its transitions, so it is the source of truth
 * the domain reconciles against.
 *
 * - [scenario] selects the outcome of [authorize]. Capture/refund only honor the *transient*
 *   scenarios (a merchant back-office call can fail in transit, but the demo decline knob is about
 *   the checkout authorization, not about settlements).
 * - [latency] is simulated with [delay] (skipped under `runTest`'s virtual time).
 * - **Idempotency per operation:** authorize, capture and refund each cache responses by their own
 *   [IdempotencyKey], so replaying any of them returns the identical response and never charges,
 *   captures or refunds twice ([chargeCount] / [captureCount] / [refundCount]).
 * - **Charge timing:** methods whose [PaymentMethod.capturesImmediately] is true settle inside
 *   [authorize] and never pass through the authorized-only stage.
 * - **Redirect + webhook:** for [PaymentMethod.requiresRedirect] methods, [authorize] only creates
 *   the provider order and answers [PspResponse.RedirectRequired]. The provider's decision arrives
 *   as a simulated **webhook** recorded here at order-creation time ([webhooks]) — the client never
 *   sees it directly. [completeRedirect] then reconciles the user's *claim* against that record:
 *   an "approved" return whose webhook was rejected is still declined. The charge itself happens
 *   only when the webhook confirms.
 * - **Holds are not forever:** an authorization can be [void]ed (order cancelled — the hold is
 *   released, nothing was charged) and it expires after [authorizationValidity]: capturing a
 *   lapsed hold is declined and the PSP releases it, exactly like real card networks do.
 *
 * PCI note: only tokens/last4 reach this class via [PaymentRequest]; no PAN is ever handled here.
 */
class FakePsp(
    override var scenario: PaymentScenario = PaymentScenario.APPROVED,
    private val latency: Duration = 0.milliseconds,
    private val validOtp: String = DemoDefaults.SCA_OTP,
    private val clock: Clock = Clock.System,
    private val authorizationValidity: Duration = DEFAULT_AUTHORIZATION_VALIDITY,
) : Psp, PaymentSimulator {

    private enum class Lifecycle { AUTHORIZED, CAPTURED, REFUNDED, VOIDED }
    private class PaymentRecord(
        var lifecycle: Lifecycle,
        val authCode: String,
        val authorizedAt: Instant,
    )

    /** What the provider confirmed to the PSP for a redirect order — the source of truth. */
    private enum class WebhookOutcome { CONFIRMED, REJECTED }

    private val authorizations = mutableMapOf<IdempotencyKey, PspResponse>()
    private val captures = mutableMapOf<IdempotencyKey, PspResponse>()
    private val voids = mutableMapOf<IdempotencyKey, PspResponse>()
    private val refunds = mutableMapOf<IdempotencyKey, PspResponse>()

    /** Lifecycle ledger per PSP payment id — what has really happened to each payment. */
    private val payments = mutableMapOf<String, PaymentRecord>()

    /** Simulated webhook inbox per redirect id, recorded when the provider order is created. */
    private val webhooks = mutableMapOf<String, WebhookOutcome>()

    /** Number of real authorizations performed (idempotent replays do not increment it). */
    var chargeCount: Int = 0
        private set

    /** Number of real captures performed (idempotent replays do not increment it). */
    var captureCount: Int = 0
        private set

    /** Number of real refunds performed (idempotent replays do not increment it). */
    var refundCount: Int = 0
        private set

    /** Number of real voids performed (idempotent replays and expiries do not increment it). */
    var voidCount: Int = 0
        private set

    override suspend fun authorize(request: PaymentRequest): PspResponse {
        delay(latency)

        // A transport failure never produces a business decision and is not cached.
        transientKind(scenario)?.let { throw PspException(it, "Simulated $it failure") }

        // Idempotent replay: same key -> same response, no new charge.
        authorizations[request.idempotencyKey]?.let { return it }

        // Redirect methods: only create the provider order — nothing is charged yet. The
        // provider's decision "arrives" as a webhook the client never sees; the demo decline
        // knob decides what that webhook will say.
        if (request.method.requiresRedirect) {
            val response = createRedirectOrder()
            authorizations[request.idempotencyKey] = response
            return response
        }

        chargeCount++
        val response = when (scenario) {
            PaymentScenario.APPROVED -> settle(request.method)
            PaymentScenario.NEEDS_SCA -> PspResponse.ScaRequired(
                challengeId = "ch_${Uuid.random()}",
                deliveryHint = DELIVERY_HINT,
                otpLength = validOtp.length,
            )
            PaymentScenario.DECLINED -> PspResponse.Declined(
                code = "insufficient_funds",
                message = "The card was declined",
            )
            PaymentScenario.NETWORK_ERROR,
            PaymentScenario.TIMEOUT,
            PaymentScenario.RATE_LIMITED -> error("handled above")
        }
        authorizations[request.idempotencyKey] = response
        return response
    }

    private fun createRedirectOrder(): PspResponse.RedirectRequired {
        val redirectId = "rd_${Uuid.random()}"
        webhooks[redirectId] = when (scenario) {
            PaymentScenario.DECLINED -> WebhookOutcome.REJECTED
            else -> WebhookOutcome.CONFIRMED
        }
        return PspResponse.RedirectRequired(
            redirectId = redirectId,
            url = "$REDIRECT_BASE_URL$redirectId",
            returnUrl = RETURN_URL,
        )
    }

    override suspend fun completeSca(request: PaymentRequest, otp: String): PspResponse {
        delay(latency)

        val pending = authorizations[request.idempotencyKey]
        if (pending !is PspResponse.ScaRequired) {
            return PspResponse.ScaFailed("no_pending_challenge")
        }
        if (otp != validOtp) {
            // Keep the challenge pending so the user can retry the OTP.
            return PspResponse.ScaFailed("wrong_otp")
        }

        // Settle the ledger so a replayed authorize/completeSca is idempotent.
        return settle(request.method).also { authorizations[request.idempotencyKey] = it }
    }

    override suspend fun resendSca(request: PaymentRequest): PspResponse {
        delay(latency)
        transientKind(scenario)?.let { throw PspException(it, "Simulated $it failure") }

        // Reissuing never consumes the challenge: the same pending record is returned, so the
        // original OTP keeps working and completeSca stays untouched.
        return when (val pending = authorizations[request.idempotencyKey]) {
            is PspResponse.ScaRequired -> pending
            else -> PspResponse.ScaFailed("no_pending_challenge")
        }
    }

    override suspend fun completeRedirect(request: PaymentRequest, returned: RedirectReturn): PspResponse {
        delay(latency)
        transientKind(scenario)?.let { throw PspException(it, "Simulated $it failure") }

        val pending = authorizations[request.idempotencyKey]
            ?: return PspResponse.Declined("no_pending_redirect", "No redirect order for this key")
        if (pending !is PspResponse.RedirectRequired) {
            // Already reconciled: replaying the completion returns the settled outcome, idempotently.
            return pending
        }

        val response = when (returned) {
            RedirectReturn.CANCELLED ->
                PspResponse.Declined("redirect_cancelled", "The user cancelled at the provider")

            RedirectReturn.FAILED ->
                PspResponse.Declined("redirect_failed", "The provider reported a failure")

            // The user's claim is reconciled against the webhook — the provider's word wins.
            RedirectReturn.APPROVED -> when (webhooks.getValue(pending.redirectId)) {
                WebhookOutcome.CONFIRMED -> {
                    chargeCount++
                    settle(request.method)
                }

                WebhookOutcome.REJECTED ->
                    PspResponse.Declined("webhook_rejected", "The provider did not confirm the payment")
            }
        }
        authorizations[request.idempotencyKey] = response
        return response
    }

    override suspend fun capture(pspPaymentId: String, idempotencyKey: IdempotencyKey): PspResponse {
        delay(latency)
        transientKind(scenario)?.let { throw PspException(it, "Simulated $it failure") }

        captures[idempotencyKey]?.let { return it }

        val record = payments[pspPaymentId]
        val response = when (record?.lifecycle) {
            null -> PspResponse.Declined("unknown_payment", "No payment with id $pspPaymentId")
            Lifecycle.CAPTURED -> PspResponse.Declined("already_captured", "The payment is already captured")
            Lifecycle.REFUNDED -> PspResponse.Declined("already_refunded", "The payment was refunded")
            Lifecycle.VOIDED -> PspResponse.Declined("authorization_voided", "The hold was already released")
            Lifecycle.AUTHORIZED ->
                if (clock.now() - record.authorizedAt > authorizationValidity) {
                    // The hold lapsed: the network releases it, so there is nothing left to charge.
                    record.lifecycle = Lifecycle.VOIDED
                    PspResponse.Declined("authorization_expired", "The authorization hold has expired")
                } else {
                    captureCount++
                    record.lifecycle = Lifecycle.CAPTURED
                    PspResponse.Captured(pspPaymentId, record.authCode)
                }
        }
        captures[idempotencyKey] = response
        return response
    }

    override suspend fun void(pspPaymentId: String, idempotencyKey: IdempotencyKey): PspResponse {
        delay(latency)
        transientKind(scenario)?.let { throw PspException(it, "Simulated $it failure") }

        voids[idempotencyKey]?.let { return it }

        val record = payments[pspPaymentId]
        val response = when (record?.lifecycle) {
            null -> PspResponse.Declined("unknown_payment", "No payment with id $pspPaymentId")
            Lifecycle.CAPTURED -> PspResponse.Declined("already_captured", "A captured payment is refunded, not voided")
            Lifecycle.REFUNDED -> PspResponse.Declined("already_refunded", "The payment was refunded")
            Lifecycle.VOIDED -> PspResponse.Declined("already_voided", "The hold was already released")
            Lifecycle.AUTHORIZED -> {
                voidCount++
                record.lifecycle = Lifecycle.VOIDED
                PspResponse.Voided(pspPaymentId)
            }
        }
        voids[idempotencyKey] = response
        return response
    }

    override suspend fun refund(pspPaymentId: String, idempotencyKey: IdempotencyKey): PspResponse {
        delay(latency)
        transientKind(scenario)?.let { throw PspException(it, "Simulated $it failure") }

        refunds[idempotencyKey]?.let { return it }

        val record = payments[pspPaymentId]
        val response = when (record?.lifecycle) {
            null -> PspResponse.Declined("unknown_payment", "No payment with id $pspPaymentId")
            Lifecycle.AUTHORIZED -> PspResponse.Declined("not_captured", "Only captured payments can be refunded")
            Lifecycle.REFUNDED -> PspResponse.Declined("already_refunded", "The payment was already refunded")
            Lifecycle.VOIDED -> PspResponse.Declined("authorization_voided", "Nothing was charged, nothing to refund")
            Lifecycle.CAPTURED -> {
                refundCount++
                record.lifecycle = Lifecycle.REFUNDED
                PspResponse.Refunded(pspPaymentId)
            }
        }
        refunds[idempotencyKey] = response
        return response
    }

    private fun transientKind(scenario: PaymentScenario): PspException.Kind? = when (scenario) {
        PaymentScenario.NETWORK_ERROR -> PspException.Kind.NETWORK
        PaymentScenario.TIMEOUT -> PspException.Kind.TIMEOUT
        PaymentScenario.RATE_LIMITED -> PspException.Kind.RATE_LIMITED
        else -> null
    }

    /** Books a fresh approved payment: hold funds, or charge at once for immediate-capture methods. */
    private fun settle(method: PaymentMethod): PspResponse {
        val paymentId = "pay_${Uuid.random()}"
        val authCode = "AUTH${Uuid.random().toString().take(AUTH_CODE_LENGTH).uppercase()}"
        return if (method.capturesImmediately) {
            payments[paymentId] = PaymentRecord(Lifecycle.CAPTURED, authCode, clock.now())
            PspResponse.Captured(paymentId, authCode)
        } else {
            payments[paymentId] = PaymentRecord(Lifecycle.AUTHORIZED, authCode, clock.now())
            PspResponse.Authorized(paymentId, authCode)
        }
    }

    companion object {
        /** How long a simulated authorization hold stays capturable (typical card-network window). */
        val DEFAULT_AUTHORIZATION_VALIDITY = 7.days

        /** Length of the random suffix in a simulated authorization code. */
        private const val AUTH_CODE_LENGTH = 6

        /** Masked destination shown for the demo 3D Secure code delivery. */
        private const val DELIVERY_HINT = "•••• 90"

        /** Base of the simulated provider approval page (a real app would open it in Custom Tabs). */
        private const val REDIRECT_BASE_URL = "https://psp.example/redirect/"

        /** Deep link the provider sends the user back to after approving/cancelling. */
        private const val RETURN_URL = "checkoutkmp://payment/return"
    }
}
