package com.apptolast.checkoutkmp.ui

/** Two-letter language code of the device (e.g. `en`, `es`), provided per platform. */
expect fun deviceLanguageCode(): String

private fun isSpanish(): Boolean = deviceLanguageCode().lowercase().startsWith("es")

/**
 * Pick the localized string for the current device language. Kotlin-based i18n: Compose resources
 * are not packaged into the Android app by the AGP 9 KMP-library plugin, so we localize in code.
 */
fun tr(en: String, es: String): String = if (isSpanish()) es else en
