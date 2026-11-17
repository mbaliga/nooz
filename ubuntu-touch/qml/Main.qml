// Main.qml -- Nooz for Ubuntu Touch: a QtWebEngine host for the web reader (web/).
//
// This is a THIN-CLIENT SHELL, not a port of the Android app (plan R12; see README.md):
// the page is the unchanged web reader, bundled in the click and loaded from file://.
// What the shell adds is the two things a page on file:// cannot do, over a console-line
// bridge (js/HostBridge.js + shim/ut-shim.js): fetch feeds and article pages natively
// (no CORS, no proxy through anyone's server), and keep external links out of the app.
//
// Deliberately plain QtQuick + QtWebEngine: no Lomiri.Components, so the same file runs
// under qmlscene on any Qt 5.12+ box (it is how this container smoke-tests it).
// EVIDENCE: see README.md. Nothing here has run on a phone.
import QtQuick 2.9
import QtWebEngine 1.7
import "js/HostBridge.js" as Bridge

Rectangle {
    id: root
    width: 360
    height: 720
    color: "#000000"

    // Staged by scripts/stage.sh into the click next to qml/.
    readonly property url appUrl: Qt.resolvedUrl("../www/index.html")
    readonly property string appBase: Qt.resolvedUrl("../www/").toString()

    property alias webView: view      // for tests/smoke/Smoke.qml
    property var tracker: Bridge.createTracker()
    property int seq: 0

    // ---- native fetch -----------------------------------------------------------
    // QML's XMLHttpRequest follows redirects, applies no CORS, and has no timeout of its
    // own (hence the tracker + Timer). It cannot report the post-redirect URL, so
    // finalUrl falls back to the requested URL (relative links on a redirected article
    // may resolve against the pre-redirect host: a known, documented gap).
    function transport(url, opts, cb) {
        var key = "r" + (++seq)
        var xhr = new XMLHttpRequest()
        var settled = false
        function settle(result) {
            if (settled) return
            settled = true
            tracker.remove(key)
            cb(result)
        }
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== XMLHttpRequest.DONE) return
            if (xhr.status === 0) { settle({ status: 0, error: "network" }); return }
            settle({ status: xhr.status,
                     contentType: xhr.getResponseHeader("Content-Type") || "",
                     body: xhr.responseText,
                     finalUrl: url })
        }
        tracker.add(key, Date.now() + opts.timeoutMs, function () {
            xhr.abort()
            settle({ status: 0, error: "timeout" })
        })
        xhr.open("GET", url)
        xhr.setRequestHeader("Accept", opts.accept)
        xhr.send()
    }

    Timer {
        interval: 1000
        repeat: true
        running: true
        onTriggered: root.tracker.sweep(Date.now())
    }

    function onBridgeMessage(message) {
        var req = Bridge.parseConsole(message)
        if (!req) return
        Bridge.handle(req, root.transport, function (reply) {
            view.runJavaScript(Bridge.replyScript(reply))
        })
    }

    // ---- page ---------------------------------------------------------------------
    WebEngineProfile {
        id: nooProfile
        storageName: "nooz"            // IndexedDB (the reader's store) lives under the app's data dir
        offTheRecord: false
        httpCacheType: WebEngineProfile.NoCache
        persistentCookiesPolicy: WebEngineProfile.NoPersistentCookies
    }

    WebEngineView {
        id: view
        anchors.fill: parent
        profile: nooProfile
        url: root.appUrl

        settings.javascriptCanOpenWindows: false
        settings.allowRunningInsecureContent: false
        settings.errorPageEnabled: false
        settings.pluginsEnabled: false
        settings.localStorageEnabled: true

        // One script, order fixed at staging time (scripts/stage.sh): ut-shim, Readability,
        // article-shim. QtWebEngine does not promise ordering across several user scripts.
        userScripts: [
            WebEngineScript { name: "nooz-shims"; sourceUrl: Qt.resolvedUrl("../shims/nooz-shims.js")
                injectionPoint: WebEngineScript.DocumentCreation; worldId: WebEngineScript.MainWorld }
        ]

        onJavaScriptConsoleMessage: root.onBridgeMessage(message)

        // Stay inside the bundled reader; anything else is handed to the system.
        onNavigationRequested: {
            var target = request.url.toString()
            if (target.indexOf(root.appBase) === 0) return        // our own pages and #hash routes
            request.action = WebEngineNavigationRequest.IgnoreRequest
            if (/^https?:\/\//i.test(target)) Qt.openUrlExternally(target)
        }
        onNewViewRequested: {
            var target = request.requestedUrl.toString()
            if (/^https?:\/\//i.test(target)) Qt.openUrlExternally(target)
        }
    }
}
