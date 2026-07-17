package com.apptolast.checkoutkmp.support

import com.apptolast.checkoutkmp.domain.giftcard.GiftCardLookup
import com.apptolast.checkoutkmp.domain.giftcard.GiftCardService
import com.apptolast.checkoutkmp.domain.giftcard.RedemptionResult
import com.apptolast.checkoutkmp.domain.giftcard.ReversalResult
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey

/**
 * Scriptable [GiftCardService] test double. Each entry point returns whatever the supplied lambda
 * produces and records the calls so tests can assert idempotency-key reuse, amounts and
 * compensation ordering.
 */
class FakeGiftCardService(
    var onLookup: (String) -> GiftCardLookup = { GiftCardLookup.NotFound },
    var onRedeem: (String, Amount, IdempotencyKey) -> RedemptionResult = { _, _, _ -> error("redeem not scripted") },
    var onReverse: (String, IdempotencyKey) -> ReversalResult = { _, _ -> error("reverse not scripted") },
) : GiftCardService {

    val lookupCalls = mutableListOf<String>()
    val redeemCalls = mutableListOf<Triple<String, Amount, IdempotencyKey>>()
    val reverseCalls = mutableListOf<Pair<String, IdempotencyKey>>()

    override suspend fun lookup(code: String): GiftCardLookup {
        lookupCalls += code
        return onLookup(code)
    }

    override suspend fun redeem(code: String, amount: Amount, idempotencyKey: IdempotencyKey): RedemptionResult {
        redeemCalls += Triple(code, amount, idempotencyKey)
        return onRedeem(code, amount, idempotencyKey)
    }

    override suspend fun reverse(redemptionId: String, idempotencyKey: IdempotencyKey): ReversalResult {
        reverseCalls += redemptionId to idempotencyKey
        return onReverse(redemptionId, idempotencyKey)
    }
}
