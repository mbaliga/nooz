import Foundation

/// What the shell's scheme handler should do with a request to `nooz://app/...`.
public enum RequestRoute: Equatable {
    /// Serve a bundled file at this bundle-relative path (inside the `web` folder).
    case asset(path: String)
    /// Natively fetch this feed URL and return the body (replaces web/api/feed.js).
    case feed(target: URL)
    /// Natively fetch this page and return `{html, finalUrl}` JSON for the in-page
    /// Readability shim (replaces the network half of web/api/article.js).
    case page(target: URL)
    /// Refuse with this HTTP status and JSON `{error}` message.
    case reject(status: Int, message: String)

    public static let scheme = "nooz"
    public static let host = "app"

    public static func route(for url: URL) -> RequestRoute {
        guard url.scheme == scheme, url.host == host else {
            return .reject(status: 404, message: "Not found")
        }
        let path = url.path
        switch path {
        case "/api/feed", "/api/feed/":
            return target(from: url).map { .feed(target: $0) } ?? .reject(status: 400, message: lastError(url))
        case "/api/page", "/api/page/":
            return target(from: url).map { .page(target: $0) } ?? .reject(status: 400, message: lastError(url))
        default:
            if path.hasPrefix("/api/") { return .reject(status: 404, message: "Not found") }
            guard let clean = AssetPath.sanitize(path) else {
                return .reject(status: 404, message: "Not found")
            }
            return .asset(path: clean)
        }
    }

    /// Validates `?url=`: present, parseable, http(s) only, not an internal address. Same
    /// checks and messages as web/api/feed.js.
    static func validateTarget(_ raw: String?) -> Result<URL, TargetError> {
        guard let raw, !raw.isEmpty else { return .failure(.missing) }
        guard let parsed = URL(string: raw), let scheme = parsed.scheme?.lowercased(), parsed.host != nil || scheme == "http" || scheme == "https" else {
            return .failure(.invalid)
        }
        guard scheme == "http" || scheme == "https" else { return .failure(.unsupportedProtocol) }
        guard let host = parsed.host, !host.isEmpty else { return .failure(.invalid) }
        if HostGuard.isBlocked(host.trimmingCharacters(in: CharacterSet(charactersIn: "[]"))) {
            return .failure(.internalAddress)
        }
        return .success(parsed)
    }

    public enum TargetError: String, Error, Equatable {
        case missing = "Missing url parameter"
        case invalid = "Invalid url"
        case unsupportedProtocol = "Unsupported protocol"
        case internalAddress = "Refused: internal address"
    }

    private static func queryValue(_ url: URL) -> String? {
        URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?.first(where: { $0.name == "url" })?.value
    }

    private static func target(from url: URL) -> URL? {
        if case .success(let u) = validateTarget(queryValue(url)) { return u }
        return nil
    }

    private static func lastError(_ url: URL) -> String {
        if case .failure(let e) = validateTarget(queryValue(url)) { return e.rawValue }
        return "Invalid url"
    }
}
