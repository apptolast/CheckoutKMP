package com.apptolast.checkoutkmp.di

import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.presentation.CheckoutState
import com.apptolast.checkoutkmp.presentation.CheckoutViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/** Demo order total shown on the checkout screen. */
private val demoAmount = Amount(minorUnits = 4999, currency = Currency.EUR)

/** Koin bindings for the Android presentation layer. */
val presentationModule = module {
    viewModel {
        CheckoutViewModel(
            processPayment = get(),
            tokenizer = get(),
            initialState = CheckoutState(amount = demoAmount),
        )
    }
}
