package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.giftcard.FakeGiftCardStore
import com.apptolast.checkoutkmp.domain.giftcard.GiftCardService
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository
import com.apptolast.checkoutkmp.domain.usecase.ApplyGiftCardUseCase
import com.apptolast.checkoutkmp.domain.usecase.CapturePaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.CompleteScaUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessSplitPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.RefundPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.ReverseGiftCardRedemptionUseCase

/** Builds the full [CheckoutUseCases] graph for ViewModel tests, mirroring the production wiring. */
fun checkoutUseCases(
    repo: PaymentRepository,
    giftCards: GiftCardService = FakeGiftCardStore(),
): CheckoutUseCases = CheckoutUseCases(
    processPayment = ProcessPaymentUseCase(repo),
    completeSca = CompleteScaUseCase(repo),
    capturePayment = CapturePaymentUseCase(repo),
    refundPayment = RefundPaymentUseCase(repo),
    processSplitPayment = ProcessSplitPaymentUseCase(giftCards, repo),
    applyGiftCard = ApplyGiftCardUseCase(giftCards),
    reverseGiftCard = ReverseGiftCardRedemptionUseCase(giftCards),
)
