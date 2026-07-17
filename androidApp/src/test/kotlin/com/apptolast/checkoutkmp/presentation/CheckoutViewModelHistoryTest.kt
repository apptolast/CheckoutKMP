package com.apptolast.checkoutkmp.presentation

import com.apptolast.checkoutkmp.data.history.InMemoryOrderHistory
import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.data.repository.PaymentRepositoryImpl
import com.apptolast.checkoutkmp.data.tokenizer.FakeCardTokenizer
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.model.Currency
import com.apptolast.checkoutkmp.domain.model.SettlementStatus
import com.apptolast.checkoutkmp.domain.model.settlement
import com.apptolast.checkoutkmp.domain.tokenizer.RawCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Every settled payment lands in the order history, and lifecycle steps update the same order. */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelHistoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val validCard = RawCard(pan = "4242424242424242", expiry = CardExpiry(12, 2030), cvv = "123")

    private fun newViewModel(): Pair<InMemoryOrderHistory, CheckoutViewModel> {
        val psp = FakePsp()
        val history = InMemoryOrderHistory()
        val repo = PaymentRepositoryImpl(psp = psp)
        val vm = CheckoutViewModel(
            useCases = checkoutUseCases(repo, history = history),
            tokenizer = FakeCardTokenizer(),
            scenarioController = psp,
            initialState = CheckoutState(amount = Amount(4999, Currency.EUR)),
        )
        return history to vm
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun a_settled_payment_is_recorded_as_an_order() = runTest {
        val (history, vm) = newViewModel()

        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        val order = history.orders.value.single()
        assertEquals(SettlementStatus.AUTHORIZED, order.settlement)
        assertEquals(Amount(4999, Currency.EUR), order.amount)
    }

    @Test
    fun lifecycle_steps_update_the_same_order() = runTest {
        val (history, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.Capture)
        advanceUntilIdle()
        assertEquals(SettlementStatus.CAPTURED, history.orders.value.single().settlement)

        vm.onIntent(CheckoutIntent.Refund)
        advanceUntilIdle()
        assertEquals(SettlementStatus.REFUNDED, history.orders.value.single().settlement)
    }

    @Test
    fun a_voided_order_is_reflected_in_the_history() = runTest {
        val (history, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        vm.onIntent(CheckoutIntent.Void)
        advanceUntilIdle()

        assertEquals(SettlementStatus.VOIDED, history.orders.value.single().settlement)
    }

    @Test
    fun each_new_payment_adds_its_own_order_newest_first() = runTest {
        val (history, vm) = newViewModel()
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()
        val firstId = history.orders.value.single().paymentId

        vm.onIntent(CheckoutIntent.Reset)
        vm.onIntent(CheckoutIntent.Submit(validCard))
        advanceUntilIdle()

        val orders = history.orders.value
        assertEquals(2, orders.size)
        assertEquals(firstId, orders.last().paymentId, "the older order stays below the newer one")
    }
}
