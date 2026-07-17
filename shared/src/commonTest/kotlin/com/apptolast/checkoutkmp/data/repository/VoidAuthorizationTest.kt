package com.apptolast.checkoutkmp.data.repository

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.support.Fixtures
import com.apptolast.checkoutkmp.support.MutableClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Voiding an uncaptured authorization: the hold is released and the customer is never charged.
 * A hold is also not forever — past the PSP's validity window it lapses on its own, and capturing
 * it is declined.
 */
class VoidAuthorizationTest {

    private val clock = MutableClock(Instant.parse("2026-07-14T00:00:00Z"))
    private val psp = FakePsp(scenario = PaymentScenario.APPROVED, clock = clock)
    private val repo = PaymentRepositoryImpl(psp = psp, clock = clock)

    private suspend fun authorizedReceipt(): Receipt =
        assertIs<PaymentResult.Authorized>(repo.authorize(Fixtures.request())).receipt

    @Test
    fun voiding_releases_the_hold_without_charging() = runTest {
        val authorized = authorizedReceipt()

        val result = repo.void(authorized, IdempotencyKey.random())

        val receipt = assertIs<PaymentResult.Voided>(result).receipt
        assertNotNull(receipt.voidedAt, "void must stamp the release time")
        assertEquals(null, receipt.capturedAt)
        assertEquals(1, psp.voidCount)
        assertEquals(0, psp.captureCount, "nothing may ever be charged on a voided hold")
    }

    @Test
    fun voiding_twice_with_the_same_key_releases_only_once() = runTest {
        val authorized = authorizedReceipt()
        val key = IdempotencyKey.random()

        assertIs<PaymentResult.Voided>(repo.void(authorized, key))
        assertIs<PaymentResult.Voided>(repo.void(authorized, key)) // idempotent replay

        assertEquals(1, psp.voidCount)
    }

    @Test
    fun voiding_again_with_a_new_key_is_declined() = runTest {
        val authorized = authorizedReceipt()
        assertIs<PaymentResult.Voided>(repo.void(authorized, IdempotencyKey.random()))

        val second = repo.void(authorized, IdempotencyKey.random())

        assertIs<PaymentError.Declined>(assertIs<PaymentResult.Failed>(second).error)
        assertEquals(1, psp.voidCount)
    }

    @Test
    fun a_voided_hold_cannot_be_captured() = runTest {
        val authorized = authorizedReceipt()
        assertIs<PaymentResult.Voided>(repo.void(authorized, IdempotencyKey.random()))

        val capture = repo.capture(authorized, IdempotencyKey.random())

        assertIs<PaymentError.Declined>(assertIs<PaymentResult.Failed>(capture).error)
        assertEquals(0, psp.captureCount)
    }

    @Test
    fun a_captured_payment_cannot_be_voided() = runTest {
        val authorized = authorizedReceipt()
        assertIs<PaymentResult.Captured>(repo.capture(authorized, IdempotencyKey.random()))

        val result = repo.void(authorized, IdempotencyKey.random())

        // The charge happened: money goes back through a refund, never a void.
        assertIs<PaymentError.Declined>(assertIs<PaymentResult.Failed>(result).error)
        assertEquals(0, psp.voidCount)
    }

    @Test
    fun a_voided_hold_cannot_be_refunded() = runTest {
        val authorized = authorizedReceipt()
        assertIs<PaymentResult.Voided>(repo.void(authorized, IdempotencyKey.random()))

        val refund = repo.refund(authorized, IdempotencyKey.random())

        assertIs<PaymentError.Declined>(assertIs<PaymentResult.Failed>(refund).error)
        assertEquals(0, psp.refundCount)
    }

    @Test
    fun capturing_an_expired_hold_is_declined_and_never_charges() = runTest {
        val authorized = authorizedReceipt()

        clock.advanceBy(FakePsp.DEFAULT_AUTHORIZATION_VALIDITY + 1.hours)
        val result = repo.capture(authorized, IdempotencyKey.random())

        assertIs<PaymentError.Declined>(assertIs<PaymentResult.Failed>(result).error)
        assertEquals(0, psp.captureCount, "a lapsed hold has nothing left to charge")
    }

    @Test
    fun capturing_within_the_validity_window_still_works() = runTest {
        val authorized = authorizedReceipt()

        clock.advanceBy(FakePsp.DEFAULT_AUTHORIZATION_VALIDITY - 1.days)
        val result = repo.capture(authorized, IdempotencyKey.random())

        assertIs<PaymentResult.Captured>(result)
        assertEquals(1, psp.captureCount)
    }
}
