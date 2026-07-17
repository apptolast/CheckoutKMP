package com.apptolast.checkoutkmp.di

import com.apptolast.checkoutkmp.data.di.dataModule
import com.apptolast.checkoutkmp.domain.giftcard.GiftCardService
import com.apptolast.checkoutkmp.domain.simulation.PaymentSimulator
import com.apptolast.checkoutkmp.domain.tokenizer.CardTokenizer
import com.apptolast.checkoutkmp.domain.di.domainModule
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository
import com.apptolast.checkoutkmp.domain.usecase.ApplyGiftCardUseCase
import com.apptolast.checkoutkmp.domain.usecase.CapturePaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.CompleteScaUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessSplitPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.RefundPaymentUseCase
import com.apptolast.checkoutkmp.domain.usecase.ReverseGiftCardRedemptionUseCase
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Verifies the shared object graph the [com.apptolast.checkoutkmp.presentation.CheckoutViewModel]
 * depends on can be resolved. These modules need no Android context, so they run on the JVM.
 */
class KoinGraphTest {

    @AfterTest
    fun tearDown() = stopKoin()

    @Test
    fun domain_and_data_graph_resolves() {
        val koin = startKoin { modules(domainModule, dataModule) }.koin

        assertNotNull(koin.get<PaymentRepository>())
        assertNotNull(koin.get<CardTokenizer>())
        assertNotNull(koin.get<ProcessPaymentUseCase>())
        assertNotNull(koin.get<CompleteScaUseCase>())
        assertNotNull(koin.get<CapturePaymentUseCase>())
        assertNotNull(koin.get<RefundPaymentUseCase>())
        assertNotNull(koin.get<GiftCardService>())
        assertNotNull(koin.get<ProcessSplitPaymentUseCase>())
        assertNotNull(koin.get<ApplyGiftCardUseCase>())
        assertNotNull(koin.get<ReverseGiftCardRedemptionUseCase>())
        assertNotNull(koin.get<PaymentSimulator>())
    }
}
