package com.apptolast.checkoutkmp.domain.di

import com.apptolast.checkoutkmp.domain.usecase.ApplyGiftCardUseCase
import com.apptolast.checkoutkmp.domain.usecase.CapturePaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.CompleteRedirectUseCase
import com.apptolast.checkoutkmp.domain.usecase.CompleteScaUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessSplitPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.RefundPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.ReverseGiftCardRedemptionUseCase
import com.apptolast.checkoutkmp.domain.usecase.VoidAuthorizationUseCase
import org.koin.dsl.module

/**
 * Koin bindings for the domain layer. The use cases depend on the domain contracts
 * ([com.apptolast.checkoutkmp.domain.repository.PaymentRepository],
 * [com.apptolast.checkoutkmp.domain.giftcard.GiftCardService]), which the data module provides —
 * Koin resolves them at runtime.
 */
val domainModule = module {
    factory { ProcessPaymentUseCase(get()) }
    factory { CompleteScaUseCase(get()) }
    factory { CompleteRedirectUseCase(get()) }
    factory { CapturePaymentUseCase(get()) }
    factory { VoidAuthorizationUseCase(get()) }
    factory { RefundPaymentUseCase(get()) }
    factory { ProcessSplitPaymentUseCase(giftCards = get(), repository = get()) }
    factory { ApplyGiftCardUseCase(get()) }
    factory { ReverseGiftCardRedemptionUseCase(get()) }
}
