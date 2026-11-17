// Headless smoke harness. tests/smoke.sh copies this next to Main.qml inside an INSTALLED
// click layout and runs it under qmlscene with QT_QPA_PLATFORM=offscreen. It lets the
// reader boot, probes the page, drives the native bridge once with a target the host must
// refuse (no network needed), and prints one SMOKE-RESULT line.
// Proves only: the shell loads under desktop Qt 5 on x86_64 and the bridge round-trips.
// It says nothing about a phone.
import QtQuick 2.9

Main {
    id: m
    Timer {
        interval: 9000; running: true
        onTriggered: {
            m.webView.runJavaScript(
                "window.__probe = null;" +
                "fetch('/api/feed?url=' + encodeURIComponent('https://127.0.0.1/x'))" +
                ".then(function (r) { return r.text().then(function (t) { window.__probe = r.status + ' ' + t; }); });" +
                "JSON.stringify({ title: document.title, hasApp: !!document.getElementById('app')," +
                " bootGone: !document.querySelector('.nooz-boot'), utShim: !!window.__noozUtShimInstalled," +
                " articleShim: !!window.__noozShimInstalled, protocol: location.protocol })",
                function (state) { console.log("SMOKE-STATE " + state) })
        }
    }
    Timer {
        interval: 12000; running: true
        onTriggered: {
            m.webView.runJavaScript("String(window.__probe)", function (probe) {
                console.log("SMOKE-RESULT bridge-refusal=" + probe)
                Qt.quit()
            })
        }
    }
}
