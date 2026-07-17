package com.apptolast.checkoutkmp.domain.model

import com.apptolast.checkoutkmp.support.Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals

/** The receipt's settlement stage is derived from its timestamps — the latest step wins. */
class SettlementStatusTest {

    @Test
    fun a_bare_authorization_is_authorized() {
        assertEquals(SettlementStatus.AUTHORIZED, Fixtures.receipt.settlement)
    }

    @Test
    fun a_captured_receipt_is_captured() {
        assertEquals(SettlementStatus.CAPTURED, Fixtures.capturedReceipt.settlement)
    }

    @Test
    fun refund_wins_over_capture() {
        assertEquals(SettlementStatus.REFUNDED, Fixtures.refundedReceipt.settlement)
    }

    @Test
    fun a_voided_receipt_is_voided() {
        assertEquals(SettlementStatus.VOIDED, Fixtures.voidedReceipt.settlement)
    }

    @Test
    fun last_update_follows_the_latest_lifecycle_step() {
        assertEquals(Fixtures.receipt.authorizedAt, Fixtures.receipt.lastUpdatedAt)
        assertEquals(Fixtures.capturedReceipt.capturedAt, Fixtures.capturedReceipt.lastUpdatedAt)
        assertEquals(Fixtures.refundedReceipt.refundedAt, Fixtures.refundedReceipt.lastUpdatedAt)
        assertEquals(Fixtures.voidedReceipt.voidedAt, Fixtures.voidedReceipt.lastUpdatedAt)
    }
}
