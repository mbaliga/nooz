import Foundation

/// Maps a request path to a bundle-relative file and its MIME type.
public enum AssetPath {
    /// Returns nil for anything that tries to leave the web folder.
    public static func sanitize(_ requestPath: String) -> String? {
        var path = requestPath
        if path.isEmpty || path == "/" { return "index.html" }
        if path.hasSuffix("/") { path += "index.html" }
        // Percent-decoding happens before the traversal check so %2e%2e cannot sneak by.
        guard let decoded = path.removingPercentEncoding else { return nil }
        let segments = decoded.split(separator: "/", omittingEmptySubsequences: true).map(String.init)
        if segments.isEmpty { return "index.html" }
        if segments.contains(where: { $0 == ".." || $0 == "." || $0.contains("\\") || $0.contains("\0") }) { return nil }
        return segments.joined(separator: "/")
    }

    public static func mimeType(for path: String) -> String {
        switch (path as NSString).pathExtension.lowercased() {
        case "html", "htm": return "text/html; charset=utf-8"
        case "js", "mjs": return "text/javascript; charset=utf-8"
        case "css": return "text/css; charset=utf-8"
        case "json", "webmanifest": return "application/json; charset=utf-8"
        case "png": return "image/png"
        case "svg": return "image/svg+xml"
        case "ttf": return "font/ttf"
        case "woff2": return "font/woff2"
        default: return "application/octet-stream"
        }
    }
}
