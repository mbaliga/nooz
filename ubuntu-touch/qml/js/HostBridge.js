.pragma library
// HostBridge.js -- the pure logic behind the Ubuntu Touch shell's native fetch bridge.
//
// No QML types are used here, so the same file runs under the QML engine (imported by
// Main.qml) and under node (ubuntu-touch/tests, which strips the `.pragma` line and
// evaluates it in a vm). All I/O is injected: the QML side passes an XMLHttpRequest
// transport; the tests pass a fake.
//
// Wire format (page -> host): the page logs one console line,
//     NOOZ-HOST/1 {"id":7,"op":"feed","url":"https://..."}
// (op is "feed" or "page"). The host answers by running window.__noozHostReply("<json>").
// The page half is ubuntu-touch/shim/ut-shim.js.
//
// Mirrors the privacy posture of apple/: https only, no userinfo, internal addresses
// refused, nothing sent anywhere except the feed/article URL the reader asked for.

var PREFIX = 'NOOZ-HOST/1 ';
var FEED_TIMEOUT_MS = 15000;      // web/api/feed.js TIMEOUT_MS
var PAGE_TIMEOUT_MS = 8000;       // web/api/article.js TIMEOUT_MS
var PAGE_MAX_BYTES = 3000000;     // web/api/article.js MAX_BYTES
var FEED_MAX_BYTES = 10000000;    // feed.js has no cap; a phone should not hold unbounded text
var MAX_URL_LENGTH = 2048;
var OPS = { feed: true, page: true };

// ---- URL + SSRF courtesy guard ---------------------------------------------

// QML's JS engine has no WHATWG URL, so parse just what we need. Returns null for
// anything that is not a plain http(s) URL with a host and no userinfo.
function parseUrl(url) {
  if (typeof url !== 'string' || url.length === 0 || url.length > MAX_URL_LENGTH) return null;
  if (/[\u0000- \u007f-\u009f\\]/.test(url)) return null; // control chars, spaces, backslashes
  var m = /^(https?):\/\/([^\/?#]*)([\s\S]*)$/i.exec(url);
  if (!m) return null;
  var authority = m[2];
  if (authority.indexOf('@') !== -1) return { scheme: m[1].toLowerCase(), userinfo: true, host: '', port: '', rest: m[3] };
  var host, port = '';
  if (authority.charAt(0) === '[') {
    var close = authority.indexOf(']');
    if (close < 0) return null;
    host = authority.substring(1, close);
    var after = authority.substring(close + 1);
    if (after !== '') { if (after.charAt(0) !== ':') return null; port = after.substring(1); }
  } else {
    var colon = authority.lastIndexOf(':');
    if (colon >= 0) { host = authority.substring(0, colon); port = authority.substring(colon + 1); }
    else host = authority;
  }
  if (host === '') return null;
  if (port !== '' && !/^\d{1,5}$/.test(port)) return null;
  return { scheme: m[1].toLowerCase(), userinfo: false, host: host.toLowerCase(), port: port, rest: m[3] };
}

// Line-for-line port of isBlockedTarget in web/api/feed.js (same vectors:
// apple/shared/host-guard-vectors.json) PLUS stricter checks the reference lacks:
// a trailing dot, and numeric/hex single-token hosts that URL parsers expand to an IPv4
// address (http://2130706433/ is 127.0.0.1). Deliberately stricter, never looser.
// Known gap shared with the reference: DNS rebinding (a name that only RESOLVES to an
// internal address) is not detected.
function isBlockedHost(hostname) {
  var lower = String(hostname).toLowerCase();
  if (lower.charAt(lower.length - 1) === '.') lower = lower.substring(0, lower.length - 1);
  if (lower === 'localhost' || /\.localhost$/.test(lower)) return true;

  var v4 = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(lower);
  if (v4) {
    var a = Number(v4[1]), b = Number(v4[2]);
    if (a === 127 || a === 10 || a === 0) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    if (a === 192 && b === 168) return true;
    if (a === 169 && b === 254) return true;
    return false;
  }
  // Extra: integer / hex / octal / short dotted forms of an IPv4 address.
  if (/^(0x[0-9a-f]+|\d+)(\.(0x[0-9a-f]+|\d+)){0,3}$/.test(lower)) return true;

  if (lower === '::1' || lower.indexOf('fe80:') === 0 || lower.indexOf('fc') === 0 || lower.indexOf('fd') === 0) {
    return true;
  }
  // Extra: IPv4-mapped / unspecified IPv6.
  if (lower === '::' || lower.indexOf('::ffff:') === 0) return true;
  return false;
}

// -> { ok: true, url } | { ok: false, error }
function checkTarget(url) {
  var p = parseUrl(url);
  if (!p) return { ok: false, error: 'Invalid url' };
  if (p.scheme !== 'https') return { ok: false, error: 'Unsupported protocol' }; // D-UT-2: no cleartext
  if (p.userinfo) return { ok: false, error: 'Invalid url' };
  if (isBlockedHost(p.host)) return { ok: false, error: 'Refused: internal address' };
  return { ok: true, url: url };
}

// ---- Wire --------------------------------------------------------------------

// Console line -> request or null. Anything malformed is ignored (the page can log anything).
function parseConsole(message) {
  if (typeof message !== 'string' || message.indexOf(PREFIX) !== 0) return null;
  var req;
  try { req = JSON.parse(message.substring(PREFIX.length)); } catch (e) { return null; }
  if (!req || typeof req !== 'object') return null;
  if (typeof req.id !== 'number' || !isFinite(req.id) || Math.floor(req.id) !== req.id) return null;
  if (typeof req.op !== 'string' || OPS[req.op] !== true) return null;
  if (typeof req.url !== 'string') return null;
  return { id: req.id, op: req.op, url: req.url };
}

// JS to run in the page to deliver a reply. The reply is double-encoded so no byte of
// feed content can break out of the string literal (U+2028/2029 are valid in JSON but
// were line terminators in pre-ES2019 JS).
function replyScript(reply) {
  var lit = JSON.stringify(JSON.stringify(reply))
    .replace(/\u2028/g, '\\u2028').replace(/\u2029/g, '\\u2029');
  return 'window.__noozHostReply && window.__noozHostReply(' + lit + ');';
}

// ---- Handler -------------------------------------------------------------------

function timeoutFor(op) { return op === 'feed' ? FEED_TIMEOUT_MS : PAGE_TIMEOUT_MS; }

function failureText(r) {
  if (r.error === 'timeout') return 'Upstream timed out';
  if (r.error) return 'Upstream unreachable';
  return 'Upstream HTTP ' + r.status;
}

// transport(url, {op, accept, timeoutMs}, cb) calls cb({status, contentType, body, error})
// exactly once: error is 'timeout' | 'network' | null; status 0 means no HTTP response.
// done(reply) is called exactly once with the reply object for the page.
// Reply shapes (what ut-shim.js turns back into the Response the web reader expects):
//   feed: {id, ok:true,  status:200, contentType, body}   | {id, ok:false, status, error}
//   page: {id, ok:true,  page:{html, finalUrl}}           | {id, ok:true, page:{error, html:null}}
// A feed failure is an HTTP error with a JSON {error} body (as web/api/feed.js);
// a page failure is a normal 200 {error, html:null} (as web/api/article.js).
function handle(req, transport, done) {
  var isFeed = req.op === 'feed';
  function fail(status, message) {
    done(isFeed ? { id: req.id, ok: false, status: status, error: message }
                : { id: req.id, ok: true, page: { error: message, html: null } });
  }
  var chk = checkTarget(req.url);
  if (!chk.ok) { fail(400, chk.error); return; }

  var finished = false;
  transport(chk.url, {
    op: req.op,
    accept: isFeed ? 'application/rss+xml, application/atom+xml, application/xml, text/xml, */*'
                   : 'text/html,application/xhtml+xml',
    timeoutMs: timeoutFor(req.op)
  }, function (r) {
    if (finished) return;
    finished = true;
    if (!r || r.error || r.status < 200 || r.status > 299 || typeof r.body !== 'string') {
      fail(502, failureText(r || { error: 'network' }));
      return;
    }
    if (isFeed) {
      if (r.body.length > FEED_MAX_BYTES) { fail(502, 'Upstream too large'); return; }
      done({ id: req.id, ok: true, status: 200,
             contentType: r.contentType || 'application/xml; charset=utf-8', body: r.body });
      return;
    }
    if (r.contentType && !/html|xml/i.test(r.contentType)) { fail(200, 'Not an HTML page'); return; }
    var html = r.body.length > PAGE_MAX_BYTES ? r.body.substring(0, PAGE_MAX_BYTES) : r.body;
    done({ id: req.id, ok: true, page: { html: html, finalUrl: r.finalUrl || chk.url } });
  });
}

// ---- Deadline tracker ----------------------------------------------------------
// QML's XMLHttpRequest has no timeout. Main.qml registers each in-flight request here
// and a 1 s Timer calls sweep(); overdue entries have their abort callback invoked.

function createTracker() {
  var pending = {};
  return {
    add: function (key, deadlineMs, abort) { pending[key] = { deadline: deadlineMs, abort: abort }; },
    remove: function (key) { delete pending[key]; },
    count: function () { return Object.keys(pending).length; },
    sweep: function (nowMs) {
      var expired = [];
      Object.keys(pending).forEach(function (k) {
        if (nowMs >= pending[k].deadline) expired.push(k);
      });
      expired.forEach(function (k) {
        var entry = pending[k];
        delete pending[k];
        entry.abort();
      });
      return expired;
    }
  };
}
