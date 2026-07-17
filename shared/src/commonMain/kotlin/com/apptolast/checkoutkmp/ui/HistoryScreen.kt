package com.apptolast.checkoutkmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.domain.model.Receipt
import com.apptolast.checkoutkmp.domain.model.SettlementStatus
import com.apptolast.checkoutkmp.domain.model.lastUpdatedAt
import com.apptolast.checkoutkmp.domain.model.settlement
import com.apptolast.checkoutkmp.presentation.HistoryViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = koinViewModel(),
) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()

    // Selection by payment id, not by receipt: the detail keeps observing the flow, so if the
    // order's lifecycle moves on it re-renders with the fresh state.
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = orders.firstOrNull { it.paymentId == selectedId }

    if (selected != null) {
        OrderDetailScreen(order = selected, onBack = { selectedId = null })
    } else {
        HistoryScreen(
            orders = orders,
            onOpenOrder = { selectedId = it.paymentId },
            onBack = onBack,
        )
    }
}

/**
 * The session's order history: one row per payment, reflecting its **latest** settlement state
 * (authorized / charged / refunded / cancelled) — the same order updates as its lifecycle moves on.
 * Tapping a row opens the order's detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    orders: List<Receipt>,
    onOpenOrder: (Receipt) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    Scaffold(topBar = { HistoryTopBar(title = strings.orderHistory, onBack = onBack) }) { padding ->
        if (orders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.spacingXLarge),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    strings.emptyHistory,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    horizontal = Dimens.spacingXLarge,
                    vertical = Dimens.spacingLarge,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium),
            ) {
                items(orders, key = { it.paymentId }) { order ->
                    OrderRow(order, onClick = { onOpenOrder(order) })
                }
            }
        }
    }
}

/**
 * One past order in full: settlement headline, amount, the same itemised receipt card the checkout
 * shows, and the lifecycle timeline (when it was authorized / charged / refunded / cancelled).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    order: Receipt,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    Scaffold(topBar = { HistoryTopBar(title = strings.orderDetail, onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.spacingXLarge, vertical = Dimens.spacingLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium),
        ) {
            Text(
                settlementLabel(order.settlement),
                style = MaterialTheme.typography.headlineSmall,
                color = settlementColor(order.settlement),
                modifier = Modifier.semantics { heading() },
            )
            Text(order.amount.formatWithCurrency(), style = MaterialTheme.typography.headlineMedium)
            ReceiptDetails(order)
            TimelineCard(order)
        }
    }
}

/** When each lifecycle step happened; only the steps this order actually went through appear. */
@Composable
private fun TimelineCard(order: Receipt) {
    val strings = LocalStrings.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Dimens.spacingLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium),
        ) {
            Text(
                strings.timeline,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            TimelineRow(strings.statusAuthorized, order.authorizedAt)
            order.capturedAt?.let { TimelineRow(strings.statusCaptured, it) }
            order.refundedAt?.let { TimelineRow(strings.statusRefunded, it) }
            order.voidedAt?.let { TimelineRow(strings.statusVoided, it) }
        }
    }
}

@Composable
private fun TimelineRow(label: String, at: Instant) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            at.formatShort(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTopBar(title: String, onBack: () -> Unit) {
    val strings = LocalStrings.current
    TopAppBar(
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    CheckoutIcons.ArrowBack,
                    contentDescription = strings.back,
                    modifier = Modifier.size(Dimens.iconMedium),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            titleContentColor = MaterialTheme.colorScheme.primary,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** One order: payment method, when it last changed, total and its settlement status. */
@Composable
private fun OrderRow(order: Receipt, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMedium),
            modifier = Modifier.fillMaxWidth().padding(Dimens.spacingLarge),
        ) {
            Icon(
                methodIcon(order.method),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconMedium),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(methodSummary(order.method), style = MaterialTheme.typography.bodyLarge)
                Text(
                    order.lastUpdatedAt.formatShort(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(order.amount.formatWithCurrency(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    settlementLabel(order.settlement),
                    style = MaterialTheme.typography.labelMedium,
                    color = settlementColor(order.settlement),
                )
            }
        }
    }
}

@Composable
private fun methodIcon(method: PaymentMethod) = when (method) {
    is PaymentMethod.Card, is PaymentMethod.Wallet -> CheckoutIcons.CreditCard
    is PaymentMethod.GiftCard -> CheckoutIcons.Receipt
}

@Composable
private fun methodSummary(method: PaymentMethod): String = when (method) {
    is PaymentMethod.Card -> "${brandLabel(method.token.brand)} · ${method.token.masked}"
    // Wallet providers and gift-card codes are already display-safe labels.
    is PaymentMethod.Wallet, is PaymentMethod.GiftCard -> method.label
}

@Composable
private fun settlementLabel(status: SettlementStatus): String {
    val strings = LocalStrings.current
    return when (status) {
        SettlementStatus.AUTHORIZED -> strings.statusAuthorized
        SettlementStatus.CAPTURED -> strings.statusCaptured
        SettlementStatus.REFUNDED -> strings.statusRefunded
        SettlementStatus.VOIDED -> strings.statusVoided
    }
}

@Composable
private fun settlementColor(status: SettlementStatus): Color = when (status) {
    SettlementStatus.AUTHORIZED -> MaterialTheme.colorScheme.primary
    SettlementStatus.CAPTURED -> extraColors.success
    SettlementStatus.REFUNDED,
    SettlementStatus.VOIDED -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Local-time `yyyy-MM-dd HH:mm` — enough for a demo history without a formatting library. */
private fun Instant.formatShort(): String {
    val local = toLocalDateTime(TimeZone.currentSystemDefault())
    val month = local.month.number.toString().padStart(2, '0')
    val day = local.day.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "${local.year}-$month-$day $hour:$minute"
}
