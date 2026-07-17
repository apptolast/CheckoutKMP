package com.apptolast.checkoutkmp.data.history

import com.apptolast.checkoutkmp.domain.history.OrderHistory
import com.apptolast.checkoutkmp.domain.model.Receipt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Session-scoped, in-memory [OrderHistory]: a single Koin instance keeps the orders alive across
 * screens for as long as the app runs. Records upsert by payment id (the freshly updated order
 * moves to the top) — no persistence on purpose, this is a demo seam like the fake PSP.
 *
 * [initialOrders] seeds the history so the screen is not empty on first open (see
 * [com.apptolast.checkoutkmp.domain.simulation.DemoDefaults.demoOrders]); it must already be in the
 * most-recently-updated-first order the UI expects. Tests use the empty default.
 */
class InMemoryOrderHistory(
    initialOrders: List<Receipt> = emptyList(),
) : OrderHistory {

    private val _orders = MutableStateFlow(initialOrders)
    override val orders: StateFlow<List<Receipt>> = _orders.asStateFlow()

    override fun record(receipt: Receipt) {
        _orders.update { current ->
            listOf(receipt) + current.filterNot { it.paymentId == receipt.paymentId }
        }
    }
}
