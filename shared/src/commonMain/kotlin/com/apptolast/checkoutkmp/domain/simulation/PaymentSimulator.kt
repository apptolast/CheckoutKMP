package com.apptolast.checkoutkmp.domain.simulation

/**
 * The outcome a simulated PSP should produce. This is a **demo/testing seam** — not core business
 * logic — that lets the app exercise every payment path (approval, SCA, decline, transient failures)
 * without a real gateway. [NEEDS_SCA] triggers 3D Secure; [NETWORK_ERROR]/[TIMEOUT]/[RATE_LIMITED]
 * are transient failures the retry policy may retry; [DECLINED] is a business decision that is not.
 */
enum class PaymentScenario {
    APPROVED,
    NEEDS_SCA,
    DECLINED,
    NETWORK_ERROR,
    TIMEOUT,
    RATE_LIMITED,
}

/**
 * Runtime switch for the active [PaymentScenario]. Declared in the domain so the presentation layer
 * can depend on it without reaching into the data layer; the fake PSP implements it.
 */
interface PaymentSimulator {
    var scenario: PaymentScenario
}
