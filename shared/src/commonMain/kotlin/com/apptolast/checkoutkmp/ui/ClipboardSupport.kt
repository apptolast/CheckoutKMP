package com.apptolast.checkoutkmp.ui

import androidx.compose.ui.platform.ClipEntry

/**
 * Builds a plain-text [ClipEntry] for the non-deprecated `LocalClipboard.setClipEntry` API.
 * Expect/actual because Compose Multiplatform has no common factory yet: `ClipEntry.withPlainText`
 * exists on iOS/desktop but not on the Android artifact, which only offers `ClipEntry(ClipData)`.
 */
expect fun plainTextClipEntry(text: String): ClipEntry
