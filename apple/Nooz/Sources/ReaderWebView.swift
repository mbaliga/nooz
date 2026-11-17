import SwiftUI
import UIKit
import WebKit
import NoozCore

/// Hosts the bundled Nooz web reader (web/, staged into Resources/web) in a WKWebView.
///
/// What this is: the web reader, packaged, with its two server helpers replaced by native
/// code (NoozSchemeHandler) and an in-page Readability shim. It is NOT the Android app
/// ported: no Room store, no local llama.cpp / Kokoro, no Lens. See apple/README.md.
struct ReaderWebView: UIViewRepresentable {
    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        // Persistent store: the reader's sources, reads and clippings live in this
        // WebView's IndexedDB (web/js/db.js). Nothing leaves the device.
        config.websiteDataStore = .default()
        config.setURLSchemeHandler(context.coordinator.schemeHandler, forURLScheme: RequestRoute.scheme)
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        config.allowsInlineMediaPlayback = true

        if let script = Self.shimSource() {
            config.userContentController.addUserScript(
                WKUserScript(source: script, injectionTime: .atDocumentStart, forMainFrameOnly: true)
            )
        }

        let view = WKWebView(frame: .zero, configuration: config)
        view.navigationDelegate = context.coordinator
        view.uiDelegate = context.coordinator
        view.scrollView.contentInsetAdjustmentBehavior = .never
        view.isOpaque = false
        view.backgroundColor = UIColor(red: 0.078, green: 0.078, blue: 0.078, alpha: 1)
        view.allowsBackForwardNavigationGestures = false
        #if DEBUG
        view.isInspectable = true
        #endif
        view.load(URLRequest(url: URL(string: "\(RequestRoute.scheme)://\(RequestRoute.host)/")!))
        return view
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    /// Readability.js (staged from npm) followed by ios-shim.js, as one document-start script.
    private static func shimSource() -> String? {
        guard
            let r = Bundle.main.url(forResource: "Readability", withExtension: "js"),
            let s = Bundle.main.url(forResource: "ios-shim", withExtension: "js"),
            let readability = try? String(contentsOf: r, encoding: .utf8),
            let shim = try? String(contentsOf: s, encoding: .utf8)
        else { return nil }
        return readability + "\n;\n" + shim
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        let schemeHandler = NoozSchemeHandler()

        /// Everything that is not the bundled reader opens in the system browser: the
        /// reader stays on nooz://app and never navigates a feed's page inside itself.
        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = action.request.url else { return decisionHandler(.cancel) }
            if url.scheme == RequestRoute.scheme || url.scheme == "about" { return decisionHandler(.allow) }
            if action.navigationType == .linkActivated || action.targetFrame == nil,
               url.scheme == "https" || url.scheme == "http" {
                UIApplication.shared.open(url)
            }
            decisionHandler(.cancel)
        }

        /// target=_blank / window.open: same rule, no new web view.
        func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration,
                     for action: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
            if let url = action.request.url, url.scheme == "https" || url.scheme == "http" {
                UIApplication.shared.open(url)
            }
            return nil
        }
    }
}
