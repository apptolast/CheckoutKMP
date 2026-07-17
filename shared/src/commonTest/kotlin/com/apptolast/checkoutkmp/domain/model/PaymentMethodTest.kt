package com.apptolast.checkoutkmp.domain.model

import com.apptolast.checkoutkmp.support.Fixtures
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Charge timing is a **property of the method**, not scattered `if`s: cards authorize at checkout
 * and capture at dispatch; wallet-style methods charge in one step.
 */
class PaymentMethodTest {

    @Test
    fun card_defers_capture() {
        assertFalse(Fixtures.method.capturesImmediately)
    }

    @Test
    fun wallets_capture_immediately() {
        PaymentMethod.Wallet.Provider.entries.forEach { provider ->
            assertTrue(
                PaymentMethod.Wallet(provider).capturesImmediately,
                "${provider.displayName} should charge at authorization time",
            )
        }
    }
}
