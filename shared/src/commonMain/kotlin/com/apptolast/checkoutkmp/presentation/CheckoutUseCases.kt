package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.domain.usecase.ApplyGiftCardUseCase
import com.apptolast.checkoutkmp.domain.usecase.CapturePaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.CompleteRedirectUseCase
import com.apptolast.checkoutkmp.domain.usecase.CompleteScaUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessSplitPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.RefundPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.ReverseGiftCardRedemptionUseCase
import com.apptolast.checkoutkmp.domain.usecase.VoidAuthorizationUseCase

/**
 * The domain use cases the [CheckoutViewModel] drives, grouped so the ViewModel constructor does
 * not grow a parameter per checkout capability.
 */
data class CheckoutUseCases(
    val processPayment: ProcessPaymentUseCase,
    val completeSca: CompleteScaUseCase,
    val completeRedirect: CompleteRedirectUseCase,
    val capturePayment: CapturePaymentUseCase,
    val voidAuthorization: VoidAuthorizationUseCase,
    val refundPayment: RefundPaymentUseCase,
    val processSplitPayment: ProcessSplitPaymentUseCase,
    val applyGiftCard: ApplyGiftCardUseCase,
    val reverseGiftCard: ReverseGiftCardRedemptionUseCase,
)
