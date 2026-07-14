package com.apptolast.checkoutkmp.ui

import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

actual fun deviceLanguageCode(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)?.take(2) ?: "en"
