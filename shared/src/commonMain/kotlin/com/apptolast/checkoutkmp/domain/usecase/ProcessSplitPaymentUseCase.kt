package com.apptolast.checkoutkmp.domain.usecase

import com.apptolast.checkoutkmp.domain.giftcard.GiftCardRedemption
import com.apptolast.checkoutkmp.domain.giftcard.GiftCardService
import com.apptolast.checkoutkmp.domain.giftcard.RedemptionResult
import com.apptolast.checkoutkmp.domain.model.Amount
import com.apptolast.checkoutkmp.domain.model.GiftCard
import com.apptolast.checkoutkmp.domain.model.GiftCardTender
import com.apptolast.checkoutkmp.domain.model.IdempotencyKey
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.PaymentRequest
import com.apptolast.checkoutkmp.domain.model.PaymentResult
import com.apptolast.checkoutkmp.domain.model.PaymentState
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.model.planSplit
import com.apptolast.checkoutkmp.domain.model.toPaymentState
import com.apptolast.checkoutkmp.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Events emitted while a split (gift card + card) payment progresses. Besides the plain
 * [StateChanged] stream, [GiftCardRedeemed] hands the caller the redemption so it can compensate
 * later if the flow is abandoned mid-way (e.g. the user cancels a pending 3D Secure challenge).
 */
sealed interface SplitPaymentEvent {
    data class GiftCardRedeemed(val redemption: GiftCardRedemption) : SplitPaymentEvent
    data class StateChanged(val state: PaymentState) : SplitPaymentEvent
}

/**
 * Pays an order with a gift card first and, if the balance does not cover the total, the card for
 * the remainder — the Zara-style split tender.
 *
 * Rules:
 * - **Order of application:** the gift card is always consumed first ([planSplit]).
 * - **Full coverage:** when the balance covers the total there is no card charge and therefore no
 *   3D Secure; the payment settles immediately ([PaymentState.Captured]).
 * - **Partial failure = compensation:** if the gift card was redeemed but the card charge fails
 *   with a **non-transient** business error (declined, invalid card), the redemption is reversed so
 *   the customer keeps their balance, and the failure is surfaced. Transient card failures do NOT
 *   compensate: the whole saga can be re-run with the **same keys** — the redemption replays
 *   idempotently and only the card call is actually retried.
 *
 * One attempt = one [redemptionKey] + one [reversalKey] (and the card request's own key), so no
 * step can ever run twice.
 */
class ProcessSplitPaymentUseCase(
    private val giftCards: GiftCardService,
    private val repository: PaymentRepository,
) {
    operator fun invoke(
        total: Amount,
        giftCard: GiftCard,
        cardRequest: PaymentRequest?,
        redemptionKey: IdempotencyKey,
        reversalKey: IdempotencyKey,
    ): Flow<SplitPaymentEvent> = flow {
        val plan = planSplit(total, giftCard)
        require(plan.coversTotal || cardRequest != null) {
            "A card request is required when the gift card does not cover the total"
        }
        require(cardRequest == null || cardRequest.amount == plan.remainder) {
            "The card request must charge exactly the remainder"
        }

        emit(SplitPaymentEvent.StateChanged(PaymentState.Processing))

        val redemption = when (val result = giftCards.redeem(giftCard.code, plan.giftCardPortion, redemptionKey)) {
            is RedemptionResult.Failure -> {
                emit(SplitPaymentEvent.StateChanged(PaymentState.Failed(result.error)))
                return@flow
            }
            is RedemptionResult.Success -> result.redemption
        }
        emit(SplitPaymentEvent.GiftCardRedeemed(redemption))

        if (plan.coversTotal) {
            emit(SplitPaymentEvent.StateChanged(PaymentState.Captured(giftCardReceipt(redemption, total))))
            return@flow
        }

        val cardResult = repository.authorize(checkNotNull(cardRequest))
        if (cardResult is PaymentResult.Failed && !cardResult.error.isTransient) {
            // The sale failed for good after the balance was consumed: compensate before surfacing.
            giftCards.reverse(redemption.redemptionId, reversalKey)
            emit(SplitPaymentEvent.StateChanged(PaymentState.Failed(cardResult.error)))
            return@flow
        }
        emit(SplitPaymentEvent.StateChanged(cardResult.toPaymentState().withGiftCardTender(redemption)))
    }

    /** Receipt for a payment fully covered by the gift card: settled at once, no auth code. */
    private fun giftCardReceipt(redemption: GiftCardRedemption, total: Amount): Receipt = Receipt(
        paymentId = redemption.redemptionId,
        amount = total,
        method = PaymentMethod.GiftCard(redemption.code),
        createdAt = redemption.redeemedAt,
        authorizedAt = redemption.redeemedAt,
        authCode = null,
        capturedAt = redemption.redeemedAt,
        giftCard = GiftCardTender(redemption.redemptionId, redemption.amount),
    )
}

/** Stamps the gift-card tender onto the receipt of a successful card outcome. */
private fun PaymentState.withGiftCardTender(redemption: GiftCardRedemption): PaymentState {
    val tender = GiftCardTender(redemption.redemptionId, redemption.amount)
    return when (this) {
        is PaymentState.Authorized -> PaymentState.Authorized(receipt.copy(giftCard = tender))
        is PaymentState.Captured -> PaymentState.Captured(receipt.copy(giftCard = tender))
        else -> this
    }
}
