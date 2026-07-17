package com.apptolast.checkoutkmp.data.repository

import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.model.RedirectReturn
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Exponential-backoff retry policy. [maxRetries] is the number of *extra* attempts after the first. */
data class RetryConfig(
    val maxRetries: Int = 3,
    val initialDelay: Duration = 200.milliseconds,
    val multiplier: Double = 2.0,
    val maxDelay: Duration = 2.seconds,
)

/**
 * A [PaymentRepository] decorator that transparently retries **only transient failures**
 * ([com.apptolast.checkoutkmp.domain.model.PaymentError.isTransient] — Network / Timeout /
 * RateLimited) with exponential backoff. Business outcomes (Declined, InvalidCard, ScaFailed) and
 * successes are returned immediately, never retried.
 *
 * Retries reuse the **same [PaymentRequest]** (hence the same IdempotencyKey), so re-attempting a
 * transient failure can never double-charge. This is option (a): the ViewModel stays oblivious —
 * retries happen under the hood in the data layer.
 */
class RetryingPaymentRepository(
    private val delegate: PaymentRepository,
    private val config: RetryConfig = RetryConfig(),
    private val onDelay: suspend (Duration) -> Unit = { delay(it) },
) : PaymentRepository {

    override suspend fun authorize(request: PaymentRequest): PaymentResult =
        retryTransient { delegate.authorize(request) }

    override suspend fun completeSca(request: PaymentRequest, otp: String): PaymentResult =
        retryTransient { delegate.completeSca(request, otp) }

    override suspend fun resendSca(request: PaymentRequest): PaymentResult =
        retryTransient { delegate.resendSca(request) }

    override suspend fun completeRedirect(request: PaymentRequest, returned: RedirectReturn): PaymentResult =
        retryTransient { delegate.completeRedirect(request, returned) }

    override suspend fun capture(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentResult =
        retryTransient { delegate.capture(receipt, idempotencyKey) }

    override suspend fun void(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentResult =
        retryTransient { delegate.void(receipt, idempotencyKey) }

    override suspend fun refund(receipt: Receipt, idempotencyKey: IdempotencyKey): PaymentResult =
        retryTransient { delegate.refund(receipt, idempotencyKey) }

    private suspend fun retryTransient(block: suspend () -> PaymentResult): PaymentResult {
        var attempt = 0
        var wait = config.initialDelay
        while (true) {
            val result = block()
            val transient = result is PaymentResult.Failed && result.error.isTransient
            if (!transient || attempt >= config.maxRetries) return result

            onDelay(wait)
            attempt++
            wait = (wait * config.multiplier).coerceAtMost(config.maxDelay)
        }
    }
}
