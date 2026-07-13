package com.apptolast.checkoutkmp.data.di

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.data.psp.Psp
import com.apptolast.checkoutkmp.data.psp.PspScenario
import com.apptolast.checkoutkmp.data.psp.PspScenarioController
import com.apptolast.checkoutkmp.data.repository.PaymentRepositoryImpl
import com.apptolast.checkoutkmp.data.tokenizer.CardTokenizer
import com.apptolast.checkoutkmp.data.tokenizer.FakeCardTokenizer
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository
import org.koin.dsl.binds
import org.koin.dsl.module
import kotlin.time.Duration.Companion.milliseconds

/**
 * Koin bindings for the data layer: a fake PSP (with realistic latency), the PCI-safe tokenizer and
 * the [PaymentRepository] implementation the domain use cases depend on.
 */
val dataModule = module {
    // One FakePsp instance, exposed both as the gateway and as the runtime scenario switch.
    single { FakePsp(scenario = PspScenario.APPROVED, latency = 800.milliseconds) } binds
        arrayOf(Psp::class, PspScenarioController::class)
    single<CardTokenizer> { FakeCardTokenizer() }
    single<PaymentRepository> { PaymentRepositoryImpl(psp = get()) }
}
