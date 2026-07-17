package com.apptolast.checkoutkmp.data.psp

import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.simulation.DemoDefaults
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.domain.simulation.PaymentSimulator
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
 *
 * PCI note: only tokens/last4 reach this class via [PaymentRequest]; no PAN is ever handled here.
 */
class FakePsp(
    override var scenario: PaymentScenario = PaymentScenario.APPROVED,
    private val latency: Duration = 0.milliseconds,
    private val validOtp: String = DemoDefaults.SCA_OTP,
) : Psp, PaymentSimulator {

    private enum class Lifecycle { AUTHORIZED, CAPTURED, REFUNDED }
    private class PaymentRecord(var lifecycle: Lifecycle, val authCode: String)

    private val authorizations = mutableMapOf<IdempotencyKey, PspResponse>()
    private val captures = mutableMapOf<IdempotencyKey, PspResponse>()
    private val refunds = mutableMapOf<IdempotencyKey, PspResponse>()

    /** Lifecycle ledger per PSP payment id — what has really happened to each payment. */
    private val payments = mutableMapOf<String, PaymentRecord>()

    /** Number of real authorizations performed (idempotent replays do not increment it). */
    var chargeCount: Int = 0
        private set

    /** Number of real captures performed (idempotent replays do not increment it). */
    var captureCount: Int = 0
        private set

    /** Number of real refunds performed (idempotent replays do not increment it). */
    var refundCount: Int = 0
        private set

    override suspend fun authorize(request: PaymentRequest): PspResponse {
        delay(latency)

        // A transport failure never produces a business decision and is not cached.
        transientKind(scenario)?.let { throw PspException(it, "Simulated $it failure") }

        // Idempotent replay: same key -> same response, no new charge.
        authorizations[request.idempotencyKey]?.let { return it }

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

    override suspend fun capture(pspPaymentId: String, idempotencyKey: IdempotencyKey): PspResponse {
        delay(latency)
        transientKind(scenario)?.let { throw PspException(it, "Simulated $it failure") }

        captures[idempotencyKey]?.let { return it }

        val record = payments[pspPaymentId]
        val response = when (record?.lifecycle) {
            null -> PspResponse.Declined("unknown_payment", "No payment with id $pspPaymentId")
            Lifecycle.CAPTURED -> PspResponse.Declined("already_captured", "The payment is already captured")
            Lifecycle.REFUNDED -> PspResponse.Declined("already_refunded", "The payment was refunded")
            Lifecycle.AUTHORIZED -> {
                captureCount++
                record.lifecycle = Lifecycle.CAPTURED
                PspResponse.Captured(pspPaymentId, record.authCode)
            }
        }
        captures[idempotencyKey] = response
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
            payments[paymentId] = PaymentRecord(Lifecycle.CAPTURED, authCode)
            PspResponse.Captured(paymentId, authCode)
        } else {
            payments[paymentId] = PaymentRecord(Lifecycle.AUTHORIZED, authCode)
            PspResponse.Authorized(paymentId, authCode)
        }
    }

    private companion object {
        /** Length of the random suffix in a simulated authorization code. */
        const val AUTH_CODE_LENGTH = 6

        /** Masked destination shown for the demo 3D Secure code delivery. */
        const val DELIVERY_HINT = "•••• 90"
    }
}
