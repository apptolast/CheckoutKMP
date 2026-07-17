package com.apptolast.checkoutkmp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apptolast.checkoutkmp.domain.simulation.DemoDefaults
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.presentation.CheckoutIntent
import com.apptolast.checkoutkmp.presentation.CheckoutState
import com.apptolast.checkoutkmp.presentation.CheckoutStatus
import com.apptolast.checkoutkmp.presentation.MethodOption
import com.apptolast.checkoutkmp.presentation.CheckoutViewModel
import org.koin.compose.viewmodel.koinViewModel

// Alpha applied to white content laid over the brand gradient header.
private const val ON_GRADIENT_WATERMARK_ALPHA = 0.16f
private const val ON_GRADIENT_MUTED_ALPHA = 0.85f

// Fraction of the content height the entering status screen slides up from.
private const val ENTER_SLIDE_FRACTION = 8

// Accessibility note: these screens are plain vertical Column/Row layouts, so the natural focus
// and reading order already matches the visual order. We deliberately do NOT set traversalIndex —
// it is only warranted when composition/draw order diverges from the desired focus order.

@Composable
fun CheckoutRoute(
    onOpenHistory: () -> Unit = {},
    viewModel: CheckoutViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CheckoutScreen(state = state, onIntent = viewModel::onIntent, onOpenHistory = onOpenHistory)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    state: CheckoutState,
    onIntent: (CheckoutIntent) -> Unit,
    onOpenHistory: () -> Unit = {},
) {
    val strings = LocalStrings.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.checkout) },
                navigationIcon = {
                    // Decorative lock reinforcing the "secure checkout" framing.
                    Icon(
                        CheckoutIcons.Lock,
                        contentDescription = null,
                        modifier = Modifier.padding(horizontal = Dimens.spacingMedium).size(Dimens.iconMedium),
                    )
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            CheckoutIcons.History,
                            contentDescription = strings.orderHistory,
                            modifier = Modifier.size(Dimens.iconMedium),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        Column(
            // imePadding before verticalScroll so the scroll viewport shrinks above the keyboard,
            // keeping the Pay button and status line reachable while typing.
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.spacingXLarge, vertical = Dimens.spacingLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = state.status,
                // Key by phase, not by instance: sub-state flips inside one phase (isVerifying,
                // isCapturing, otpError…) update in place instead of re-running the transition.
                contentKey = { phaseKey(it) },
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / ENTER_SLIDE_FRACTION }) togetherWith fadeOut()
                },
                // Cap the content width so tablet/desktop layouts read as a centered checkout
                // column instead of edge-to-edge stretched fields.
                modifier = Modifier.widthIn(max = Dimens.contentMaxWidth).fillMaxWidth(),
            ) { animatedStatus ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXLarge),
                ) {
                    StatusContent(animatedStatus, state, onIntent)
                }
            }
        }
    }
}

/** Stable per-screen discriminator for [AnimatedContent]; editing and processing share the form. */
private fun phaseKey(status: CheckoutStatus): String = when (status) {
    is CheckoutStatus.Authorized -> "authorized"
    is CheckoutStatus.Captured -> "captured"
    is CheckoutStatus.Refunded -> "refunded"
    is CheckoutStatus.Voided -> "voided"
    is CheckoutStatus.RequiresSca -> "sca"
    is CheckoutStatus.RequiresRedirect -> "redirect"
    is CheckoutStatus.Failed -> "failed"
    else -> "form"
}

/** The per-status screen content, extracted so [AnimatedContent] animates over one composable. */
@Composable
private fun StatusContent(
    status: CheckoutStatus,
    state: CheckoutState,
    onIntent: (CheckoutIntent) -> Unit,
) {
    val strings = LocalStrings.current
    when (status) {
        is CheckoutStatus.Authorized -> AuthorizedReceiptView(status, onIntent)
        is CheckoutStatus.Captured -> CapturedReceiptView(status, onIntent)
        is CheckoutStatus.Refunded -> RefundedReceiptView(status.receipt, onIntent)
        is CheckoutStatus.Voided -> VoidedReceiptView(status.receipt, onIntent)

        is CheckoutStatus.RequiresSca -> ScaChallengeScreen(
            challenge = status.challenge,
            otpError = status.otpError,
            isVerifying = status.isVerifying,
            onVerify = { onIntent(CheckoutIntent.SubmitOtp(it)) },
            onCancel = { onIntent(CheckoutIntent.CancelSca) },
            modifier = Modifier.fillMaxWidth(),
        )

        is CheckoutStatus.RequiresRedirect -> RedirectApprovalScreen(
            provider = methodLabel(state.method),
            challenge = status.redirect,
            isConfirming = status.isConfirming,
            onReturn = { onIntent(CheckoutIntent.CompleteRedirect(it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        is CheckoutStatus.Failed -> FailureView(
            error = status.error,
            onRetry = { onIntent(CheckoutIntent.Retry) },
            onStartOver = { onIntent(CheckoutIntent.Reset) },
        )

        else -> {
            BrandHeader(state)
            DemoSurface {
                ScenarioSelector(
                    selected = state.scenario,
                    enabled = !state.isProcessing,
                    onSelect = { onIntent(CheckoutIntent.SelectScenario(it)) },
                )
            }
            MethodSelector(
                selected = state.method,
                enabled = !state.isProcessing,
                onSelect = { onIntent(CheckoutIntent.SelectMethod(it)) },
            )
            if (state.method == MethodOption.CARD) {
                // Split tenders are a card-checkout feature; wallets pay the full total.
                GiftCardSection(
                    state = state,
                    enabled = !state.isProcessing,
                    onIntent = onIntent,
                )
                if (state.plan.coversTotal) {
                    GiftCardOnlyPay(
                        enabled = !state.isProcessing,
                        onPay = { onIntent(CheckoutIntent.SubmitGiftCardOnly) },
                    )
                } else {
                    CardForm(
                        enabled = !state.isProcessing,
                        // With a gift card applied, the card only pays the remainder.
                        payAmount = state.plan.remainder.formatWithCurrency(),
                        onSubmit = { onIntent(CheckoutIntent.Submit(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                WalletPayButton(
                    label = strings.payWith(methodLabel(state.method)),
                    enabled = !state.isProcessing,
                    onPay = { onIntent(CheckoutIntent.SubmitWallet) },
                )
            }
            StatusLine(status)
        }
    }
}

/**
 * Tonal container that visually separates demo-harness controls from the real checkout UI, so
 * screenshots and demos read instantly which part is product and which is the test rig.
 */
@Composable
internal fun DemoSurface(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(Dimens.cornerMedium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Dimens.spacingMedium)) { content() }
    }
}

/**
 * Branded order-total header. The diagonal gradient and the faint card motif are the same ones used
 * in the launcher icon, so the screen and the app icon read as one product.
 */
@Composable
private fun BrandHeader(state: CheckoutState) {
    val strings = LocalStrings.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cornerLarge))
            .background(Brush.linearGradient(extraColors.brandGradient))
            .padding(Dimens.spacingXLarge),
    ) {
        Icon(
            CheckoutIcons.CreditCard,
            contentDescription = null,
            tint = Color.White.copy(alpha = ON_GRADIENT_WATERMARK_ALPHA),
            modifier = Modifier.size(Dimens.watermarkIconSize).align(Alignment.TopEnd),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
            // One merged announcement ("Order total, 49,99 €…") instead of three stray texts.
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                strings.orderTotal,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = ON_GRADIENT_MUTED_ALPHA),
            )
            Text(
                state.amount.formatWithCurrency(),
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXSmall),
            ) {
                Icon(
                    CheckoutIcons.Lock,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = ON_GRADIENT_MUTED_ALPHA),
                    modifier = Modifier.size(Dimens.iconSmall),
                )
                Text(
                    strings.securePaymentDemo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = ON_GRADIENT_MUTED_ALPHA),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScenarioSelector(
    selected: PaymentScenario,
    enabled: Boolean,
    onSelect: (PaymentScenario) -> Unit,
) {
    val strings = LocalStrings.current
    Column {
        SectionHeading(CheckoutIcons.Bolt, strings.testScenarioDemo)
        Spacer(Modifier.height(Dimens.spacingSmall))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
        ) {
            PaymentScenario.entries.forEach { scenario ->
                val isSelected = scenario == selected
                FilterChip(
                    selected = isSelected,
                    enabled = enabled,
                    onClick = { onSelect(scenario) },
                    label = { Text(scenarioLabel(scenario)) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                CheckoutIcons.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
private fun MethodSelector(
    selected: MethodOption,
    enabled: Boolean,
    onSelect: (MethodOption) -> Unit,
) {
    val strings = LocalStrings.current
    Column {
        SectionHeading(CheckoutIcons.CreditCard, strings.paymentMethod)
        Spacer(Modifier.height(Dimens.spacingSmall))
        MethodOption.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                    ) { onSelect(option) }
                    .padding(vertical = Dimens.spacingXSmall),
            ) {
                // onClick = null: the row is the single selectable target, so screen readers get
                // one focusable radio per option instead of two nested clickables.
                RadioButton(selected = option == selected, enabled = enabled, onClick = null)
                Icon(
                    CheckoutIcons.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconMedium),
                )
                Text(methodLabel(option), style = MaterialTheme.typography.bodyLarge)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = Dimens.spacingSmall))
    }
}

/**
 * Split-payment section: apply a gift card by code, or show the applied balance, the remaining
 * amount and a "remove" action. The code itself is not PCI data, so it can live in state; the
 * balance is only consumed when the order is actually paid.
 */
@Composable
private fun GiftCardSection(
    state: CheckoutState,
    enabled: Boolean,
    onIntent: (CheckoutIntent) -> Unit,
) {
    val strings = LocalStrings.current
    Column {
        SectionHeading(CheckoutIcons.Receipt, strings.giftCard)
        Spacer(Modifier.height(Dimens.spacingSmall))

        val giftCard = state.giftCard
        if (giftCard == null) {
            var code by remember { mutableStateOf("") }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(strings.giftCardCode) },
                    singleLine = true,
                    enabled = enabled,
                    isError = state.giftCardNotFound,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { onIntent(CheckoutIntent.ApplyGiftCard(code)) },
                    enabled = enabled && code.isNotBlank(),
                ) {
                    Text(strings.apply)
                }
            }
            val supportingText = if (state.giftCardNotFound) {
                strings.giftCardNotFound
            } else {
                strings.demoGiftCards("${DemoDefaults.GIFT_CARD_PARTIAL}, ${DemoDefaults.GIFT_CARD_FULL}")
            }
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.giftCardNotFound) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                // Announce "not found" when it appears without stealing focus.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(giftCard.code, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        strings.giftCardApplied(state.plan.giftCardPortion.formatWithCurrency()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = extraColors.success,
                    )
                    Text(
                        if (state.plan.coversTotal) {
                            strings.giftCardCoversTotal
                        } else {
                            strings.remainingToPay(state.plan.remainder.formatWithCurrency())
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onIntent(CheckoutIntent.RemoveGiftCard) }, enabled = enabled) {
                    Text(strings.remove)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = Dimens.spacingSmall))
    }
}

/** Pay button for the gift-card-covers-everything path: no card form, no 3D Secure. */
@Composable
private fun GiftCardOnlyPay(enabled: Boolean, onPay: () -> Unit) {
    val strings = LocalStrings.current
    Button(onClick = onPay, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(CheckoutIcons.Lock, contentDescription = null, modifier = Modifier.size(Dimens.iconSmall))
        Spacer(Modifier.width(Dimens.spacingSmall))
        Text(strings.payWithGiftCard)
    }
}

/** A titled section heading with a leading brand-tinted icon. */
@Composable
private fun SectionHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
        modifier = Modifier.semantics(mergeDescendants = true) { heading() },
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.iconSmall),
        )
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun scenarioLabel(scenario: PaymentScenario): String {
    val strings = LocalStrings.current
    return when (scenario) {
        PaymentScenario.APPROVED -> strings.scenarioApproved
        PaymentScenario.NEEDS_SCA -> strings.scenario3dSecure
        PaymentScenario.DECLINED -> strings.scenarioDeclined
        PaymentScenario.NETWORK_ERROR -> strings.scenarioNetworkError
        PaymentScenario.TIMEOUT -> strings.scenarioTimeout
        PaymentScenario.RATE_LIMITED -> strings.scenarioRateLimited
    }
}

@Composable
private fun methodLabel(option: MethodOption): String = when (option) {
    MethodOption.CARD -> LocalStrings.current.creditDebitCard
    // Real payment brands are proper nouns and are not localized.
    MethodOption.PAYPAL -> PaymentMethod.Wallet.Provider.PAYPAL.displayName
    MethodOption.BIZUM -> PaymentMethod.Wallet.Provider.BIZUM.displayName
}

/** Pay button for redirect wallets: the approval itself happens on the provider's page. */
@Composable
private fun WalletPayButton(label: String, enabled: Boolean, onPay: () -> Unit) {
    Button(onClick = onPay, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(CheckoutIcons.Lock, contentDescription = null, modifier = Modifier.size(Dimens.iconSmall))
        Spacer(Modifier.width(Dimens.spacingSmall))
        Text(label)
    }
}

/**
 * A single, always-present status line for the editing/processing states. It is a Polite
 * [LiveRegionMode] node that stays in the tree, so TalkBack announces progress when it appears.
 * (Terminal failures have their own [FailureView] screen.)
 */
@Composable
private fun StatusLine(status: CheckoutStatus) {
    val isProcessing = status is CheckoutStatus.Processing
    val message = if (isProcessing) LocalStrings.current.processingPayment else ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
    ) {
        if (isProcessing) {
            CircularProgressIndicator(modifier = Modifier.size(Dimens.inlineProgressSize))
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * A large tinted circle badge with a centered icon, used to headline the terminal success and
 * failure screens.
 */
@Composable
private fun StatusBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(Dimens.statusBadgeSize)
            .clip(CircleShape)
            .background(containerColor),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(Dimens.statusIconSize),
        )
    }
}

/**
 * Terminal failure screen with a per-type accessible message. Transient failures (already retried
 * under the hood) offer a manual "Retry" that reuses the same IdempotencyKey; non-transient ones
 * only offer starting over with a different card.
 */
@Composable
private fun FailureView(
    error: PaymentError,
    onRetry: () -> Unit,
    onStartOver: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium),
    ) {
        Spacer(Modifier.height(Dimens.spacingLarge))
        StatusBadge(
            icon = CheckoutIcons.ErrorOutline,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            strings.paymentFailed,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Assertive
            },
        )
        Text(
            errorMessage(error),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Dimens.spacingSmall))

        if (error.isTransient) {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Icon(CheckoutIcons.Refresh, contentDescription = null, modifier = Modifier.size(Dimens.iconSmall))
                Spacer(Modifier.width(Dimens.spacingSmall))
                Text(strings.retry)
            }
        }
        OutlinedButton(onClick = onStartOver, modifier = Modifier.fillMaxWidth()) {
            Text(strings.startOver)
        }
    }
}

/** Funds held, customer not charged yet: capture on dispatch, or void by cancelling the order. */
@Composable
private fun AuthorizedReceiptView(
    status: CheckoutStatus.Authorized,
    onIntent: (CheckoutIntent) -> Unit,
) {
    val strings = LocalStrings.current
    ReceiptScaffold(
        receipt = status.receipt,
        headline = strings.paymentAuthorized,
        amountColor = MaterialTheme.colorScheme.primary,
        badgeIcon = CheckoutIcons.Lock,
        badgeContainer = MaterialTheme.colorScheme.secondaryContainer,
        badgeContent = MaterialTheme.colorScheme.onSecondaryContainer,
        note = strings.authorizedChargeNote,
        onDone = { onIntent(CheckoutIntent.Reset) },
    ) {
        SettlementAction(
            label = strings.simulateDispatch,
            busyLabel = strings.capturingPayment,
            isBusy = status.isCapturing,
            error = status.captureError,
            enabled = !status.isWorking,
            onClick = { onIntent(CheckoutIntent.Capture) },
        )
        SettlementAction(
            label = strings.voidOrder,
            busyLabel = strings.voidingPayment,
            isBusy = status.isVoiding,
            error = status.voidError,
            enabled = !status.isWorking,
            primary = false,
            onClick = { onIntent(CheckoutIntent.Void) },
        )
    }
}

/** The order was cancelled before dispatch: the hold is gone and nothing was ever charged. */
@Composable
private fun VoidedReceiptView(receipt: Receipt, onIntent: (CheckoutIntent) -> Unit) {
    val strings = LocalStrings.current
    ReceiptScaffold(
        receipt = receipt,
        headline = strings.paymentVoided,
        amountColor = MaterialTheme.colorScheme.onSurfaceVariant,
        badgeIcon = CheckoutIcons.Lock,
        badgeContainer = MaterialTheme.colorScheme.surfaceVariant,
        badgeContent = MaterialTheme.colorScheme.onSurfaceVariant,
        note = strings.voidedNote,
        onDone = { onIntent(CheckoutIntent.Reset) },
    )
}

/** The customer has been charged: offers the refund. */
@Composable
private fun CapturedReceiptView(
    status: CheckoutStatus.Captured,
    onIntent: (CheckoutIntent) -> Unit,
) {
    val strings = LocalStrings.current
    ReceiptScaffold(
        receipt = status.receipt,
        headline = strings.paymentCaptured,
        amountColor = extraColors.success,
        badgeIcon = CheckoutIcons.CheckCircle,
        badgeContainer = extraColors.successContainer,
        badgeContent = extraColors.success,
        onDone = { onIntent(CheckoutIntent.Reset) },
    ) {
        // The refund action only exists when the method admits refund-to-origin.
        if (status.receipt.method.afterSales.canRefundToOrigin) {
            SettlementAction(
                label = strings.refund,
                busyLabel = strings.refundingPayment,
                isBusy = status.isRefunding,
                error = status.refundError,
                onClick = { onIntent(CheckoutIntent.Refund) },
            )
        }
    }
}

/** The charge was returned to the customer. */
@Composable
private fun RefundedReceiptView(receipt: Receipt, onIntent: (CheckoutIntent) -> Unit) {
    ReceiptScaffold(
        receipt = receipt,
        headline = LocalStrings.current.paymentRefunded,
        amountColor = MaterialTheme.colorScheme.onSurfaceVariant,
        badgeIcon = CheckoutIcons.Refresh,
        badgeContainer = MaterialTheme.colorScheme.surfaceVariant,
        badgeContent = MaterialTheme.colorScheme.onSurfaceVariant,
        onDone = { onIntent(CheckoutIntent.Reset) },
    )
}

/**
 * Shared layout for the three settlement receipts (authorized / captured / refunded): badge,
 * announced headline, amount, optional charge-timing note, itemised details and the phase-specific
 * [actions] above the always-present "new payment" exit.
 */
@Composable
private fun ReceiptScaffold(
    receipt: Receipt,
    headline: String,
    amountColor: Color,
    badgeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeContainer: Color,
    badgeContent: Color,
    note: String? = null,
    onDone: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Column(
        // fillMaxWidth, not fillMaxSize: the parent column is scrollable, so height is unbounded.
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium),
    ) {
        Spacer(Modifier.height(Dimens.spacingLarge))
        StatusBadge(
            icon = badgeIcon,
            containerColor = badgeContainer,
            contentColor = badgeContent,
        )
        Text(
            headline,
            style = MaterialTheme.typography.headlineSmall,
            // Announce the settlement outcome on arrival and expose it as a heading for navigation.
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(
            receipt.amount.formatWithCurrency(),
            style = MaterialTheme.typography.headlineMedium,
            color = amountColor,
        )
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Dimens.spacingSmall))
        ReceiptDetails(receipt)
        actions()
        // Full width like every other CTA in the flow — the exit action is not a footnote.
        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.spacingSmall),
        ) {
            Text(LocalStrings.current.newPayment)
        }
    }
}

/**
 * A capture/void/refund demo button with its in-flight progress and an inline, announced error
 * line — a failed settlement keeps the receipt on screen so tapping again retries with the same
 * IdempotencyKey. [primary] picks filled vs outlined so a secondary operation (voiding) does not
 * compete visually with the main one (capturing).
 */
@Composable
private fun SettlementAction(
    label: String,
    busyLabel: String,
    isBusy: Boolean,
    error: PaymentError?,
    onClick: () -> Unit,
    enabled: Boolean = !isBusy,
    primary: Boolean = true,
) {
    if (error != null) {
        Text(
            errorMessage(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
    val content: @Composable () -> Unit = {
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(Dimens.inlineProgressSize))
            Spacer(Modifier.width(Dimens.spacingSmall))
        }
        Text(if (isBusy) busyLabel else label)
    }
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/** The itemised receipt card: tenders (method + gift card), auth code and payment id.
 *  Shared with the order-detail screen, which shows the same card for a past payment. */
@Composable
internal fun ReceiptDetails(receipt: Receipt) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium),
        ) {
            val strings = LocalStrings.current
            when (val method = receipt.method) {
                is PaymentMethod.Card -> {
                    val brand = brandLabel(method.token.brand)
                    ReceiptRow(
                        icon = CheckoutIcons.CreditCard,
                        label = strings.card,
                        value = "$brand · ${method.token.masked}",
                        // Read the masked card cleanly instead of "dot dot dot dot 4242".
                        valueDescription = strings.cardEndingIn(brand, method.token.last4),
                    )
                }

                is PaymentMethod.Wallet -> ReceiptRow(
                    icon = CheckoutIcons.CreditCard,
                    label = strings.paymentMethod,
                    value = method.label,
                )

                is PaymentMethod.GiftCard -> ReceiptRow(
                    icon = CheckoutIcons.Receipt,
                    label = strings.giftCard,
                    value = method.code,
                )
            }
            // The gift-card tender of a split payment (skipped when it IS the method, shown above).
            if (receipt.giftCard != null && receipt.method !is PaymentMethod.GiftCard) {
                ReceiptRow(
                    icon = CheckoutIcons.Receipt,
                    label = strings.giftCard,
                    value = "−${receipt.giftCard.amount.formatWithCurrency()}",
                )
            }
            if (receipt.authCode != null) {
                ReceiptRow(
                    icon = CheckoutIcons.CheckCircle,
                    label = strings.authCode,
                    value = receipt.authCode,
                )
            }
            ReceiptRow(
                icon = CheckoutIcons.Receipt,
                label = strings.paymentId,
                value = receipt.paymentId,
            )
            HorizontalDivider()
            // After-sales eligibility: the payment method conditions the business, not just the charge.
            AfterSalesRow(label = strings.sizeChange, available = receipt.method.afterSales.canChangeSize)
            AfterSalesRow(label = strings.refundToOrigin, available = receipt.method.afterSales.canRefundToOrigin)
        }
    }
}

/** One post-sale operation and whether the receipt's payment method admits it. */
@Composable
private fun AfterSalesRow(label: String, available: Boolean) {
    val strings = LocalStrings.current
    ReceiptRow(
        icon = if (available) CheckoutIcons.CheckCircle else CheckoutIcons.ErrorOutline,
        label = label,
        value = if (available) strings.available else strings.notAvailable,
    )
}

@Composable
private fun ReceiptRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueDescription: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMedium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconMedium),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val valueModifier = if (valueDescription != null) {
                Modifier.clearAndSetSemantics { contentDescription = valueDescription }
            } else {
                Modifier
            }
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = valueModifier)
        }
    }
}

/** Maps a domain [PaymentError] to its localized, user-facing message. The technical `reason`
 *  carried by some errors is intentionally NOT shown — it's a diagnostic code, not localized copy. */
@Composable
private fun errorMessage(error: PaymentError): String {
    val strings = LocalStrings.current
    return when (error) {
        is PaymentError.Declined -> strings.errorDeclined
        is PaymentError.InvalidCard -> strings.errorInvalidCard
        PaymentError.Network -> strings.errorNetwork
        PaymentError.Timeout -> strings.errorTimeout
        PaymentError.RateLimited -> strings.errorRateLimited
        is PaymentError.ScaFailed -> strings.errorScaFailed
        PaymentError.Cancelled -> strings.errorCancelled
        is PaymentError.Unknown -> strings.errorUnknown
    }
}
