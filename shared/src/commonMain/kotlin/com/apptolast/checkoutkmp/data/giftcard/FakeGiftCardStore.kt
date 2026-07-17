package com.apptolast.checkoutkmp.data.giftcard

import com.apptolast.checkoutkmp.domain.giftcard.GiftCardLookup
import com.apptolast.checkoutkmp.domain.giftcard.GiftCardRedemption
import com.apptolast.checkoutkmp.domain.giftcard.GiftCardService
import com.apptolast.checkoutkmp.domain.giftcard.RedemptionResult
import com.apptolast.checkoutkmp.domain.giftcard.ReversalResult
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.GiftCard
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.simulation.DemoDefaults
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * In-memory gift-card backend for the demo and tests. Owns the balances and the redemption
 * ledger, and — like [com.apptolast.checkoutkmp.data.psp.FakePsp] — is the source of truth the
 * domain reconciles against.
 *
 * - **Idempotency per operation:** redemptions and reversals each cache their outcome by
 *   [IdempotencyKey]; replaying one returns the original response and never moves the balance
 *   twice ([redemptionCount] / [reversalCount]).
 * - **Lifecycle:** a redemption can be reversed exactly once; reversing it again (with a new key)
 *   or redeeming beyond the balance is declined.
 * - Failures are mapped here, at the boundary, into the closed [PaymentError] taxonomy
 *   (as [PaymentError.Declined] with a diagnostic code).
 *
 * Codes are normalized (trim + uppercase) so user input like ` gift25 ` matches `GIFT25`.
 */
class FakeGiftCardStore(
    initialCards: Map<String, Amount> = DemoDefaults.giftCards,
    private val clock: Clock = Clock.System,
) : GiftCardService {

    private class RedemptionRecord(val code: String, val amount: Amount, var reversed: Boolean = false)

    private val balances = initialCards.mapKeys { it.key.normalized() }.toMutableMap()
    private val records = mutableMapOf<String, RedemptionRecord>()
    private val redemptions = mutableMapOf<IdempotencyKey, RedemptionResult>()
    private val reversals = mutableMapOf<IdempotencyKey, ReversalResult>()

    /** Number of real redemptions performed (idempotent replays do not increment it). */
    var redemptionCount: Int = 0
        private set

    /** Number of real reversals performed (idempotent replays do not increment it). */
    var reversalCount: Int = 0
        private set

    /** Test/demo visibility: the current balance of [code], or null if the card does not exist. */
    fun balanceOf(code: String): Amount? = balances[code.normalized()]

    override suspend fun lookup(code: String): GiftCardLookup {
        val normalized = code.normalized()
        val balance = balances[normalized] ?: return GiftCardLookup.NotFound
        return GiftCardLookup.Found(GiftCard(code = normalized, balance = balance))
    }

    override suspend fun redeem(code: String, amount: Amount, idempotencyKey: IdempotencyKey): RedemptionResult {
        redemptions[idempotencyKey]?.let { return it }

        val normalized = code.normalized()
        val balance = balances[normalized]
        val result = when {
            balance == null ->
                RedemptionResult.Failure(PaymentError.Declined("gift_card_not_found"))

            amount.currency != balance.currency || amount.minorUnits > balance.minorUnits ->
                RedemptionResult.Failure(PaymentError.Declined("gift_card_insufficient_balance"))

            else -> {
                redemptionCount++
                balances[normalized] = balance - amount
                val redemption = GiftCardRedemption(
                    redemptionId = "gcr_${Uuid.random()}",
                    code = normalized,
                    amount = amount,
                    redeemedAt = clock.now(),
                )
                records[redemption.redemptionId] = RedemptionRecord(normalized, amount)
                RedemptionResult.Success(redemption)
            }
        }
        redemptions[idempotencyKey] = result
        return result
    }

    override suspend fun reverse(redemptionId: String, idempotencyKey: IdempotencyKey): ReversalResult {
        reversals[idempotencyKey]?.let { return it }

        val record = records[redemptionId]
        val result = when {
            record == null ->
                ReversalResult.Failure(PaymentError.Declined("unknown_redemption"))

            record.reversed ->
                ReversalResult.Failure(PaymentError.Declined("already_reversed"))

            else -> {
                reversalCount++
                record.reversed = true
                balances[record.code] = balances.getValue(record.code) + record.amount
                ReversalResult.Success(reversedAt = clock.now())
            }
        }
        reversals[idempotencyKey] = result
        return result
    }

    private fun String.normalized(): String = trim().uppercase()
}
