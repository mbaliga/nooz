import Foundation

/// The JSON the native /api/page route returns to the in-page shim (ios-shim.js).
/// Failures use the same shape as web/api/article.js: HTTP 200 with `{error, html:null}`.
public enum PageEnvelope {
    /// Default cap, same as web/api/article.js MAX_BYTES.
    public static let maxBytes = 3_000_000

    public static func success(html: String, finalUrl: URL) -> Data {
        encode(["html": html, "finalUrl": finalUrl.absoluteString])
    }

    public static func failure(_ message: String) -> Data {
        // JSONSerialization needs NSNull for a JSON null.
        encode(["error": message, "html": NSNull()])
    }

    /// Truncates to `maxBytes` and decodes as UTF-8, replacing invalid sequences.
    public static func cappedString(_ data: Data, maxBytes: Int = PageEnvelope.maxBytes) -> String {
        String(decoding: data.prefix(maxBytes), as: UTF8.self)
    }

    private static func encode(_ object: [String: Any]) -> Data {
        (try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys])) ?? Data("{\"error\":\"Encoding failed\",\"html\":null}".utf8)
    }
}
