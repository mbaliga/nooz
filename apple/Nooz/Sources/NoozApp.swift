import SwiftUI

@main
struct NoozApp: App {
    var body: some Scene {
        WindowGroup {
            ReaderWebView()
                .ignoresSafeArea()
                .background(Color(red: 0.078, green: 0.078, blue: 0.078)) // #141414, the web theme-color
        }
    }
}
