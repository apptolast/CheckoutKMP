package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.giftcard.FakeGiftCardStore
import com.apptolast.checkoutkmp.data.history.InMemoryOrderHistory
import com.apptolast.checkoutkmp.domain.giftcard.GiftCardService
import com.apptolast.checkoutkmp.domain.history.OrderHistory
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository
import com.apptolast.checkoutkmp.domain.usecase.ApplyGiftCardUseCase
import com.apptolast.checkoutkmp.domain.usecase.CapturePaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.CompleteRedirectUseCase
import com.apptolast.checkoutkmp.domain.usecase.CompleteScaUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessSplitPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.RecordOrderUseCase
import com.apptolast.checkoutkmp.domain.usecase.RefundPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.ResendScaUseCase
import com.apptolast.checkoutkmp.domain.usecase.ReverseGiftCardRedemptionUseCase
import com.apptolast.checkoutkmp.domain.usecase.VoidAuthorizationUseCase

/** Builds the full [CheckoutUseCases] graph for ViewModel tests, mirroring the production wiring. */
fun checkoutUseCases(
    repo: PaymentRepository,
    giftCards: GiftCardService = FakeGiftCardStore(),
    history: OrderHistory = InMemoryOrderHistory(),
): CheckoutUseCases = CheckoutUseCases(
    processPayment = ProcessPaymentUseCase(repo),
    completeSca = CompleteScaUseCase(repo),
    resendSca = ResendScaUseCase(repo),
    completeRedirect = CompleteRedirectUseCase(repo),
    capturePayment = CapturePaymentUseCase(repo),
    voidAuthorization = VoidAuthorizationUseCase(repo),
    refundPayment = RefundPaymentUseCase(repo),
    processSplitPayment = ProcessSplitPaymentUseCase(giftCards, repo),
    applyGiftCard = ApplyGiftCardUseCase(giftCards),
    reverseGiftCard = ReverseGiftCardRedemptionUseCase(giftCards),
    recordOrder = RecordOrderUseCase(history),
)
