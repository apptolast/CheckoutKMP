package com.apptolast.checkoutkmp.domain.di

import com.apptolast.checkoutkmp.domain.usecase.CapturePaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.CompleteScaUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.RefundPaymentUseCase
import org.koin.dsl.module

/**
 * Koin bindings for the domain layer. The use cases depend on a
 * [com.apptolast.checkoutkmp.domain.repository.PaymentRepository], which the data module
 * (phase 3) provides — Koin resolves it at runtime.
 */
val domainModule = module {
    factory { ProcessPaymentUseCase(get()) }
    factory { CompleteScaUseCase(get()) }
    factory { CapturePaymentUseCase(get()) }
    factory { RefundPaymentUseCase(get()) }
}
