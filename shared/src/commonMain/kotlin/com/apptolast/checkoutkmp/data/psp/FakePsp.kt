package com.apptolast.checkoutkmp.data.psp

import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.domain.simulation.PaymentSimulator
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/**
 * In-memory PSP simulator for the demo and tests.
 *
 * - [scenario] selects the outcome of [authorize].
 * - [latency] is simulated with [delay] (skipped under `runTest`'s virtual time).
 * - **Idempotency:** results are cached per [IdempotencyKey], so replaying an authorization with the
 *   same key returns the identical response and never triggers a second charge ([chargeCount]).
 *
 * PCI note: only tokens/last4 reach this class via [PaymentRequest]; no PAN is ever handled here.
 */
class FakePsp(
    override var scenario: PaymentScenario = PaymentScenario.APPROVED,
    private val latency: Duration = 0.milliseconds,
    private val validOtp: String = "123456",
) : Psp, PaymentSimulator {

    private val ledger = mutableMapOf<IdempotencyKey, PspResponse>()

    /** Number of real authorizations performed (idempotent replays do not increment it). */
    var chargeCount: Int = 0
        private set

    override suspend fun authorize(request: PaymentRequest): PspResponse {
        delay(latency)

        // A transport failure never produces a business decision and is not cached.
        transientKind(scenario)?.let { throw PspException(it, "Simulated $it failure") }

        // Idempotent replay: same key -> same response, no new charge.
        ledger[request.idempotencyKey]?.let { return it }

        chargeCount++
        val response = when (scenario) {
            PaymentScenario.APPROVED -> PspResponse.Approved(
                pspPaymentId = "pay_${Uuid.random()}",
                authCode = "AUTH${Uuid.random().toString().take(6).uppercase()}",
            )
            PaymentScenario.NEEDS_SCA -> PspResponse.ScaRequired(
                challengeId = "ch_${Uuid.random()}",
                deliveryHint = "•••• 90",
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
        ledger[request.idempotencyKey] = response
        return response
    }

    private fun transientKind(scenario: PaymentScenario): PspException.Kind? = when (scenario) {
        PaymentScenario.NETWORK_ERROR -> PspException.Kind.NETWORK
        PaymentScenario.TIMEOUT -> PspException.Kind.TIMEOUT
        PaymentScenario.RATE_LIMITED -> PspException.Kind.RATE_LIMITED
        else -> null
    }

    override suspend fun completeSca(request: PaymentRequest, otp: String): PspResponse {
        delay(latency)

        val pending = ledger[request.idempotencyKey]
        if (pending !is PspResponse.ScaRequired) {
            return PspResponse.ScaFailed("no_pending_challenge")
        }
        if (otp != validOtp) {
            // Keep the challenge pending so the user can retry the OTP.
            return PspResponse.ScaFailed("wrong_otp")
        }

        val approved = PspResponse.Approved(
            pspPaymentId = "pay_${Uuid.random()}",
            authCode = "AUTH${Uuid.random().toString().take(6).uppercase()}",
        )
        // Settle the ledger so a replayed authorize/completeSca is idempotent.
        ledger[request.idempotencyKey] = approved
        return approved
    }
}
