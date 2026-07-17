package com.apptolast.checkoutkmp.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The full-width primary call-to-action shared by every "pay now" entry point (card form, wallets,
 * gift-card-only) and the failure Retry: a leading [icon] (the secure-checkout lock by default),
 * a small gap and the [label]. Buttons with in-flight busy-spinner semantics (SettlementAction,
 * the SCA Verify) intentionally do NOT use this.
 */
@Composable
internal fun PayCta(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = CheckoutIcons.Lock,
) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(Dimens.iconSmall))
        Spacer(Modifier.width(Dimens.spacingSmall))
        Text(label)
    }
}
