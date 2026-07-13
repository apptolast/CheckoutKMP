package com.apptolast.checkoutkmp.data.psp

/**
 * Lets the demo UI switch the [FakePsp]'s behaviour at runtime (approved / needs SCA / declined /
 * network). Bound to the same singleton as [Psp], so changing it affects the next payment.
 */
interface PspScenarioController {
    var scenario: PspScenario
}
