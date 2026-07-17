package com.apptolast.checkoutkmp.data.di

import com.apptolast.checkoutkmp.data.giftcard.FakeGiftCardStore
import com.apptolast.checkoutkmp.data.history.InMemoryOrderHistory
import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.data.psp.Psp
import com.apptolast.checkoutkmp.data.repository.PaymentRepositoryImpl
import com.apptolast.checkoutkmp.data.repository.RetryingPaymentRepository
import com.apptolast.checkoutkmp.data.tokenizer.FakeCardTokenizer
import com.apptolast.checkoutkmp.domain.giftcard.GiftCardService
import com.apptolast.checkoutkmp.domain.history.OrderHistory
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.domain.simulation.PaymentSimulator
import com.apptolast.checkoutkmp.domain.tokenizer.CardTokenizer
import org.koin.dsl.binds
import org.koin.dsl.module
import kotlin.time.Duration.Companion.milliseconds

/**
 * Koin bindings for the data layer: a fake PSP (with realistic latency), the PCI-safe tokenizer and
 * the [PaymentRepository] implementation the domain use cases depend on.
 */
val dataModule = module {
    // One FakePsp instance, exposed both as the gateway and as the runtime scenario switch.
    single { FakePsp(scenario = PaymentScenario.APPROVED, latency = 800.milliseconds) } binds
        arrayOf(Psp::class, PaymentSimulator::class)
    single<CardTokenizer> { FakeCardTokenizer() }
    // The gift-card backend: one instance so balances persist across the demo session.
    single<GiftCardService> { FakeGiftCardStore() }
    // One instance so the order history survives screen changes for the whole session.
    single<OrderHistory> { InMemoryOrderHistory() }
    // Transient failures are retried transparently (same IdempotencyKey) by the decorator.
    single<PaymentRepository> {
        RetryingPaymentRepository(delegate = PaymentRepositoryImpl(psp = get()))
    }
}
