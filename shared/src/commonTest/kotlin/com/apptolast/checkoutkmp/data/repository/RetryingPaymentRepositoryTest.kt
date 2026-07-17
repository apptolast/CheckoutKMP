package com.apptolast.checkoutkmp.data.repository

import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.support.Fixtures
import com.apptolast.checkoutkmp.support.FakePaymentRepository
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RetryingPaymentRepositoryTest {

    private val config = RetryConfig(
        maxRetries = 3,
        initialDelay = 100.milliseconds,
        multiplier = 2.0,
        maxDelay = 1000.milliseconds,
    )

    private fun retrying(
        delegate: FakePaymentRepository,
        recordedDelays: MutableList<Duration> = mutableListOf(),
    ) = RetryingPaymentRepository(delegate, config, onDelay = { recordedDelays.add(it) })

    @Test
    fun retries_a_transient_failure_then_succeeds() = runTest {
        var calls = 0
        val delegate = FakePaymentRepository(onAuthorize = {
            calls++
            if (calls < 3) PaymentResult.Failed(PaymentError.Network)
            else PaymentResult.Authorized(Fixtures.receipt)
        })

        val result = retrying(delegate).authorize(Fixtures.request())

        assertIs<PaymentResult.Authorized>(result)
        assertEquals(3, delegate.authorizeCalls.size) // 2 failures + 1 success
    }

    @Test
    fun exhausts_retries_and_returns_the_last_transient_failure() = runTest {
        val delegate = FakePaymentRepository(onAuthorize = { PaymentResult.Failed(PaymentError.Timeout) })

        val result = retrying(delegate).authorize(Fixtures.request())

        assertEquals(PaymentResult.Failed(PaymentError.Timeout), result)
        assertEquals(4, delegate.authorizeCalls.size) // 1 initial + maxRetries(3)
    }

    @Test
    fun does_not_retry_a_decline() = runTest {
        val error = PaymentError.Declined("insufficient_funds")
        val delegate = FakePaymentRepository(onAuthorize = { PaymentResult.Failed(error) })

        val result = retrying(delegate).authorize(Fixtures.request())

        assertEquals(PaymentResult.Failed(error), result)
        assertEquals(1, delegate.authorizeCalls.size)
    }

    @Test
    fun does_not_retry_an_invalid_card() = runTest {
        val delegate = FakePaymentRepository(onAuthorize = { PaymentResult.Failed(PaymentError.InvalidCard("bad")) })

        retrying(delegate).authorize(Fixtures.request())

        assertEquals(1, delegate.authorizeCalls.size)
    }

    @Test
    fun sca_failure_is_not_retried() = runTest {
        val delegate = FakePaymentRepository(
            onCompleteSca = { _, _ -> PaymentResult.Failed(PaymentError.ScaFailed("wrong_otp")) },
        )

        retrying(delegate).completeSca(Fixtures.request(), otp = "000000")

        assertEquals(1, delegate.completeScaCalls.size)
    }

    @Test
    fun every_retry_reuses_the_same_idempotency_key() = runTest {
        val key = IdempotencyKey.random()
        val delegate = FakePaymentRepository(onAuthorize = { PaymentResult.Failed(PaymentError.Network) })

        retrying(delegate).authorize(Fixtures.request(key))

        assertEquals(4, delegate.authorizeCalls.size)
        assertEquals(setOf(key), delegate.authorizeCalls.map { it.idempotencyKey }.toSet())
    }

    @Test
    fun retries_a_transient_capture_failure_with_the_same_key() = runTest {
        var calls = 0
        val delegate = FakePaymentRepository(onCapture = { receipt, _ ->
            calls++
            if (calls < 2) PaymentResult.Failed(PaymentError.Network)
            else PaymentResult.Captured(receipt.copy(capturedAt = Fixtures.capturedReceipt.capturedAt))
        })
        val key = IdempotencyKey.random()

        val result = retrying(delegate).capture(Fixtures.receipt, key)

        assertIs<PaymentResult.Captured>(result)
        assertEquals(2, delegate.captureCalls.size)
        assertEquals(setOf(key), delegate.captureCalls.map { it.second }.toSet())
    }

    @Test
    fun retries_a_transient_refund_failure_with_the_same_key() = runTest {
        val delegate = FakePaymentRepository(onRefund = { _, _ -> PaymentResult.Failed(PaymentError.Timeout) })
        val key = IdempotencyKey.random()

        retrying(delegate).refund(Fixtures.capturedReceipt, key)

        assertEquals(4, delegate.refundCalls.size) // 1 initial + maxRetries(3)
        assertEquals(setOf(key), delegate.refundCalls.map { it.second }.toSet())
    }

    @Test
    fun does_not_retry_a_declined_capture() = runTest {
        val delegate = FakePaymentRepository(
            onCapture = { _, _ -> PaymentResult.Failed(PaymentError.Declined("already_captured")) },
        )

        retrying(delegate).capture(Fixtures.receipt, IdempotencyKey.random())

        assertEquals(1, delegate.captureCalls.size)
    }

    @Test
    fun backs_off_exponentially_between_retries() = runTest {
        val delays = mutableListOf<Duration>()
        val delegate = FakePaymentRepository(onAuthorize = { PaymentResult.Failed(PaymentError.Network) })

        retrying(delegate, delays).authorize(Fixtures.request())

        // 3 retries -> 3 waits: 100ms, 200ms, 400ms
        assertEquals(listOf(100.milliseconds, 200.milliseconds, 400.milliseconds), delays)
    }
}
