package com.apptolast.checkoutkmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.apptolast.checkoutkmp.domain.model.RedirectChallenge
import com.apptolast.checkoutkmp.domain.model.RedirectReturn

/**
 * Simulated provider redirect (PayPal/Bizum). In a real app this would open the provider's page in
 * Custom Tabs and the outcome would come back through the [RedirectChallenge.returnUrl] deep link;
 * here the possible returns are demo buttons, framed in a [DemoSurface] so the test rig reads apart
 * from the one real action (cancelling). Whatever the user picks is only a **claim** — the
 * ViewModel reconciles it against the PSP's webhook record, which is where the truth lives.
 *
 * While the claim is being reconciled the tapped button swaps its leading icon for the in-button
 * spinner (same footprint, no layout jump) and an always-present status line announces politely.
 */
@Composable
fun RedirectApprovalScreen(
    challenge: RedirectChallenge,
    isConfirming: Boolean,
    onReturn: (RedirectReturn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    // Which simulated return was tapped, remembered locally so only THAT button shows the confirm
    // spinner while every button is disabled. A claim is never re-submittable mid-confirm, so a
    // stale value can only be replaced by the next tap.
    var tappedReturn by remember { mutableStateOf<RedirectReturn?>(null) }
    val confirmingReturn = tappedReturn.takeIf { isConfirming }
    val submit = { returned: RedirectReturn ->
        tappedReturn = returned
        onReturn(returned)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium)) {
        ScreenHeading(CheckoutIcons.Lock, strings.continueAt(challenge.provider))
        Text(strings.redirectWebhookNote, style = MaterialTheme.typography.bodyMedium)

        // Demo rig: the fake provider URL and the simulated returns live together on the tonal
        // surface; only Cancel below is a real user action.
        DemoSurface {
            Text(
                challenge.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                // A friendly description instead of a URL spelled out character by character.
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = strings.simulatedProviderPageLink(challenge.provider)
                },
            )
            Spacer(Modifier.height(Dimens.spacingMedium))
            BusyButton(
                label = strings.approveSimulated,
                icon = CheckoutIcons.CheckCircle,
                isBusy = confirmingReturn == RedirectReturn.APPROVED,
                enabled = !isConfirming,
                onClick = { submit(RedirectReturn.APPROVED) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Dimens.spacingSmall))
            BusyButton(
                label = strings.simulateProviderFailure,
                icon = CheckoutIcons.ErrorOutline,
                isBusy = confirmingReturn == RedirectReturn.FAILED,
                enabled = !isConfirming,
                primary = false,
                onClick = { submit(RedirectReturn.FAILED) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Always-present slot (empty when idle) so the announcement never shifts the buttons.
        Text(
            if (isConfirming) strings.confirmingWithProvider else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )

        BusyButton(
            label = strings.cancel,
            isBusy = confirmingReturn == RedirectReturn.CANCELLED,
            enabled = !isConfirming,
            primary = false,
            onClick = { submit(RedirectReturn.CANCELLED) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
