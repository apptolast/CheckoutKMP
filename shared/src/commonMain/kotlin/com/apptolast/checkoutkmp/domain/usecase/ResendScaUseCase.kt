package com.apptolast.checkoutkmp.domain.usecase

import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.model.toPaymentState
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository

/**
 * Asks the PSP to send the OTP of the pending 3D Secure challenge for [PaymentRequest] again,
 * reusing the original [com.apptolast.checkoutkmp.domain.model.IdempotencyKey]. The challenge is
 * not consumed or replaced — only the code delivery is repeated.
 *
 * Returns [PaymentState.RequiresSca] carrying the (still pending) challenge, or
 * [PaymentState.Failed] when there is no pending challenge / the call fails.
 */
class ResendScaUseCase(
    private val repository: PaymentRepository,
) {
    suspend operator fun invoke(request: PaymentRequest): PaymentState =
        repository.resendSca(request).toPaymentState()
}
