package com.apptolast.checkoutkmp.domain.usecase

import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.model.RedirectReturn
import com.apptolast.checkoutkmp.domain.model.toPaymentState
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Processes the user's return from a redirect method (PayPal, Bizum), reusing the original
 * [com.apptolast.checkoutkmp.domain.model.IdempotencyKey].
 *
 * **Where the truth lives:** the return deep link is a *claim* — the outcome that counts is what
 * the provider confirmed to the PSP asynchronously (webhook). The repository reconciles the claim
 * against the PSP's record, so an "approved" return whose webhook was rejected still fails, and a
 * wallet payment settles straight to [PaymentState.Captured] (immediate capture, never Authorized).
 */
class CompleteRedirectUseCase(
    private val repository: PaymentRepository,
) {
    operator fun invoke(request: PaymentRequest, returned: RedirectReturn): Flow<PaymentState> = flow {
        emit(PaymentState.Processing)
        emit(repository.completeRedirect(request, returned).toPaymentState())
    }
}
