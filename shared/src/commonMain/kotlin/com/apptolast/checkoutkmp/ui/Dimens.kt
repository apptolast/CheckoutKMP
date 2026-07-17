package com.apptolast.checkoutkmp.ui

import androidx.compose.ui.unit.dp

/**
 * Central spacing and sizing scale for the checkout UI. Composables reference these named values
 * instead of scattering raw `dp` literals, so the layout rhythm stays consistent and adjustable
 * from one place.
 */
object Dimens {
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

    /** Stroke of the small spinner that swaps in for a button's leading icon while working. */
    val progressStrokeThin = 2.dp

    /** Small leading icon inside text fields, chips and buttons. */
    val iconSmall = 18.dp

    /** Standard icon (app bar, list rows). */
    val iconMedium = 24.dp

    /** Icon inside the large success/failure status badge. */
    val statusIconSize = 44.dp

    /** Tinted circle behind the terminal status icon. */
    val statusBadgeSize = 88.dp

    /** Muted icon that headlines an empty state (e.g. the empty order history). */
    val emptyStateIconSize = 64.dp

    /** Height of a text field's input box, so a sibling button can align to it, not to the field
     *  plus its supporting text. */
    val fieldHeight = 56.dp

    /** Corner radius for inset tonal containers (demo surfaces). */
    val cornerMedium = 12.dp

    /** Corner radius for the branded summary header. */
    val cornerLarge = 24.dp

    /** Widest the checkout column gets on tablet/desktop before centering with side gutters. */
    val contentMaxWidth = 480.dp

    /** Faint card motif watermarked into the branded header. */
    val watermarkIconSize = 72.dp

    /** Width of one segmented OTP cell (six cells plus gaps fit well inside [contentMaxWidth]). */
    val otpCellWidth = 44.dp

    /** Height of one segmented OTP cell. */
    val otpCellHeight = 52.dp

    /** Corner radius of a segmented OTP cell. */
    val otpCellCorner = 8.dp

    /** Outline of an idle OTP cell. */
    val otpCellStroke = 1.dp

    /** Outline of the highlighted OTP cell (the one the next digit lands in). */
    val otpCellStrokeActive = 2.dp
}
