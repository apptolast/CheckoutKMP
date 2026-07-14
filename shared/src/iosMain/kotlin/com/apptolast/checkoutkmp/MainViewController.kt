package com.apptolast.checkoutkmp

import androidx.compose.ui.window.ComposeUIViewController
import com.apptolast.checkoutkmp.di.initKoin
import com.apptolast.checkoutkmp.ui.App

/** Called once from SwiftUI's `App.init` to start the Koin graph on iOS. */
fun startKoinIos() = initKoin()

/** The Compose UI view controller hosted by SwiftUI's `ContentView`. */
fun MainViewController() = ComposeUIViewController { App() }
