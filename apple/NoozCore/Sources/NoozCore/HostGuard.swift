import Foundation

/// Refuses obviously internal fetch targets. A line-for-line port of `isBlockedTarget` in
/// web/api/feed.js and web/api/article.js, pinned to the same vectors
/// (apple/shared/host-guard-vectors.json). Inside the iOS shell the fetch runs on the
/// phone, so this is a courtesy against a pasted `http://192.168.1.1/` source rather than
/// an SSRF defence for a server, but it is kept identical so the two never disagree.
///
/// Known gaps shared with the reference: a hostname that only resolves to an internal
/// address (DNS rebinding) is not detected, and the `fc`/`fd` prefix test also refuses
/// ordinary hostnames that start with those letters.
public enum HostGuard {
    public static func isBlocked(_ hostname: String) -> Bool {
        let lower = hostname.lowercased()
        if lower == "localhost" { return true }

        let parts = lower.split(separator: ".", omittingEmptySubsequences: false)
        if parts.count == 4, parts.allSatisfy({ (1...3).contains($0.count) && $0.allSatisfy(\.isASCIIDigit) }) {
            let a = Int(parts[0])!, b = Int(parts[1])!
            if a == 127 || a == 10 || a == 0 { return true }
            if a == 172 && (16...31).contains(b) { return true }
            if a == 192 && b == 168 { return true }
            if a == 169 && b == 254 { return true }
            return false
        }

        if lower == "::1" || lower.hasPrefix("fe80:") || lower.hasPrefix("fc") || lower.hasPrefix("fd") {
            return true
        }
        return false
    }
}

private extension Character {
    var isASCIIDigit: Bool { ("0"..."9").contains(self) }
}
