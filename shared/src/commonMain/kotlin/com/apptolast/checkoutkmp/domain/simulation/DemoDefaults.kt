package com.apptolast.checkoutkmp.domain.simulation

/**
 * Fixed demo credentials shared by the simulated PSP and the UI hints, so the value the user is
 * told to type always matches what the fake gateway accepts. This is a **demo/testing seam**, not
 * production data — no real secret ever lives here.
 */
object DemoDefaults {
    /** OTP accepted by the simulated 3D Secure challenge (also shown as a hint in the demo UI). */
    const val SCA_OTP = "123456"
}
