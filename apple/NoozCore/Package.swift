// swift-tools-version:5.9
// NoozCore: the Foundation-only logic of the iOS shell (host guard, request routing,
// asset paths, MIME types, size cap). No UIKit, no WebKit, no networking, so it builds
// and tests on Linux as well as on macOS. The app target (apple/Nooz) depends on it.
import PackageDescription

let package = Package(
    name: "NoozCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "NoozCore", targets: ["NoozCore"])],
    targets: [
        .target(name: "NoozCore"),
        .testTarget(
            name: "NoozCoreTests",
            dependencies: ["NoozCore"],
            resources: [.copy("host-guard-vectors.json")]
        ),
    ]
)
