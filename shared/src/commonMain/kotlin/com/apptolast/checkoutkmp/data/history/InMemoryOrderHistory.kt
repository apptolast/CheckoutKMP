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
 */
class InMemoryOrderHistory : OrderHistory {

    private val _orders = MutableStateFlow<List<Receipt>>(emptyList())
    override val orders: StateFlow<List<Receipt>> = _orders.asStateFlow()

    override fun record(receipt: Receipt) {
        _orders.update { current ->
            listOf(receipt) + current.filterNot { it.paymentId == receipt.paymentId }
        }
    }
}
