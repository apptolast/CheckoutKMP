package com.apptolast.checkoutkmp.ui

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.toClipEntry

// Null label: the receipt identifiers need no user-visible clip description.
actual fun plainTextClipEntry(text: String): ClipEntry =
    ClipData.newPlainText(null, text).toClipEntry()
