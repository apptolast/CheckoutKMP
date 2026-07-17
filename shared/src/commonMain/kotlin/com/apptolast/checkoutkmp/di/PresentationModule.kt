package com.apptolast.checkoutkmp.di

import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.presentation.CheckoutState
import com.apptolast.checkoutkmp.presentation.CheckoutViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Demo order total shown on the checkout screen (€49.99). */
private val demoAmount = Amount.of(major = 49, minor = 99, currency = Currency.EUR)

/** Koin bindings for the Android presentation layer. */
val presentationModule = module {
    viewModel {
        CheckoutViewModel(
            processPayment = get(),
            completeSca = get(),
            capturePayment = get(),
            refundPayment = get(),
            tokenizer = get(),
            scenarioController = get(),
            initialState = CheckoutState(amount = demoAmount),
        )
    }
}
