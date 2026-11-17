import XCTest
@testable import NoozCore

final class HostGuardTests: XCTestCase {
    private struct Vectors: Decodable {
        struct Case: Decodable { let host: String; let blocked: Bool }
        let cases: [Case]
    }

    func testSharedVectors() throws {
        let url = try XCTUnwrap(Bundle.module.url(forResource: "host-guard-vectors", withExtension: "json"))
        let vectors = try JSONDecoder().decode(Vectors.self, from: Data(contentsOf: url))
        XCTAssertGreaterThan(vectors.cases.count, 15)
        for c in vectors.cases {
            XCTAssertEqual(HostGuard.isBlocked(c.host), c.blocked, c.host)
        }
    }
}

final class RouteTests: XCTestCase {
    private func route(_ s: String) -> RequestRoute { RequestRoute.route(for: URL(string: s)!) }

    func testRootAndIndex() {
        XCTAssertEqual(route("nooz://app/"), .asset(path: "index.html"))
        XCTAssertEqual(route("nooz://app/index.html"), .asset(path: "index.html"))
        XCTAssertEqual(route("nooz://app/js/app.js"), .asset(path: "js/app.js"))
    }

    func testFeedRoute() {
        let r = route("nooz://app/api/feed?url=" + "https%3A%2F%2Ffeeds.bbci.co.uk%2Fnews%2Frss.xml")
        XCTAssertEqual(r, .feed(target: URL(string: "https://feeds.bbci.co.uk/news/rss.xml")!))
    }

    func testPageRoute() {
        let r = route("nooz://app/api/page?url=https%3A%2F%2Fexample.com%2Fa")
        XCTAssertEqual(r, .page(target: URL(string: "https://example.com/a")!))
    }

    func testRejections() {
        XCTAssertEqual(route("nooz://app/api/feed"), .reject(status: 400, message: "Missing url parameter"))
        XCTAssertEqual(route("nooz://app/api/feed?url=ftp%3A%2F%2Fexample.com%2Ff"), .reject(status: 400, message: "Unsupported protocol"))
        XCTAssertEqual(route("nooz://app/api/feed?url=http%3A%2F%2F192.168.1.1%2Frss"), .reject(status: 400, message: "Refused: internal address"))
        XCTAssertEqual(route("nooz://app/api/page?url=http%3A%2F%2Flocalhost%3A8080%2F"), .reject(status: 400, message: "Refused: internal address"))
        XCTAssertEqual(route("nooz://app/api/page?url=http%3A%2F%2F%5B%3A%3A1%5D%2F"), .reject(status: 400, message: "Refused: internal address"))
        XCTAssertEqual(route("nooz://app/api/unknown"), .reject(status: 404, message: "Not found"))
        XCTAssertEqual(route("https://app/index.html"), .reject(status: 404, message: "Not found"))
        XCTAssertEqual(route("nooz://other/index.html"), .reject(status: 404, message: "Not found"))
    }

    func testTraversalIsRefused() {
        XCTAssertNil(AssetPath.sanitize("/../secret"))
        XCTAssertNil(AssetPath.sanitize("/js/%2e%2e/%2e%2e/x"))
        XCTAssertNil(AssetPath.sanitize("/a\\b"))
        XCTAssertEqual(AssetPath.sanitize("/fonts/pt_serif_regular.ttf"), "fonts/pt_serif_regular.ttf")
        XCTAssertEqual(AssetPath.sanitize("/views/"), "views/index.html")
    }

    func testMime() {
        XCTAssertEqual(AssetPath.mimeType(for: "js/app.js"), "text/javascript; charset=utf-8")
        XCTAssertEqual(AssetPath.mimeType(for: "manifest.webmanifest"), "application/json; charset=utf-8")
        XCTAssertEqual(AssetPath.mimeType(for: "fonts/x.ttf"), "font/ttf")
        XCTAssertEqual(AssetPath.mimeType(for: "x.bin"), "application/octet-stream")
    }
}

final class EnvelopeTests: XCTestCase {
    func testSuccessAndFailureShapes() throws {
        let ok = try JSONSerialization.jsonObject(with: PageEnvelope.success(html: "<p>\"hi\"</p>", finalUrl: URL(string: "https://e.com/a")!)) as! [String: Any]
        XCTAssertEqual(ok["html"] as? String, "<p>\"hi\"</p>")
        XCTAssertEqual(ok["finalUrl"] as? String, "https://e.com/a")
        let bad = try JSONSerialization.jsonObject(with: PageEnvelope.failure("Upstream timed out")) as! [String: Any]
        XCTAssertEqual(bad["error"] as? String, "Upstream timed out")
        XCTAssertTrue(bad["html"] is NSNull)
    }

    func testCap() {
        let big = Data(repeating: 0x61, count: 5_000_000)
        XCTAssertEqual(PageEnvelope.cappedString(big).utf8.count, 3_000_000)
    }
}
