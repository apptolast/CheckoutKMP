package com.apptolast.checkoutkmp.presentation

import androidx.lifecycle.ViewModel
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.usecase.ObserveOrderHistoryUseCase
import kotlinx.coroutines.flow.StateFlow

/**
 * Exposes the session's order history to the UI. Read-only: orders are recorded by the checkout
 * flow ([CheckoutViewModel]) as payments settle; this screen just observes them.
 */
class HistoryViewModel(
    observeOrderHistory: ObserveOrderHistoryUseCase,
) : ViewModel() {

    /** All orders, most recently updated first. Receipts are PCI-safe by construction. */
    val orders: StateFlow<List<Receipt>> = observeOrderHistory()
}
