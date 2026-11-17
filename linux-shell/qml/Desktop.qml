// Desktop.qml -- Nooz for Linux desktop: a window around the same QtWebEngine shell the
// Ubuntu Touch click uses (../ubuntu-touch/qml/Main.qml, copied verbatim into this layout by
// scripts/build-tarball.sh; there is one shell source, not two).
//
// THIN CLIENT, NOT A PORT OF THE ANDROID APP (plan R12; see README.md). The reader is the
// unchanged web reader (web/) loaded from file://; feeds and article pages are fetched
// natively over the console-line bridge, with no proxy hop through anyone's server.
//
// What this file adds over Main.qml: a resizable top-level window, a stable data directory
// name, and a download handler (OPML export, clipping PNG), which Main.qml has no use for on
// the phone. EVIDENCE: see README.md.
import QtQuick 2.9
import QtQuick.Window 2.2
import QtWebEngine 1.7

Window {
    id: win
    visible: true
    width: 1100
    height: 780
    minimumWidth: 360
    minimumHeight: 480
    title: "Nooz"
    color: "#000000"

    property alias webView: shell.webView          // for tests/smoke/Smoke.qml
    property string lastSaved: ""

    // qmlscene would otherwise name the data directory "qmlscene". Evaluated while the
    // window's properties are initialised, before the shell (and its profile) is created.
    // The reader's IndexedDB therefore lives under ~/.local/share/nooz/QtWebEngine/nooz.
    readonly property bool identityApplied: {
        Qt.application.name = "nooz"
        Qt.application.organization = ""
        return true
    }

    Main {
        id: shell
        anchors.fill: parent
    }

    // The reader exports through <a download> on blob:/data: URLs it made itself. Save
    // those to the profile's download folder (default ~/Downloads) and refuse anything
    // else: a remote URL asking for a download is not something the reader initiates.
    Connections {
        target: shell.webView.profile
        onDownloadRequested: {
            var u = download.url.toString()
            if (u.indexOf("blob:") !== 0 && u.indexOf("data:") !== 0) {
                download.cancel()
                return
            }
            download.accept()
        }
        onDownloadFinished: {
            if (download.state === WebEngineDownloadItem.DownloadCompleted) {
                win.lastSaved = download.path
                toastTimer.restart()
            }
        }
    }

    Rectangle {
        visible: win.lastSaved !== ""
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 24
        width: Math.min(parent.width - 32, savedLabel.implicitWidth + 32)
        height: savedLabel.implicitHeight + 20
        color: "#1d1d1d"
        border.color: "#8a8a8a"
        border.width: 1
        Text {
            id: savedLabel
            anchors.centerIn: parent
            width: parent.width - 32
            elide: Text.ElideMiddle
            color: "#f0f0f0"
            font.pixelSize: 14
            text: "Saved: " + win.lastSaved
        }
    }
    Timer { id: toastTimer; interval: 6000; onTriggered: win.lastSaved = "" }
}
