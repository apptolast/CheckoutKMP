package com.apptolast.checkoutkmp.data.repository

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.data.psp.PspScenario
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.support.Fixtures
import com.apptolast.checkoutkmp.support.FixedClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PaymentRepositoryImplTest {

    private fun repositoryWith(scenario: PspScenario, psp: FakePsp = FakePsp(scenario = scenario)) =
        psp to PaymentRepositoryImpl(psp = psp, clock = FixedClock.default)

    @Test
    fun approved_scenario_returns_a_pci_safe_receipt() = runTest {
        val (_, repo) = repositoryWith(PspScenario.APPROVED)

        val result = repo.authorize(Fixtures.request())

        val receipt = assertIs<PaymentResult.Authorized>(result).receipt
        assertEquals(Fixtures.amount, receipt.amount)
        assertEquals("•••• 4242", receipt.maskedCard) // no PAN, only last4
        assertTrue(receipt.paymentId.startsWith("pay_"))
        assertTrue(receipt.authCode.isNotBlank())
    }

    @Test
    fun declined_scenario_maps_to_declined_error() = runTest {
        val (_, repo) = repositoryWith(PspScenario.DECLINED)

        val result = repo.authorize(Fixtures.request())

        val error = assertIs<PaymentResult.Failed>(result).error
        assertIs<PaymentError.Declined>(error)
    }

    @Test
    fun network_failure_maps_to_transient_network_error() = runTest {
        val (_, repo) = repositoryWith(PspScenario.NETWORK_ERROR)

        val result = repo.authorize(Fixtures.request())

        val error = assertIs<PaymentResult.Failed>(result).error
        assertEquals(PaymentError.Network, error)
        assertTrue(error.isTransient)
    }

    @Test
    fun needs_sca_scenario_returns_a_challenge() = runTest {
        val (_, repo) = repositoryWith(PspScenario.NEEDS_SCA)

        val result = repo.authorize(Fixtures.request())

        val challenge = assertIs<PaymentResult.RequiresSca>(result).challenge
        assertEquals(6, challenge.otpLength)
    }

    @Test
    fun authorizing_twice_with_the_same_key_charges_only_once() = runTest {
        val (psp, repo) = repositoryWith(PspScenario.APPROVED)
        val request = Fixtures.request(IdempotencyKey.random())

        val first = repo.authorize(request)
        val second = repo.authorize(request) // idempotent replay

        val firstId = assertIs<PaymentResult.Authorized>(first).receipt.paymentId
        val secondId = assertIs<PaymentResult.Authorized>(second).receipt.paymentId
        assertEquals(firstId, secondId)
        assertEquals(1, psp.chargeCount)
    }

    @Test
    fun different_keys_produce_separate_charges() = runTest {
        val (psp, repo) = repositoryWith(PspScenario.APPROVED)

        repo.authorize(Fixtures.request(IdempotencyKey.random()))
        repo.authorize(Fixtures.request(IdempotencyKey.random()))

        assertEquals(2, psp.chargeCount)
    }

    @Test
    fun sca_completes_with_the_correct_otp() = runTest {
        val (_, repo) = repositoryWith(PspScenario.NEEDS_SCA)
        val request = Fixtures.request()

        assertIs<PaymentResult.RequiresSca>(repo.authorize(request)) // arm the challenge
        val result = repo.completeSca(request, otp = "123456")

        assertIs<PaymentResult.Authorized>(result)
    }

    @Test
    fun sca_fails_with_a_wrong_otp() = runTest {
        val (_, repo) = repositoryWith(PspScenario.NEEDS_SCA)
        val request = Fixtures.request()

        assertIs<PaymentResult.RequiresSca>(repo.authorize(request))
        val result = repo.completeSca(request, otp = "000000")

        val error = assertIs<PaymentResult.Failed>(result).error
        assertIs<PaymentError.ScaFailed>(error)
    }
}
