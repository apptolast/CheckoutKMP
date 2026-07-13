package com.apptolast.checkoutkmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apptolast.checkoutkmp.data.psp.PspScenario
import com.apptolast.checkoutkmp.domain.model.PaymentError
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.presentation.CheckoutIntent
import com.apptolast.checkoutkmp.presentation.CheckoutState
import com.apptolast.checkoutkmp.presentation.CheckoutStatus
import com.apptolast.checkoutkmp.presentation.MethodOption
import com.apptolast.checkoutkmp.presentation.CheckoutViewModel
import org.koin.androidx.compose.koinViewModel

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
        topBar = { TopAppBar(title = { Text("Checkout") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
                    StatusArea(status)
                }
            }
        }
    }
}

@Composable
private fun OrderSummary(state: CheckoutState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Order total", style = MaterialTheme.typography.labelMedium)
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
    selected: PspScenario,
    enabled: Boolean,
    onSelect: (PspScenario) -> Unit,
) {
    Column {
        Text("Test scenario (demo)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PspScenario.entries.forEach { scenario ->
                FilterChip(
                    selected = scenario == selected,
                    enabled = enabled,
                    onClick = { onSelect(scenario) },
                    label = { Text(scenario.demoLabel) },
                )
            }
        }
    }
}

private val PspScenario.demoLabel: String
    get() = when (this) {
        PspScenario.APPROVED -> "Approved"
        PspScenario.NEEDS_SCA -> "3D Secure"
        PspScenario.DECLINED -> "Declined"
        PspScenario.NETWORK_ERROR -> "Network error"
    }

@Composable
private fun MethodSelector(
    selected: MethodOption,
    enabled: Boolean,
    onSelect: (MethodOption) -> Unit,
) {
    Column {
        Text("Payment method", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        MethodOption.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, enabled = enabled) { onSelect(option) }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = option == selected, enabled = enabled, onClick = { onSelect(option) })
                Text(option.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun StatusArea(status: CheckoutStatus) {
    when (status) {
        CheckoutStatus.Editing, is CheckoutStatus.Approved -> Unit

        CheckoutStatus.Processing -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(0.dp))
            Text("  Processing payment…", style = MaterialTheme.typography.bodyMedium)
        }

        is CheckoutStatus.RequiresSca -> Text(
            "Additional authentication required (3D Secure) — coming in the next phase.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        is CheckoutStatus.Failed -> Text(
            errorMessage(status.error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ReceiptView(receipt: Receipt, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Payment approved", style = MaterialTheme.typography.headlineSmall)
        Text(receipt.amount.formatWithCurrency(), style = MaterialTheme.typography.headlineMedium)
        Text("${receipt.brand.displayName} · ${receipt.maskedCard}", textAlign = TextAlign.Center)
        Text("Auth code: ${receipt.authCode}", style = MaterialTheme.typography.bodySmall)
        Text("Payment id: ${receipt.paymentId}", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onDone, modifier = Modifier.padding(top = 8.dp)) {
            Text("New payment")
        }
    }
}

/** Basic per-type message; phase 7 replaces this with the full accessible error taxonomy. */
private fun errorMessage(error: PaymentError): String = when (error) {
    is PaymentError.Declined -> "Your card was declined (${error.reason})."
    is PaymentError.InvalidCard -> "Card details are invalid: ${error.reason}."
    PaymentError.Network -> "Network problem. Please try again."
    PaymentError.Timeout -> "The request timed out. Please try again."
    PaymentError.RateLimited -> "Too many attempts. Please wait a moment."
    is PaymentError.ScaFailed -> "Authentication failed (${error.reason})."
    PaymentError.Cancelled -> "Payment cancelled."
    is PaymentError.Unknown -> "Something went wrong. Please try again."
}
