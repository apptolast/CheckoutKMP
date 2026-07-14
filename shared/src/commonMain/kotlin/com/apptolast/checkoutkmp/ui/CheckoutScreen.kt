package com.apptolast.checkoutkmp.ui

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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apptolast.checkoutkmp.domain.simulation.PaymentScenario
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.presentation.CheckoutIntent
import com.apptolast.checkoutkmp.presentation.CheckoutState
import com.apptolast.checkoutkmp.presentation.CheckoutStatus
import com.apptolast.checkoutkmp.presentation.MethodOption
import com.apptolast.checkoutkmp.presentation.CheckoutViewModel
import org.koin.compose.viewmodel.koinViewModel

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
        topBar = { TopAppBar(title = { Text(tr("Checkout", "Pago")) }) },
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
            Text(tr("Order total", "Total del pedido"), style = MaterialTheme.typography.labelMedium)
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
            tr("Test scenario (demo)", "Escenario de prueba (demo)"),
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
                    label = { Text(scenarioLabel(scenario)) },
                )
            }
        }
    }
}

@Composable
private fun scenarioLabel(scenario: PaymentScenario): String = when (scenario) {
    PaymentScenario.APPROVED -> tr("Approved", "Aprobado")
    PaymentScenario.NEEDS_SCA -> "3D Secure"
    PaymentScenario.DECLINED -> tr("Declined", "Rechazado")
    PaymentScenario.NETWORK_ERROR -> tr("Network error", "Error de red")
    PaymentScenario.TIMEOUT -> tr("Timeout", "Tiempo agotado")
    PaymentScenario.RATE_LIMITED -> tr("Rate limited", "Límite de peticiones")
}

@Composable
private fun MethodSelector(
    selected: MethodOption,
    enabled: Boolean,
    onSelect: (MethodOption) -> Unit,
) {
    Column {
        Text(
            tr("Payment method", "Método de pago"),
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
                Text(methodLabel(option), style = MaterialTheme.typography.bodyLarge)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = Dimens.spacingSmall))
    }
}

@Composable
private fun methodLabel(option: MethodOption): String = when (option) {
    MethodOption.CARD -> tr("Credit / debit card", "Tarjeta de crédito / débito")
}

/**
 * A single, always-present status line for the editing/processing states. It is a Polite
 * [LiveRegionMode] node that stays in the tree, so TalkBack announces progress when it appears.
 * (Terminal failures have their own [FailureView] screen.)
 */
@Composable
private fun StatusLine(status: CheckoutStatus) {
    val isProcessing = status is CheckoutStatus.Processing
    val message = if (isProcessing) tr("Processing payment…", "Procesando pago…") else ""

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
            tr("Payment failed", "Pago fallido"),
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
                Text(tr("Retry", "Reintentar"))
            }
        }
        OutlinedButton(onClick = onStartOver, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Start over", "Empezar de nuevo"))
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
            tr("Payment approved", "Pago aprobado"),
            style = MaterialTheme.typography.headlineSmall,
            // Announce success on arrival and expose it as a heading for navigation.
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(receipt.amount.formatWithCurrency(), style = MaterialTheme.typography.headlineMedium)
        val brand = brandLabel(receipt.brand)
        val last4 = receipt.maskedCard.takeLast(MASKED_CARD_VISIBLE_DIGITS)
        // Read the masked card cleanly instead of "dot dot dot dot 4242".
        val maskedCardDescription = tr("$brand, card ending in $last4", "$brand, tarjeta terminada en $last4")
        Text(
            "$brand · ${receipt.maskedCard}",
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = maskedCardDescription },
        )
        Text(
            tr("Auth code: ${receipt.authCode}", "Código de autorización: ${receipt.authCode}"),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            tr("Payment id: ${receipt.paymentId}", "ID de pago: ${receipt.paymentId}"),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onDone, modifier = Modifier.padding(top = Dimens.spacingSmall)) {
            Text(tr("New payment", "Nuevo pago"))
        }
    }
}

/** Maps a domain [PaymentError] to its localized, user-facing message. The technical `reason`
 *  carried by some errors is intentionally NOT shown — it's a diagnostic code, not localized copy. */
@Composable
private fun errorMessage(error: PaymentError): String = when (error) {
    is PaymentError.Declined ->
        tr("Your card was declined. Please try another card.", "Tu tarjeta fue rechazada. Prueba con otra tarjeta.")
    is PaymentError.InvalidCard ->
        tr("Card details are invalid. Please check and try again.", "Los datos de la tarjeta no son válidos. Revísalos e inténtalo de nuevo.")
    PaymentError.Network ->
        tr("Network problem. Please try again.", "Problema de red. Inténtalo de nuevo.")
    PaymentError.Timeout ->
        tr("The request timed out. Please try again.", "La solicitud tardó demasiado. Inténtalo de nuevo.")
    PaymentError.RateLimited ->
        tr("Too many attempts. Please wait a moment.", "Demasiados intentos. Espera un momento.")
    is PaymentError.ScaFailed ->
        tr("Authentication failed. Please try again.", "La autenticación falló. Inténtalo de nuevo.")
    PaymentError.Cancelled ->
        tr("Payment cancelled.", "Pago cancelado.")
    is PaymentError.Unknown ->
        tr("Something went wrong. Please try again.", "Algo salió mal. Inténtalo de nuevo.")
}
