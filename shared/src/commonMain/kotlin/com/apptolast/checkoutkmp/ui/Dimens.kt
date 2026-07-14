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

    /** Diameter of the inline "processing" progress indicator on the status line. */
    val inlineProgressSize = 20.dp
}
