package com.apptolast.checkoutkmp.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

// Accessibility note: these screens are plain vertical Column/Row layouts, so the natural focus
// and reading order already matches the visual order. We deliberately do NOT set traversalIndex —
// it is only warranted when composition/draw order diverges from the desired focus order.

@Composable
fun CheckoutRoute(viewModel: CheckoutViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CheckoutScreen(state = state, onIntent = viewModel::onIntent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    state: CheckoutState,
    onIntent: (CheckoutIntent) -> Unit,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
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
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXLarge),
        ) {
            when (val status = state.status) {
                is CheckoutStatus.Authorized -> AuthorizedReceiptView(status, onIntent)
                is CheckoutStatus.Captured -> CapturedReceiptView(status, onIntent)
                is CheckoutStatus.Refunded -> RefundedReceiptView(status.receipt, onIntent)

                is CheckoutStatus.RequiresSca -> ScaChallengeScreen(
                    challenge = status.challenge,
                    otpError = status.otpError,
                    isVerifying = status.isVerifying,
                    onVerify = { onIntent(CheckoutIntent.SubmitOtp(it)) },
                    onCancel = { onIntent(CheckoutIntent.CancelSca) },
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
                    CardForm(
                        enabled = !state.isProcessing,
                        payAmount = state.amount.formatWithCurrency(),
                        onSubmit = { onIntent(CheckoutIntent.Submit(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StatusLine(status)
                }
            }
        }
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
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSmall)) {
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

/** Funds held, customer not charged yet: offers the demo "order dispatched" capture. */
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
            onClick = { onIntent(CheckoutIntent.Capture) },
        )
    }
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
        SettlementAction(
            label = strings.refund,
            busyLabel = strings.refundingPayment,
            isBusy = status.isRefunding,
            error = status.refundError,
            onClick = { onIntent(CheckoutIntent.Refund) },
        )
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
        OutlinedButton(onClick = onDone, modifier = Modifier.padding(top = Dimens.spacingSmall)) {
            Text(LocalStrings.current.newPayment)
        }
    }
}

/**
 * The capture/refund demo button with its in-flight progress and an inline, announced error line —
 * a failed settlement keeps the receipt on screen so tapping again retries with the same
 * IdempotencyKey.
 */
@Composable
private fun SettlementAction(
    label: String,
    busyLabel: String,
    isBusy: Boolean,
    error: PaymentError?,
    onClick: () -> Unit,
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
    Button(onClick = onClick, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
        if (isBusy) {
            CircularProgressIndicator(modifier = Modifier.size(Dimens.inlineProgressSize))
            Spacer(Modifier.width(Dimens.spacingSmall))
        }
        Text(if (isBusy) busyLabel else label)
    }
}

/** The itemised receipt card: payment method, auth code and payment id. */
@Composable
private fun ReceiptDetails(receipt: Receipt) {
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
            }
            ReceiptRow(
                icon = CheckoutIcons.CheckCircle,
                label = strings.authCode,
                value = receipt.authCode,
            )
            ReceiptRow(
                icon = CheckoutIcons.Receipt,
                label = strings.paymentId,
                value = receipt.paymentId,
            )
        }
    }
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
