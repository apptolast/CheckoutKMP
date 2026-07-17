package com.apptolast.checkoutkmp.support

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A [Clock] tests can move forward, for time-dependent behaviour like authorization expiry. */
class MutableClock(private var current: Instant) : Clock {
    override fun now(): Instant = current

    /** Move the clock forward by [duration]. */
    fun advanceBy(duration: Duration) {
        current += duration
    }
}
