package com.apptolast.checkoutkmp.ui

import androidx.compose.runtime.Composable
import org.koin.compose.KoinContext

/**
 * Root shared composable, hosted by both the Android and iOS entry points. [KoinContext] exposes the
 * already-started global Koin to the composition so `koinViewModel()` resolves on every platform.
 */
@Composable
fun App() {
    KoinContext {
        CheckoutTheme {
            CheckoutRoute()
        }
    }
}
