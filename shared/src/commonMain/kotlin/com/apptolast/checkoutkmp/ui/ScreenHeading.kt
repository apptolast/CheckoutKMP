package com.apptolast.checkoutkmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * The top-of-screen title shared by the focused steps (3D Secure, provider redirect): a brand-tinted
 * leading [icon] and a `headlineSmall` [text], merged into one node that is both a navigation heading
 * and a Polite live region so the screen announces itself on arrival. Sibling of [SectionHeading],
 * which is the smaller in-form section title.
 */
@Composable
internal fun ScreenHeading(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
        modifier = Modifier.semantics(mergeDescendants = true) {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.iconMedium),
        )
        Text(text, style = MaterialTheme.typography.headlineSmall)
    }
}
