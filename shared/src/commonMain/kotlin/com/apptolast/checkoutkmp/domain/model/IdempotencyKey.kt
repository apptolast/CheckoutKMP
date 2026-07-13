package com.apptolast.checkoutkmp.domain.model

import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/**
 * Uniquely identifies a single payment attempt. The PSP dedupes by this key, so a **retry of a
 * transient failure reuses the same key** and never double-charges. Backed by [Uuid] (stdlib).
 */
@JvmInline
value class IdempotencyKey(val value: Uuid) {
    override fun toString(): String = value.toString()

    companion object {
        /** Generate a fresh key for a new payment attempt. */
        fun random(): IdempotencyKey = IdempotencyKey(Uuid.random())

        fun parse(text: String): IdempotencyKey = IdempotencyKey(Uuid.parse(text))
    }
}
