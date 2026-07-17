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

    @Test
    fun gift_cards_capture_immediately() {
        assertTrue(PaymentMethod.GiftCard("GIFT25").capturesImmediately)
    }

    @Test
    fun only_wallets_require_a_redirect() {
        assertFalse(Fixtures.method.requiresRedirect)
        assertFalse(PaymentMethod.GiftCard("GIFT25").requiresRedirect)
        PaymentMethod.Wallet.Provider.entries.forEach { provider ->
            assertTrue(
                PaymentMethod.Wallet(provider).requiresRedirect,
                "${provider.displayName} should approve on the provider's page",
            )
        }
    }
}
