import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import test from 'node:test';
import { loadBridge } from './load.mjs';

// The whole page-side chain with a fake host: reader fetch('/api/article') -> article-shim
// (staged copy of apple/.../ios-shim.js) -> fetch('/api/page') -> ut-shim -> console line ->
// HostBridge.handle -> fake transport -> window.__noozHostReply -> Response -> Readability.
const require = createRequire(import.meta.url);
const web = createRequire(new URL('../../web/package.json', import.meta.url));
const { parseHTML } = web('linkedom');
const { Readability } = web('@mozilla/readability');
const utShim = require('../shim/ut-shim.js');
const articleShim = require('../../apple/Nooz/Resources/ios-shim.js');
const B = loadBridge();

const BODY = '<p>' + 'The council met on Tuesday and adopted the harbour plan after a long debate. '.repeat(8) + '</p>';
const PAGES = {
  'https://news.example/a/1': { contentType: 'text/html; charset=utf-8', body: `<html><head><title>Harbour plan adopted</title></head><body><article><h1>Harbour plan adopted</h1>${BODY}${BODY}</article></body></html>` },
  'https://news.example/feed.xml': { contentType: 'application/rss+xml', body: '<rss><channel><title>T</title></channel></rss>' },
};

function makeWindow(log = []) {
  const win = {
    location: { protocol: 'file:' },
    setTimeout, clearTimeout,
    DOMParser: class { parseFromString(h) { return parseHTML(h).document; } },
    fetch: async (u) => { log.push(['realFetch', u]); return new Response('real', { status: 200 }); },
    console: {
      log(line) {
        const req = B.parseConsole(line);
        if (!req) return;
        B.handle(req, (url, _o, cb) => {
          const p = PAGES[url];
          setImmediate(() => cb(p ? { status: 200, ...p } : { status: 404, body: '' }));
        }, (reply) => {
          const js = B.replyScript(reply);
          setImmediate(() => new Function('window', js)(win));
        });
      },
    },
    XMLHttpRequest: class {
      open(_m, url) { this.url = url; }
      send() { setImmediate(() => { if (this.url === 'i18n/en.json') { this.status = 0; this.responseText = '{"a":1}'; this.onload(); } else { this.status = 0; this.responseText = ''; this.onerror(); } }); }
    },
  };
  utShim.install(win);
  articleShim.install(win, Readability);
  return win;
}

test('feed: native host answers /api/feed with the upstream body and content type', async () => {
  const win = makeWindow();
  const res = await win.fetch('/api/feed?url=' + encodeURIComponent('https://news.example/feed.xml'));
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type'), /rss/);
  assert.match(await res.text(), /<rss>/);
});

test('feed: upstream 404 and refused target surface as HTTP error with {error}', async () => {
  const win = makeWindow();
  let res = await win.fetch('/api/feed?url=' + encodeURIComponent('https://news.example/missing'));
  assert.equal(res.status, 502);
  assert.deepEqual(await res.json(), { error: 'Upstream HTTP 404' });
  res = await win.fetch('/api/feed?url=' + encodeURIComponent('https://10.0.0.1/x'));
  assert.equal(res.status, 400);
  assert.deepEqual(await res.json(), { error: 'Refused: internal address' });
});

test('article: end-to-end extraction through the bridge', async () => {
  const win = makeWindow();
  const res = await win.fetch('/api/article?url=' + encodeURIComponent('https://news.example/a/1'));
  const out = await res.json();
  assert.equal(out.title, 'Harbour plan adopted');
  assert.match(out.html, /harbour plan/);
});

test('article: a failing page is a 200 {error, html:null}', async () => {
  const win = makeWindow();
  const res = await win.fetch('/api/article?url=' + encodeURIComponent('https://news.example/nope'));
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { error: 'Upstream HTTP 404', html: null });
});

test('file: origin reads relative URLs via XHR; absolute URLs still use the real fetch', async () => {
  const log = [];
  const win = makeWindow(log);
  const res = await win.fetch('i18n/en.json');
  assert.deepEqual(await res.json(), { a: 1 });
  await assert.rejects(win.fetch('i18n/missing.json'), TypeError);
  await win.fetch('https://other.example/img.png');
  assert.deepEqual(log, [['realFetch', 'https://other.example/img.png']]);
});

test('queryUrl ignores lookalikes and survives bad escapes', () => {
  assert.equal(utShim.queryUrl('/api/feedx?url=a', 'feed'), null);
  assert.equal(utShim.queryUrl('/api/feed?url=%E0%A4%A', 'feed'), '');
  assert.equal(utShim.queryUrl('/api/feed?url=https%3A%2F%2Fa.b%2Fc', 'feed'), 'https://a.b/c');
});
