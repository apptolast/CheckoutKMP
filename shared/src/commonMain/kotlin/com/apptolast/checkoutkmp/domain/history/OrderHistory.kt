package com.apptolast.checkoutkmp.domain.history

import com.apptolast.checkoutkmp.domain.model.Receipt
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-facing contract for the session's order history: every payment that reached a settlement
 * outcome, observable by the UI. Implementations (data layer) own the storage.
 *
 * [record] **upserts by [Receipt.paymentId]**: later lifecycle steps of the same payment
 * (capture, refund, void) update the existing order instead of adding a new one, and the list is
 * ordered by most recently updated first. PCI note: receipts are PCI-safe by construction, so the
 * history never holds a PAN either.
 */
interface OrderHistory {
    /** All recorded orders, most recently updated first. */
    val orders: StateFlow<List<Receipt>>

    /** Add [receipt] or update the existing order with the same payment id. */
    fun record(receipt: Receipt)
}
