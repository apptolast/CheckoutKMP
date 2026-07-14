import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Start the shared Koin graph before any Composable resolves a ViewModel.
        MainViewControllerKt.startKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
