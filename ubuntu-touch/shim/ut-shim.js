// ut-shim.js -- Ubuntu Touch shell only. Injected by Main.qml at DocumentCreation, in the
// main world, BEFORE Readability.js and article-shim.js (a staged copy of
// apple/Nooz/Resources/ios-shim.js, the single source of the article-extraction code).
//
// The web reader (web/) calls /api/feed and /api/article, two serverless helpers. In the
// click there is no server and the page is served from file://, so:
//   * /api/feed?url=  and  /api/page?url=  are answered by the QML host over a
//     console-line bridge (qml/js/HostBridge.js) using native XMLHttpRequest: no CORS,
//     no proxy hop through anyone's server.
//   * /api/article?url= is then answered by article-shim.js (Readability in the page),
//     which asks for /api/page -- i.e. this file. web/ is not modified.
//   * Chromium's fetch() refuses file: URLs, and the reader fetches i18n/*.json that way,
//     so same-origin relative reads fall back to XMLHttpRequest when running from file:.
(function (root) {
  'use strict';
  var PREFIX = 'NOOZ-HOST/1 ';
  var PAGE_WAIT_MS = 30000; // the host enforces its own, shorter, upstream deadlines

  function queryUrl(raw, route) {
    var m = new RegExp('(?:^|/)api/' + route + '\\?url=([^&]+)').exec(raw);
    if (!m) return null;
    try { return decodeURIComponent(m[1]); } catch (_e) { return ''; }
  }

  function jsonResponse(status, body) {
    return new Response(JSON.stringify(body), { status: status, headers: { 'Content-Type': 'application/json' } });
  }

  // Turns a host reply into the Response the web reader expects. Pure, for the tests.
  function responseFor(op, reply) {
    if (!reply) return op === 'feed' ? jsonResponse(502, { error: 'Upstream unreachable' })
                                     : jsonResponse(200, { error: 'Upstream unreachable', html: null });
    if (op === 'feed') {
      if (!reply.ok) return jsonResponse(reply.status || 502, { error: reply.error || 'Upstream unreachable' });
      return new Response(reply.body, { status: 200, headers: { 'Content-Type': reply.contentType || 'application/xml; charset=utf-8' } });
    }
    return jsonResponse(200, reply.page || { error: 'Upstream unreachable', html: null });
  }

  function createClient(win) {
    var nextId = 1;
    var waiting = {};
    win.__noozHostReply = function (json) {
      var reply;
      try { reply = JSON.parse(json); } catch (_e) { return; }
      var w = reply && waiting[reply.id];
      if (!w) return;
      delete waiting[reply.id];
      win.clearTimeout(w.timer);
      w.resolve(reply);
    };
    return function call(op, url) {
      return new Promise(function (resolve) {
        var id = nextId++;
        var timer = win.setTimeout(function () { delete waiting[id]; resolve(null); }, PAGE_WAIT_MS);
        waiting[id] = { resolve: resolve, timer: timer };
        win.console.log(PREFIX + JSON.stringify({ id: id, op: op, url: url }));
      });
    };
  }

  // fetch() cannot read file: URLs; XHR can. Same-origin relative reads only.
  function xhrFetch(win, url) {
    return new Promise(function (resolve, reject) {
      var xhr = new win.XMLHttpRequest();
      xhr.open('GET', url);
      xhr.onload = function () {
        // file: reads report status 0 on success in Chromium.
        var status = xhr.status === 0 && xhr.responseText ? 200 : xhr.status;
        resolve(new Response(xhr.responseText, { status: status === 0 ? 404 : status }));
      };
      xhr.onerror = function () { reject(new TypeError('Failed to fetch')); };
      xhr.send();
    });
  }

  function install(win) {
    var realFetch = win.fetch.bind(win);
    var call = createClient(win);
    win.fetch = function (input, init) {
      var raw = typeof input === 'string' ? input : (input && input.url) || '';
      var feedUrl = queryUrl(raw, 'feed');
      if (feedUrl !== null) return call('feed', feedUrl).then(function (r) { return responseFor('feed', r); });
      var pageUrl = queryUrl(raw, 'page');
      if (pageUrl !== null) return call('page', pageUrl).then(function (r) { return responseFor('page', r); });
      if (win.location && win.location.protocol === 'file:' && !/^[a-z][a-z0-9+.-]*:/i.test(raw)) {
        return xhrFetch(win, raw);
      }
      return realFetch(input, init);
    };
  }

  var api = { queryUrl: queryUrl, responseFor: responseFor, createClient: createClient, install: install, PREFIX: PREFIX };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (root && root.document && root.fetch && !root.__noozUtShimInstalled) {
    root.__noozUtShimInstalled = true;
    install(root);
  }
})(typeof window !== 'undefined' ? window : undefined);
