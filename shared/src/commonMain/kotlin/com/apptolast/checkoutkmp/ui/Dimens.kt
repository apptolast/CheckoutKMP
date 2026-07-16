package com.apptolast.checkoutkmp.ui

import androidx.compose.ui.unit.dp

/**
 * Central spacing and sizing scale for the checkout UI. Composables reference these named values
 * instead of scattering raw `dp` literals, so the layout rhythm stays consistent and adjustable
 * from one place.
 */
object Dimens {
    /** Fine adjustment nudge (e.g. a button pushed slightly off its neighbour). */
    val spacingTiny = 2.dp

    /** Extra-small gap, e.g. between a label and its value inside a card. */
    val spacingXSmall = 4.dp

    /** Small gap between closely related controls. */
    val spacingSmall = 8.dp

    /** Default gap between fields in a form. */
    val spacingMedium = 12.dp

    /** Content padding inside cards; vertical screen padding. */
    val spacingLarge = 16.dp

    /** Gap between top-level screen sections; horizontal screen padding. */
    val spacingXLarge = 20.dp

    /** Generous gap around terminal (success/failure) screens. */
    val spacingXXLarge = 32.dp

    /** Diameter of the inline "processing" progress indicator on the status line. */
    val inlineProgressSize = 20.dp

    /** Small leading icon inside text fields, chips and buttons. */
    val iconSmall = 18.dp

    /** Standard icon (app bar, list rows). */
    val iconMedium = 24.dp

    /** Icon inside the large success/failure status badge. */
    val statusIconSize = 44.dp

    /** Tinted circle behind the terminal status icon. */
    val statusBadgeSize = 88.dp

    /** Corner radius for the branded summary header. */
    val cornerLarge = 24.dp

    /** Faint card motif watermarked into the branded header. */
    val watermarkIconSize = 72.dp
}
