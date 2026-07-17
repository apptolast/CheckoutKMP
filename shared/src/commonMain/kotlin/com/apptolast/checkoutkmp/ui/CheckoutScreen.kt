package com.apptolast.checkoutkmp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// Alpha applied to white content laid over the brand gradient header.
private const val ON_GRADIENT_WATERMARK_ALPHA = 0.16f
private const val ON_GRADIENT_MUTED_ALPHA = 0.85f

// Hours and minutes are always shown with two digits ("09:05", not "9:5").
private const val TIME_COMPONENT_DIGITS = 2

// Fraction of the content height the entering status screen slides up from.
private const val ENTER_SLIDE_FRACTION = 8

// How long the "copied" confirmation stays shown before its slot clears, so copying the same
// value again later produces a fresh live-region announcement.
private val COPY_CONFIRMATION_DURATION = 4.seconds

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
        // One scroll state for every phase; reset to the top when the phase changes so a receipt or
        // challenge never arrives inheriting the form's scroll offset (which used to clip the badge).
        val scrollState = rememberScrollState()
        LaunchedEffect(phaseKey(state.status)) { scrollState.scrollTo(0) }
        BoxWithConstraints(
            // imePadding here so the measured viewport shrinks above the keyboard, keeping the Pay
            // button and status line reachable while typing.
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
        ) {
            // Height available for content once the screen's own vertical padding is removed; the
            // focused steps fill it so they center vertically instead of hugging the app bar.
            val focusedMinHeight = maxHeight - Dimens.spacingLarge * 2
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
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
                    val centered = isCenteredPhase(animatedStatus)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (centered) Modifier.heightIn(min = focusedMinHeight) else Modifier),
                        verticalArrangement = if (centered) {
                            Arrangement.spacedBy(Dimens.spacingXLarge, Alignment.CenterVertically)
                        } else {
                            Arrangement.spacedBy(Dimens.spacingXLarge)
                        },
                    ) {
                        StatusContent(animatedStatus, state, onIntent)
                    }
                }
            }
        }
    }
}

/**
 * Single-task steps (3D Secure, provider redirect, terminal failure) are short, so they center in
 * the viewport instead of clinging to the top with a large void below. The form and the receipts
 * carry enough content to stay top-aligned and scroll.
 */
private fun isCenteredPhase(status: CheckoutStatus): Boolean = when (status) {
    is CheckoutStatus.RequiresSca,
    is CheckoutStatus.RequiresRedirect,
    is CheckoutStatus.Failed,
    -> true

    else -> false
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
            resendSecondsLeft = status.resendSecondsLeft,
            otpResent = status.otpResent,
            onVerify = { onIntent(CheckoutIntent.SubmitOtp(it)) },
            onResend = { onIntent(CheckoutIntent.ResendOtp) },
            onCancel = { onIntent(CheckoutIntent.CancelSca) },
            modifier = Modifier.fillMaxWidth(),
        )

        is CheckoutStatus.RequiresRedirect -> RedirectApprovalScreen(
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
            ScenarioSelector(
                selected = state.scenario,
                enabled = !state.isProcessing,
                onSelect = { onIntent(CheckoutIntent.SelectScenario(it)) },
            )
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
                    // Pay button for the gift-card-covers-everything path: no card form, no 3D Secure.
                    PayCta(
                        label = strings.payWithGiftCard(state.plan.giftCardPortion.formatWithCurrency()),
                        enabled = !state.isProcessing,
                        onClick = { onIntent(CheckoutIntent.SubmitGiftCardOnly) },
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
                    // Wallets do not split tenders: they always charge the full order total.
                    label = strings.payWith(methodLabel(state.method), state.amount.formatWithCurrency()),
                    enabled = !state.isProcessing,
                    onPay = { onIntent(CheckoutIntent.SubmitWallet) },
                )
                if (state.walletIgnoresGiftCard) {
                    // The user last saw "remaining to pay X" with the gift card applied; say
                    // explicitly that this wallet charges the full total instead. Polite live
                    // region so switching methods announces the caveat without stealing focus.
                    Text(
                        strings.giftCardNotUsedWith(methodLabel(state.method)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScenarioSelector(
    selected: PaymentScenario,
    enabled: Boolean,
    onSelect: (PaymentScenario) -> Unit,
) {
    val strings = LocalStrings.current
    // Same structure as the Payment method / Gift card sections: a flush heading (here with a DEMO
    // tag marking the test rig instead of a tonal box) and a trailing divider — one consistent rhythm.
    Column {
        SectionHeading(CheckoutIcons.Bolt, strings.testScenario) { DemoBadge() }
        Spacer(Modifier.height(Dimens.spacingSmall))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
        ) {
            PaymentScenario.entries.forEach { scenario ->
                val isSelected = scenario == selected
                FilterChip(
                    selected = isSelected,
                    enabled = enabled,
                    onClick = { onSelect(scenario) },
                    label = { Text(scenarioLabel(scenario)) },
                    // Same soft radius as the text fields so inputs read as one family.
                    shape = RoundedCornerShape(Dimens.cornerField),
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
        HorizontalDivider(modifier = Modifier.padding(top = Dimens.spacingMedium))
    }
}

/** Small tonal "DEMO" tag next to a heading, marking a section as test-rig rather than product. */
@Composable
private fun DemoBadge() {
    Text(
        text = LocalStrings.current.demoTag,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.cornerMedium))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.spacingSmall, vertical = Dimens.spacingXSmall),
    )
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
                    methodOptionIcon(option),
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
            val fieldEnabled = enabled && !state.isApplyingGiftCard
            val apply = { if (code.isNotBlank()) onIntent(CheckoutIntent.ApplyGiftCard(code)) }
            Row(
                // Top-align so the Apply button lines up with the field's input box, not with the
                // taller field-plus-supporting-text block.
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { new ->
                        // A stale "not found" verdict clears as soon as the user edits the code.
                        if (new != code && state.giftCardNotFound) {
                            onIntent(CheckoutIntent.ClearGiftCardError)
                        }
                        code = new
                    },
                    label = { Text(strings.giftCardCode) },
                    singleLine = true,
                    enabled = fieldEnabled,
                    isError = state.giftCardNotFound,
                    supportingText = {
                        // Slot always present (the demo hint by default) so isError associates the
                        // message with the field semantically and nothing jumps when it appears.
                        if (state.giftCardNotFound) {
                            Text(
                                strings.giftCardNotFound,
                                // Announce "not found" when it appears without stealing focus.
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        } else {
                            Text(
                                strings.demoGiftCards(
                                    "${DemoDefaults.GIFT_CARD_PARTIAL}, ${DemoDefaults.GIFT_CARD_FULL}",
                                ),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        // Codes are uppercase alphanumerics, never words: no autocorrect.
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { apply() }),
                    shape = RoundedCornerShape(Dimens.cornerField),
                    modifier = Modifier.weight(1f),
                )
                // Align to the field's input box and give it the same busy-spinner standard as the
                // other actions. Not full-width: it sits beside the field.
                BusyButton(
                    label = strings.apply,
                    isBusy = state.isApplyingGiftCard,
                    enabled = fieldEnabled && code.isNotBlank(),
                    onClick = apply,
                    modifier = Modifier.height(Dimens.fieldHeight),
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    // One merged announcement (code + applied + remaining) instead of three stray
                    // texts; the Polite live region announces the tender the moment it is applied.
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                ) {
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
                TextButton(
                    onClick = { onIntent(CheckoutIntent.RemoveGiftCard) },
                    enabled = enabled,
                    // "Remove" alone is ambiguous out of context: name the target for readers.
                    modifier = Modifier.semantics {
                        contentDescription = strings.removeGiftCard(giftCard.code)
                    },
                ) {
                    Text(strings.remove)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = Dimens.spacingSmall))
    }
}

/** A titled section heading with a leading brand-tinted icon and an optional [trailing] slot. */
@Composable
private fun SectionHeading(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    trailing: (@Composable () -> Unit)? = null,
) {
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
        trailing?.invoke()
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
    PayCta(label = label, enabled = enabled, onClick = onPay)
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
            // The error red (not the muted on-container) so the icon carries the same alarm as the
            // headline below it.
            contentColor = MaterialTheme.colorScheme.error,
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
            PayCta(
                label = strings.retry,
                enabled = true,
                onClick = onRetry,
                icon = CheckoutIcons.Refresh,
            )
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
        badgeIcon = CheckoutIcons.Block,
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
        note = LocalStrings.current.refundedNote,
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
    // Slot always present so a failed settlement appearing never shifts the receipt below it.
    if (error != null) {
        Text(
            errorMessage(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        )
    } else {
        Text("", style = MaterialTheme.typography.bodyMedium)
    }
    BusyButton(
        label = label,
        busyLabel = busyLabel,
        isBusy = isBusy,
        enabled = enabled,
        primary = primary,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The itemised receipt card: tenders (method + gift card), auth code and payment id.
 *  Shared with the order-detail screen, which shows the same card for a past payment. */
@Composable
internal fun ReceiptDetails(receipt: Receipt) {
    val strings = LocalStrings.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    // Transient copy confirmation; cleared after a beat so a later copy announces again.
    var copiedMessage by remember { mutableStateOf("") }
    LaunchedEffect(copiedMessage) {
        if (copiedMessage.isNotEmpty()) {
            delay(COPY_CONFIRMATION_DURATION)
            copiedMessage = ""
        }
    }

    // SECURITY: only the receipt identifiers (payment id, auth code) are ever copyable.
    // The masked card line must never gain a copy affordance — nothing card-shaped
    // belongs in the clipboard, not even the mask.
    fun copyIdentifier(label: String, value: String) {
        scope.launch {
            clipboard.setClipEntry(plainTextClipEntry(value))
            copiedMessage = strings.copiedAnnouncement(label)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium),
        ) {
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
                    icon = walletIcon(method.provider),
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
            val giftCardTender = receipt.giftCard
            if (giftCardTender != null && receipt.method !is PaymentMethod.GiftCard) {
                val tenderAmount = giftCardTender.amount.formatWithCurrency()
                ReceiptRow(
                    icon = CheckoutIcons.Receipt,
                    label = strings.giftCard,
                    value = "${strings.minusSign}$tenderAmount",
                    // Read "minus X" cleanly — screen readers skip or garble the U+2212 sign.
                    valueDescription = strings.minusAmount(tenderAmount),
                )
            }
            val authCode = receipt.authCode
            if (authCode != null) {
                ReceiptRow(
                    icon = CheckoutIcons.CheckCircle,
                    label = strings.authCode,
                    value = authCode,
                    onCopy = { copyIdentifier(strings.authCode, authCode) },
                )
            }
            ReceiptRow(
                icon = CheckoutIcons.Receipt,
                label = strings.paymentId,
                value = receipt.paymentId,
                onCopy = { copyIdentifier(strings.paymentId, receipt.paymentId) },
            )
            // Slot always present so the copy confirmation appearing never shifts the rows below;
            // the Polite live region announces "{label} copied" for TalkBack without moving focus.
            Text(
                copiedMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            ReceiptRow(
                icon = CheckoutIcons.CalendarMonth,
                label = strings.date,
                value = formatReceiptDate(receipt.createdAt, strings),
            )
            HorizontalDivider()
            // After-sales eligibility: the payment method conditions the business, not just the charge.
            AfterSalesRow(label = strings.sizeChange, available = receipt.method.afterSales.canChangeSize)
            AfterSalesRow(label = strings.refundToOrigin, available = receipt.method.afterSales.canRefundToOrigin)
        }
    }
}

/**
 * Formats the receipt's creation timestamp in the device time zone, e.g. `17 Jul 2026, 13:25`.
 * Month names come from the localized [Strings] catalog; the rest is locale-neutral.
 */
private fun formatReceiptDate(createdAt: Instant, strings: Strings): String {
    val local = createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = strings.monthAbbreviations[local.month.ordinal]
    val hour = local.hour.toString().padStart(TIME_COMPONENT_DIGITS, '0')
    val minute = local.minute.toString().padStart(TIME_COMPONENT_DIGITS, '0')
    return "${local.day} $month ${local.year}, $hour:$minute"
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

/**
 * One labelled line of the receipt card. [onCopy], when present, adds a trailing copy button named
 * "Copy {label}" for screen readers. Reserved for the receipt identifiers (payment id, auth code):
 * card data — even masked — must never be made copyable.
 */
@Composable
private fun ReceiptRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueDescription: String? = null,
    onCopy: (() -> Unit)? = null,
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
        if (onCopy != null) {
            IconButton(onClick = onCopy) {
                Icon(
                    CheckoutIcons.Copy,
                    contentDescription = LocalStrings.current.copyLabel(label),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.iconSmall),
                )
            }
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
