package com.apptolast.checkoutkmp.data.repository

import com.apptolast.checkoutkmp.data.psp.Psp
import com.apptolast.checkoutkmp.data.psp.PspErrorMapper
import com.apptolast.checkoutkmp.data.psp.PspException
import com.apptolast.checkoutkmp.data.psp.PspResponse
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.model.ScaChallenge
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock

/**
 * [PaymentRepository] backed by a [Psp]. This is the **boundary**: raw PSP responses and
 * transport exceptions are translated here into the closed domain [PaymentResult]/
 * [com.apptolast.checkoutkmp.domain.model.PaymentError] taxonomy, so nothing PSP-specific leaks
 * inward. Receipts are assembled from the request's own (PCI-safe) token data.
 */
class PaymentRepositoryImpl(
    private val psp: Psp,
    private val clock: Clock = Clock.System,
) : PaymentRepository {

    override suspend fun authorize(request: PaymentRequest): PaymentResult =
        runCatchingPsp { psp.authorize(request).toResult(request) }

    override suspend fun completeSca(request: PaymentRequest, otp: String): PaymentResult =
        runCatchingPsp { psp.completeSca(request, otp).toResult(request) }

    private inline fun runCatchingPsp(block: () -> PaymentResult): PaymentResult =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: PspException) {
            PaymentResult.Failed(PspErrorMapper.map(e))
        }

    private fun PspResponse.toResult(request: PaymentRequest): PaymentResult = when (this) {
        is PspResponse.Approved -> PaymentResult.Authorized(buildReceipt(request))
        is PspResponse.ScaRequired -> PaymentResult.RequiresSca(
            ScaChallenge(challengeId = challengeId, deliveryHint = deliveryHint, otpLength = otpLength),
        )
        is PspResponse.Declined -> PaymentResult.Failed(PspErrorMapper.mapDeclined(this))
        is PspResponse.ScaFailed -> PaymentResult.Failed(PspErrorMapper.mapScaFailed(this))
    }

    private fun PspResponse.Approved.buildReceipt(request: PaymentRequest): Receipt {
        val card = (request.method as PaymentMethod.Card).token
        return Receipt(
            paymentId = pspPaymentId,
            amount = request.amount,
            brand = card.brand,
            maskedCard = card.masked,
            authorizedAt = clock.now(),
            authCode = authCode,
        )
    }
}
