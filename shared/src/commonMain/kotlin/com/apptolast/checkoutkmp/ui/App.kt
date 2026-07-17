package com.apptolast.checkoutkmp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.koin.compose.KoinContext

/**
 * Root shared composable, hosted by both the Android and iOS entry points. [KoinContext] exposes the
 * already-started global Koin to the composition so `koinViewModel()` resolves on every platform.
 *
 * Navigation is a single hoisted flag (checkout ⇄ order history) — two screens do not warrant a
 * navigation library, and keeping it here leaves both routes platform-agnostic.
 */
@Composable
fun App() {
    KoinContext {
        CheckoutTheme {
            var showHistory by remember { mutableStateOf(false) }
            if (showHistory) {
                HistoryRoute(onBack = { showHistory = false })
            } else {
                CheckoutRoute(onOpenHistory = { showHistory = true })
            }
        }
    }
}
