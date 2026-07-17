package com.apptolast.checkoutkmp.data.repository

import com.apptolast.checkoutkmp.data.psp.FakePsp
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.support.FixedClock
import com.apptolast.checkoutkmp.support.Fixtures
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** The resend seam: reissuing a pending 3D Secure challenge without consuming or replacing it. */
class ScaResendTest {

    private fun repository(psp: FakePsp = FakePsp(scenario = PaymentScenario.NEEDS_SCA)) =
        psp to PaymentRepositoryImpl(psp = psp, clock = FixedClock.default)

    @Test
    fun resend_returns_the_same_pending_challenge() = runTest {
        val (_, repo) = repository()
        val request = Fixtures.request()

        val issued = assertIs<PaymentResult.RequiresSca>(repo.authorize(request)).challenge
        val reissued = assertIs<PaymentResult.RequiresSca>(repo.resendSca(request)).challenge

        assertEquals(issued, reissued) // same challenge, only the delivery is repeated
    }

    @Test
    fun resend_does_not_consume_the_challenge_and_the_original_otp_still_completes() = runTest {
        val (psp, repo) = repository()
        val request = Fixtures.request()

        repo.authorize(request)
        repo.resendSca(request)

        assertIs<PaymentResult.Authorized>(repo.completeSca(request, otp = "123456"))
        assertEquals(1, psp.chargeCount) // reissuing never creates a second charge attempt
    }

    @Test
    fun resend_without_a_pending_challenge_fails_as_sca_failure() = runTest {
        val (_, repo) = repository()

        val result = repo.resendSca(Fixtures.request())

        val error = assertIs<PaymentResult.Failed>(result).error
        assertIs<PaymentError.ScaFailed>(error)
    }
}
