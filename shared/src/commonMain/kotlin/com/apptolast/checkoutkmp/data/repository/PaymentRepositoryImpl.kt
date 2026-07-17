package com.apptolast.checkoutkmp.data.repository

import com.apptolast.checkoutkmp.data.psp.Psp
import com.apptolast.checkoutkmp.data.psp.PspErrorMapper
import com.apptolast.checkoutkmp.data.psp.PspException
import com.apptolast.checkoutkmp.data.psp.PspResponse
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.model.RedirectChallenge
import com.apptolast.checkoutkmp.domain.model.RedirectReturn
import com.apptolast.checkoutkmp.domain.model.ScaChallenge
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * [PaymentRepository] backed by a [Psp]. This is the **boundary**: raw PSP responses and
 * transport exceptions are translated here into the closed domain [PaymentResult]/
 * [com.apptolast.checkoutkmp.domain.model.PaymentError] taxonomy, so nothing PSP-specific leaks
 * inward. Receipts are assembled from the request's own (PCI-safe) method data and stamped with
 * this repository's [clock] as the payment moves through authorize → capture → refund.
 */
class PaymentRepositoryImpl(
    private val psp: Psp,
    private val clock: Clock = Clock.System,
) : PaymentRepository {

    override suspend fun authorize(request: PaymentRequest): PaymentResult =
        runCatchingPsp { psp.authorize(request).toResult(request) }

    override suspend fun completeSca(request: PaymentRequest, otp: String): PaymentResult =
        runCatchingPsp { psp.completeSca(request, otp).toResult(request) }

    override suspend fun resendSca(request: PaymentRequest): PaymentResult =
        runCatchingPsp { psp.resendSca(request).toResult(request) }

    override suspend fun completeRedirect(request: PaymentRequest, returned: RedirectReturn): PaymentResult =
        runCatchingPsp { psp.completeRedirect(request, returned).toResult(request) }

    override suspend fun capture(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentResult =
        runCatchingPsp {
            when (val response = psp.capture(receipt.paymentId, idempotencyKey)) {
                is PspResponse.Captured -> PaymentResult.Captured(receipt.copy(capturedAt = clock.now()))
                else -> response.toFailure()
            }
        }

    override suspend fun void(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentResult =
        runCatchingPsp {
            when (val response = psp.void(receipt.paymentId, idempotencyKey)) {
                is PspResponse.Voided -> PaymentResult.Voided(receipt.copy(voidedAt = clock.now()))
                else -> response.toFailure()
            }
        }

    override suspend fun refund(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentResult =
        runCatchingPsp {
            when (val response = psp.refund(receipt.paymentId, idempotencyKey)) {
                is PspResponse.Refunded -> PaymentResult.Refunded(receipt.copy(refundedAt = clock.now()))
                else -> response.toFailure()
            }
        }

    private inline fun runCatchingPsp(block: () -> PaymentResult): PaymentResult =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: PspException) {
            PaymentResult.Failed(PspErrorMapper.map(e))
        }

    private fun PspResponse.toResult(request: PaymentRequest): PaymentResult = when (this) {
        is PspResponse.Authorized ->
            PaymentResult.Authorized(buildReceipt(request, pspPaymentId, authCode))
        // An immediate-capture method settles inside the authorization itself.
        is PspResponse.Captured ->
            PaymentResult.Captured(buildReceipt(request, pspPaymentId, authCode, capturedAt = clock.now()))
        is PspResponse.ScaRequired -> PaymentResult.RequiresSca(
            ScaChallenge(challengeId = challengeId, deliveryHint = deliveryHint, otpLength = otpLength),
        )
        is PspResponse.RedirectRequired -> PaymentResult.RequiresRedirect(
            // The provider identity travels with the challenge: the UI derives its heading from the
            // domain instead of re-deriving it from the selected method.
            RedirectChallenge(
                redirectId = redirectId,
                provider = request.method.label,
                url = url,
                returnUrl = returnUrl,
            ),
        )
        is PspResponse.Refunded -> toFailure()
        is PspResponse.Voided -> toFailure()
        is PspResponse.Declined -> toFailure()
        is PspResponse.ScaFailed -> toFailure()
    }

    private fun PspResponse.toFailure(): PaymentResult.Failed = when (this) {
        is PspResponse.Declined -> PaymentResult.Failed(PspErrorMapper.mapDeclined(this))
        is PspResponse.ScaFailed -> PaymentResult.Failed(PspErrorMapper.mapScaFailed(this))
        // A success response arriving through the wrong operation is a contract violation.
        else -> PaymentResult.Failed(PaymentError.Unknown("unexpected PSP response"))
    }

    private fun buildReceipt(
        request: PaymentRequest,
        pspPaymentId: String,
        authCode: String,
        capturedAt: Instant? = null,
    ): Receipt {
        val now = clock.now()
        return Receipt(
            paymentId = pspPaymentId,
            amount = request.amount,
            method = request.method,
            createdAt = now,
            authorizedAt = now,
            authCode = authCode,
            capturedAt = capturedAt,
        )
    }
}
