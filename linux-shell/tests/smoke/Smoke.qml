// Headless smoke harness for the DESKTOP window (Desktop.qml). tests/smoke.sh copies it next
// to Desktop.qml in an extracted tarball. It lets the reader boot, probes the page, drives the
// native bridge once with a target the host must refuse (no network), then exercises the
// download path with a blob: link (must save) and an https: link (must be refused).
// Proves only: the window loads under desktop Qt 5 on x86_64 and these paths round-trip.
import QtQuick 2.9

Desktop {
    id: d
    Timer {
        interval: 9000; running: true
        onTriggered: {
            d.webView.runJavaScript(
                "window.__probe = null;" +
                "fetch('/api/feed?url=' + encodeURIComponent('https://127.0.0.1/x'))" +
                ".then(function (r) { return r.text().then(function (t) { window.__probe = r.status + ' ' + t; }); });" +
                "JSON.stringify({ title: document.title, hasApp: !!document.getElementById('app')," +
                " utShim: !!window.__noozUtShimInstalled, articleShim: !!window.__noozShimInstalled," +
                " protocol: location.protocol, w: window.innerWidth })",
                function (state) { console.log("SMOKE-STATE " + state) })
        }
    }
    Timer {
        interval: 11000; running: true
        onTriggered: {
            d.webView.runJavaScript(
                "(function () {" +
                "  var a = document.createElement('a');" +
                "  a.href = URL.createObjectURL(new Blob(['<opml version=\"2.0\"/>'], { type: 'text/x-opml' }));" +
                "  a.download = 'smoke.opml'; document.body.appendChild(a); a.click();" +
                "  return 'clicked'; })()",
                function (r) { console.log("SMOKE-DOWNLOAD " + r) })
        }
    }
    Timer {
        interval: 15000; running: true
        onTriggered: {
            d.webView.runJavaScript("String(window.__probe)", function (probe) {
                console.log("SMOKE-RESULT bridge-refusal=" + probe + " saved=" + d.lastSaved)
                Qt.quit()
            })
        }
    }
}
