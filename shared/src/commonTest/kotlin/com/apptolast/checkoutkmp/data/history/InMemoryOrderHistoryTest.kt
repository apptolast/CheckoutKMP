package com.apptolast.checkoutkmp.data.history

import com.apptolast.checkoutkmp.support.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryOrderHistoryTest {

    private val history = InMemoryOrderHistory()

    @Test
    fun starts_empty() {
        assertEquals(emptyList(), history.orders.value)
    }

    @Test
    fun records_newest_first() {
        val first = Fixtures.receipt.copy(paymentId = "pay_1")
        val second = Fixtures.receipt.copy(paymentId = "pay_2")

        history.record(first)
        history.record(second)

        assertEquals(listOf("pay_2", "pay_1"), history.orders.value.map { it.paymentId })
    }

    @Test
    fun recording_the_same_payment_updates_the_existing_order() {
        history.record(Fixtures.receipt) // authorized
        history.record(Fixtures.capturedReceipt) // same paymentId, now captured

        val orders = history.orders.value
        assertEquals(1, orders.size, "lifecycle steps must update the order, not duplicate it")
        assertEquals(Fixtures.capturedReceipt, orders.single())
    }

    @Test
    fun an_updated_order_moves_to_the_top() {
        val other = Fixtures.receipt.copy(paymentId = "pay_other")
        history.record(Fixtures.receipt)
        history.record(other)

        history.record(Fixtures.capturedReceipt) // update the first payment

        assertEquals(
            listOf(Fixtures.capturedReceipt.paymentId, "pay_other"),
            history.orders.value.map { it.paymentId },
        )
    }
}
