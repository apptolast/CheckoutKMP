package com.apptolast.checkoutkmp.domain.usecase

import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.model.toPaymentState
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Completes a pending 3D Secure challenge for [PaymentRequest] using the user-entered OTP, reusing
 * the original [com.apptolast.checkoutkmp.domain.model.IdempotencyKey].
 *
 * Emits [PaymentState.Processing], then [PaymentState.Approved] or [PaymentState.Failed]
 * (e.g. [com.apptolast.checkoutkmp.domain.model.PaymentError.ScaFailed]).
 */
class CompleteScaUseCase(
    private val repository: PaymentRepository,
) {
    operator fun invoke(request: PaymentRequest, otp: String): Flow<PaymentState> = flow {
        emit(PaymentState.Processing)
        emit(repository.completeSca(request, otp).toPaymentState())
    }
}
