package com.apptolast.checkoutkmp.domain.usecase

import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.model.toPaymentState
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Authorizes a payment and emits the resulting state transitions.
 *
 * Emits [PaymentState.Processing] immediately, then the settlement/intermediate outcome:
 * [PaymentState.Authorized] (funds held), [PaymentState.Captured] (immediate-capture methods),
 * [PaymentState.RequiresSca] or [PaymentState.Failed]. The repository has already classified any
 * error, so this use case stays a thin, pure orchestration.
 */
class ProcessPaymentUseCase(
    private val repository: PaymentRepository,
) {
    operator fun invoke(request: PaymentRequest): Flow<PaymentState> = flow {
        emit(PaymentState.Processing)
        emit(repository.authorize(request).toPaymentState())
    }
}
