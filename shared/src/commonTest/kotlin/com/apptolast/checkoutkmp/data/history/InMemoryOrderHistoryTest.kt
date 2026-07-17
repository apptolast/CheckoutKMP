package com.apptolast.checkoutkmp.data.history

import com.apptolast.checkoutkmp.domain.model.lastUpdatedAt
import com.apptolast.checkoutkmp.domain.simulation.DemoDefaults
import com.apptolast.checkoutkmp.support.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class InMemoryOrderHistoryTest {

    private val history = InMemoryOrderHistory()

    @Test
    fun starts_empty() {
        assertEquals(emptyList(), history.orders.value)
    }

    @Test
    fun seeds_from_initial_orders_preserving_order() {
        val seeded = InMemoryOrderHistory(
            initialOrders = listOf(
                Fixtures.receipt.copy(paymentId = "pay_a"),
                Fixtures.receipt.copy(paymentId = "pay_b"),
            ),
        )

        assertEquals(listOf("pay_a", "pay_b"), seeded.orders.value.map { it.paymentId })
    }

    @Test
    fun demo_seed_is_non_empty_and_newest_first() {
        val orders = DemoDefaults.demoOrders(now = Instant.fromEpochSeconds(1_700_000_000))

        assertTrue(orders.isNotEmpty(), "the history screen should not open empty in the demo")
        val timestamps = orders.map { it.lastUpdatedAt }
        assertEquals(timestamps.sortedDescending(), timestamps, "demo orders must be most-recent first")
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
