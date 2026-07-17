package com.apptolast.checkoutkmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.apptolast.checkoutkmp.domain.model.RedirectChallenge
import com.apptolast.checkoutkmp.domain.model.RedirectReturn

/**
 * Simulated provider redirect (PayPal/Bizum). In a real app this would open the provider's page in
 * Custom Tabs and the outcome would come back through the [RedirectChallenge.returnUrl] deep link;
 * here the three possible returns are demo buttons. Whatever the user picks is only a **claim** —
 * the ViewModel reconciles it against the PSP's webhook record, which is where the truth lives.
 */
@Composable
fun RedirectApprovalScreen(
    provider: String,
    challenge: RedirectChallenge,
    isConfirming: Boolean,
    onReturn: (RedirectReturn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
            modifier = Modifier.semantics(mergeDescendants = true) {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            Icon(
                CheckoutIcons.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconMedium),
            )
            Text(
                strings.continueAt(provider),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(strings.redirectWebhookNote, style = MaterialTheme.typography.bodyMedium)
        Text(
            challenge.url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        if (isConfirming) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(Dimens.inlineProgressSize))
                Text(
                    strings.confirmingWithProvider,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        Button(
            onClick = { onReturn(RedirectReturn.APPROVED) },
            enabled = !isConfirming,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(CheckoutIcons.CheckCircle, contentDescription = null, modifier = Modifier.size(Dimens.iconSmall))
            Spacer(Modifier.width(Dimens.spacingSmall))
            Text(strings.approveSimulated)
        }
        OutlinedButton(
            onClick = { onReturn(RedirectReturn.FAILED) },
            enabled = !isConfirming,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.simulateProviderFailure)
        }
        TextButton(
            onClick = { onReturn(RedirectReturn.CANCELLED) },
            enabled = !isConfirming,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.cancel)
        }
    }
}
