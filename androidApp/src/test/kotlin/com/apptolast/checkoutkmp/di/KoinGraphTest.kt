package com.apptolast.checkoutkmp.di

import com.apptolast.checkoutkmp.data.di.dataModule
import com.apptolast.checkoutkmp.data.psp.PspScenarioController
import com.apptolast.checkoutkmp.data.tokenizer.CardTokenizer
import com.apptolast.checkoutkmp.domain.di.domainModule
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository
import com.apptolast.checkoutkmp.domain.usecase.CompleteScaUseCase
import com.apptolast.checkoutkmp.domain.usecase.ProcessPaymentUseCase
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
        assertNotNull(koin.get<PspScenarioController>())
    }
}
