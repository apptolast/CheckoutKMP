package com.apptolast.checkoutkmp.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * House-standard in-button progress: content-colored, thin stroke, sized like a leading icon so it
 * swaps in for one without shifting the button's layout. The single source of this spinner — every
 * busy button in the app renders exactly this.
 */
@Composable
internal fun InButtonSpinner() {
    CircularProgressIndicator(
        color = LocalContentColor.current,
        strokeWidth = Dimens.progressStrokeThin,
        modifier = Modifier.size(Dimens.iconSmall),
    )
}

/**
 * An action button that shows the shared [InButtonSpinner] while [isBusy], with no layout jump: the
 * spinner replaces [icon] in the leading slot (or is prepended when there is no icon), and the
 * caption swaps [label] for [busyLabel]. [primary] chooses filled vs outlined so a secondary
 * operation does not compete with the main one. Width is the caller's to set via [modifier] (the
 * full-width CTAs pass `Modifier.fillMaxWidth()`; the inline gift-card Apply does not).
 *
 * Unifies the capture/void/refund actions, the SCA "Verify", the redirect buttons and the gift-card
 * "Apply", which all reimplemented the same spinner-swap by hand. Buttons with no busy state use
 * [PayCta] instead.
 */
@Composable
internal fun BusyButton(
    label: String,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = !isBusy,
    primary: Boolean = true,
    icon: ImageVector? = null,
    busyLabel: String = label,
) {
    val content: @Composable () -> Unit = {
        val hasLeading = isBusy || icon != null
        if (isBusy) {
            InButtonSpinner()
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(Dimens.iconSmall))
        }
        if (hasLeading) Spacer(Modifier.width(Dimens.spacingSmall))
        Text(if (isBusy) busyLabel else label)
    }
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { content() }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { content() }
    }
}
