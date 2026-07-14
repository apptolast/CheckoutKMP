package com.apptolast.checkoutkmp.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apptolast.checkoutkmp.R
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.presentation.CheckoutIntent
import com.apptolast.checkoutkmp.presentation.CheckoutState
import com.apptolast.checkoutkmp.presentation.CheckoutStatus
import com.apptolast.checkoutkmp.presentation.MethodOption
import com.apptolast.checkoutkmp.presentation.CheckoutViewModel
import org.koin.androidx.compose.koinViewModel

// Number of trailing digits kept visible in a masked card (e.g. •••• 4242).
private const val MASKED_CARD_VISIBLE_DIGITS = 4

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
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.checkout_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Dimens.spacingXLarge, vertical = Dimens.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXLarge),
        ) {
            when (val status = state.status) {
                is CheckoutStatus.Approved -> ReceiptView(
                    receipt = status.receipt,
                    onDone = { onIntent(CheckoutIntent.Reset) },
                )

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
                    OrderSummary(state)
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
                        onSubmit = { onIntent(CheckoutIntent.Submit(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StatusLine(status)
                }
            }
        }
    }
}

@Composable
private fun OrderSummary(state: CheckoutState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXSmall),
        ) {
            Text(stringResource(R.string.order_total_label), style = MaterialTheme.typography.labelMedium)
            Text(
                state.amount.formatWithCurrency(),
                style = MaterialTheme.typography.headlineSmall,
            )
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
    Column {
        Text(
            stringResource(R.string.test_scenario_heading),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Dimens.spacingSmall))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
        ) {
            PaymentScenario.entries.forEach { scenario ->
                FilterChip(
                    selected = scenario == selected,
                    enabled = enabled,
                    onClick = { onSelect(scenario) },
                    label = { Text(stringResource(scenario.labelRes)) },
                )
            }
        }
    }
}

@get:StringRes
private val PaymentScenario.labelRes: Int
    get() = when (this) {
        PaymentScenario.APPROVED -> R.string.scenario_approved
        PaymentScenario.NEEDS_SCA -> R.string.scenario_needs_sca
        PaymentScenario.DECLINED -> R.string.scenario_declined
        PaymentScenario.NETWORK_ERROR -> R.string.scenario_network_error
        PaymentScenario.TIMEOUT -> R.string.scenario_timeout
        PaymentScenario.RATE_LIMITED -> R.string.scenario_rate_limited
    }

@Composable
private fun MethodSelector(
    selected: MethodOption,
    enabled: Boolean,
    onSelect: (MethodOption) -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.payment_method_heading),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Dimens.spacingSmall))
        MethodOption.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, enabled = enabled) { onSelect(option) }
                    .padding(vertical = Dimens.spacingXSmall),
            ) {
                RadioButton(selected = option == selected, enabled = enabled, onClick = { onSelect(option) })
                Text(stringResource(option.labelRes), style = MaterialTheme.typography.bodyLarge)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = Dimens.spacingSmall))
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
    val message = if (isProcessing) stringResource(R.string.status_processing) else ""

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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium),
    ) {
        Text(
            stringResource(R.string.failure_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Assertive
            },
        )
        Text(errorMessage(error), style = MaterialTheme.typography.bodyLarge)

        if (error.isTransient) {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_retry))
            }
        }
        OutlinedButton(onClick = onStartOver, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_start_over))
        }
    }
}

@Composable
private fun ReceiptView(receipt: Receipt, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium),
    ) {
        Text(
            stringResource(R.string.success_title),
            style = MaterialTheme.typography.headlineSmall,
            // Announce success on arrival and expose it as a heading for navigation.
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(receipt.amount.formatWithCurrency(), style = MaterialTheme.typography.headlineMedium)
        // Read the masked card cleanly instead of "dot dot dot dot 4242". Resolved here (not inside
        // the semantics lambda) because stringResource is @Composable.
        val brand = brandLabel(receipt.brand)
        val maskedCardDescription = stringResource(
            R.string.card_ending_in_a11y,
            brand,
            receipt.maskedCard.takeLast(MASKED_CARD_VISIBLE_DIGITS),
        )
        Text(
            stringResource(R.string.receipt_brand_masked, brand, receipt.maskedCard),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = maskedCardDescription },
        )
        Text(
            stringResource(R.string.receipt_auth_code, receipt.authCode),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            stringResource(R.string.receipt_payment_id, receipt.paymentId),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onDone, modifier = Modifier.padding(top = Dimens.spacingSmall)) {
            Text(stringResource(R.string.action_new_payment))
        }
    }
}

/**
 * Maps a domain [PaymentError] to its localized, user-facing message. The technical `reason`
 * carried by some errors is intentionally NOT shown — it's a diagnostic code (e.g. `insufficient_funds`),
 * not localized copy — so users only ever see fully translated messages.
 */
@Composable
private fun errorMessage(error: PaymentError): String = when (error) {
    is PaymentError.Declined -> stringResource(R.string.error_declined)
    is PaymentError.InvalidCard -> stringResource(R.string.error_invalid_card)
    PaymentError.Network -> stringResource(R.string.error_network)
    PaymentError.Timeout -> stringResource(R.string.error_timeout)
    PaymentError.RateLimited -> stringResource(R.string.error_rate_limited)
    is PaymentError.ScaFailed -> stringResource(R.string.error_sca_failed)
    PaymentError.Cancelled -> stringResource(R.string.error_cancelled)
    is PaymentError.Unknown -> stringResource(R.string.error_unknown)
}
