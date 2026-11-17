import Foundation
import WebKit
import NoozCore

/// Answers every request the page makes to nooz://app/ :
///   * files  -> from the app bundle's `web` folder
///   * /api/feed?url=  -> a native URLSession GET of the feed (no CORS, no proxy hop)
///   * /api/page?url=  -> the same for an article page, size-capped, as JSON for ios-shim.js
///
/// Network use is exactly what the user asked for: the feed or article URL the reader is
/// asked to open. No other host is ever contacted by native code, and nothing is sent about
/// the user. The session is ephemeral (no cookies, no URL cache, no credential storage).
final class NoozSchemeHandler: NSObject, WKURLSchemeHandler {
    private static let userAgent = "Mozilla/5.0 (compatible; NoozReader/1.0; +https://asystemofcells.com/nooz/read)"
    private static let feedTimeout: TimeInterval = 15
    private static let pageTimeout: TimeInterval = 8

    private let session: URLSession = {
        let c = URLSessionConfiguration.ephemeral
        c.httpCookieStorage = nil
        c.urlCache = nil
        c.requestCachePolicy = .reloadIgnoringLocalCacheData
        return URLSession(configuration: c)
    }()

    // WebKit raises if a task is answered after stop(); track liveness under a lock.
    private let lock = NSLock()
    private var live: [ObjectIdentifier: URLSessionTask?] = [:]

    func webView(_ webView: WKWebView, start task: any WKURLSchemeTask) {
        let key = ObjectIdentifier(task as AnyObject)
        lock.lock(); live[key] = .some(nil); lock.unlock()

        guard let url = task.request.url else { return finish(task, key, status: 400, json: ["error": "Invalid url"]) }
        switch RequestRoute.route(for: url) {
        case .asset(let path):
            serveAsset(task, key, path: path)
        case .reject(let status, let message):
            finish(task, key, status: status, json: ["error": message])
        case .feed(let target):
            fetch(task, key, target: target, timeout: Self.feedTimeout,
                  accept: "application/rss+xml, application/atom+xml, application/xml, text/xml, */*") { data, response in
                guard (200..<300).contains(response.statusCode) else {
                    return self.finish(task, key, status: 502, json: ["error": "Upstream HTTP \(response.statusCode)"])
                }
                let type = response.value(forHTTPHeaderField: "Content-Type") ?? "application/xml; charset=utf-8"
                self.finish(task, key, status: 200, body: data, contentType: type)
            } failure: { timedOut in
                self.finish(task, key, status: 502, json: ["error": timedOut ? "Upstream timed out" : "Upstream unreachable"])
            }
        case .page(let target):
            // Per-item failures are HTTP 200 {error, html:null}, the contract api/article.js keeps.
            fetch(task, key, target: target, timeout: Self.pageTimeout,
                  accept: "text/html,application/xhtml+xml") { data, response in
                guard (200..<300).contains(response.statusCode) else {
                    return self.finish(task, key, status: 200, body: PageEnvelope.failure("Upstream HTTP \(response.statusCode)"), contentType: "application/json")
                }
                let type = (response.value(forHTTPHeaderField: "Content-Type") ?? "").lowercased()
                guard type.isEmpty || type.contains("html") else {
                    return self.finish(task, key, status: 200, body: PageEnvelope.failure("Not an HTML page"), contentType: "application/json")
                }
                let html = PageEnvelope.cappedString(data)
                self.finish(task, key, status: 200,
                            body: PageEnvelope.success(html: html, finalUrl: response.url ?? target),
                            contentType: "application/json")
            } failure: { timedOut in
                self.finish(task, key, status: 200, body: PageEnvelope.failure(timedOut ? "Upstream timed out" : "Upstream unreachable"), contentType: "application/json")
            }
        }
    }

    func webView(_ webView: WKWebView, stop task: any WKURLSchemeTask) {
        let key = ObjectIdentifier(task as AnyObject)
        lock.lock()
        let inflight = live.removeValue(forKey: key) ?? nil
        lock.unlock()
        inflight?.cancel()
    }

    // MARK: - assets

    private func serveAsset(_ task: any WKURLSchemeTask, _ key: ObjectIdentifier, path: String) {
        guard
            let root = Bundle.main.url(forResource: "web", withExtension: nil),
            case let file = root.appendingPathComponent(path),
            file.standardizedFileURL.path.hasPrefix(root.standardizedFileURL.path),
            let data = try? Data(contentsOf: file)
        else { return finish(task, key, status: 404, json: ["error": "Not found"]) }
        finish(task, key, status: 200, body: data, contentType: AssetPath.mimeType(for: path))
    }

    // MARK: - network

    private func fetch(_ task: any WKURLSchemeTask, _ key: ObjectIdentifier, target: URL,
                       timeout: TimeInterval, accept: String,
                       success: @escaping (Data, HTTPURLResponse) -> Void,
                       failure: @escaping (_ timedOut: Bool) -> Void) {
        var request = URLRequest(url: target, timeoutInterval: timeout)
        request.setValue(accept, forHTTPHeaderField: "Accept")
        request.setValue(Self.userAgent, forHTTPHeaderField: "User-Agent")
        request.httpShouldHandleCookies = false
        let t = session.dataTask(with: request) { data, response, error in
            if let http = response as? HTTPURLResponse, let data {
                // A redirect may have landed on an internal address; refuse it as the pre-flight would.
                if let host = http.url?.host, HostGuard.isBlocked(host) { return failure(false) }
                success(data, http)
            } else {
                failure((error as? URLError)?.code == .timedOut)
            }
        }
        lock.lock(); live[key] = .some(t); lock.unlock()
        t.resume()
    }

    // MARK: - responses

    private func finish(_ task: any WKURLSchemeTask, _ key: ObjectIdentifier, status: Int, json: [String: String]) {
        let body = (try? JSONSerialization.data(withJSONObject: json)) ?? Data()
        finish(task, key, status: status, body: body, contentType: "application/json")
    }

    private func finish(_ task: any WKURLSchemeTask, _ key: ObjectIdentifier, status: Int, body: Data, contentType: String) {
        guard let url = task.request.url else { return }
        let headers = [
            "Content-Type": contentType,
            "Content-Length": String(body.count),
            "Cache-Control": "no-store",
        ]
        guard let response = HTTPURLResponse(url: url, statusCode: status, httpVersion: "HTTP/1.1", headerFields: headers) else { return }
        // WebKit calls start/stop on the main thread, and answering a task after stop()
        // raises an Objective-C exception, so the liveness check and the reply both run on
        // main with nothing in between.
        DispatchQueue.main.async { [self] in
            lock.lock()
            let stillLive = live.removeValue(forKey: key) != nil
            lock.unlock()
            guard stillLive else { return }
            task.didReceive(response)
            task.didReceive(body)
            task.didFinish()
        }
    }
}
