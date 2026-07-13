package com.apptolast.checkoutkmp.support

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** A [Clock] frozen at [instant], for deterministic time-dependent tests. */
class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant

    companion object {
        /** Frozen at 2026-07-14T00:00:00Z. */
        val default = FixedClock(Instant.parse("2026-07-14T00:00:00Z"))
    }
}
