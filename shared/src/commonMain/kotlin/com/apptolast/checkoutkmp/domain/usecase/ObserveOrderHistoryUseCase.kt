package com.apptolast.checkoutkmp.domain.usecase

import com.apptolast.checkoutkmp.domain.history.OrderHistory
import com.apptolast.checkoutkmp.domain.model.Receipt
import kotlinx.coroutines.flow.StateFlow

/** Exposes the session's order history (most recently updated first) for the UI to observe. */
class ObserveOrderHistoryUseCase(
    private val history: OrderHistory,
) {
    operator fun invoke(): StateFlow<List<Receipt>> = history.orders
}
