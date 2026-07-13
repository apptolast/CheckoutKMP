package com.apptolast.checkoutkmp.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdempotencyKeyTest {

    @Test
    fun random_keys_are_unique() {
        assertNotEquals(IdempotencyKey.random(), IdempotencyKey.random())
    }

    @Test
    fun parse_round_trips_with_toString() {
        val key = IdempotencyKey.random()
        assertEquals(key, IdempotencyKey.parse(key.toString()))
    }
}
