package com.apptolast.checkoutkmp.domain.usecase

import com.apptolast.checkoutkmp.domain.history.OrderHistory
import com.apptolast.checkoutkmp.domain.model.Receipt

/**
 * Records a settled payment in the session's order history. Idempotent by nature: the history
 * upserts by payment id, so recording every lifecycle step of the same payment keeps one order
 * that reflects its latest settlement state.
 */
class RecordOrderUseCase(
    private val history: OrderHistory,
) {
    operator fun invoke(receipt: Receipt) = history.record(receipt)
}
