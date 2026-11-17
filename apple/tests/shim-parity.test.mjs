import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import test from 'node:test';

// Parity between the in-page iOS shim (apple/Nooz/Resources/ios-shim.js) and the
// server it replaces (web/api/article.js): same HTML in, same JSON out.
const require = createRequire(import.meta.url);
const web = createRequire(new URL('../../web/package.json', import.meta.url));
const { parseHTML } = web('linkedom');
const { Readability } = web('@mozilla/readability');
const shim = require('../Nooz/Resources/ios-shim.js');
const handler = require('../../web/api/article.js');

const BODY = '<p>' + 'The council met on Tuesday and adopted the harbour plan after a long debate. '.repeat(8) + '</p>';
const PAGES = {
  'https://news.example/a/1': `<html><head><title>Harbour plan adopted</title>
    <meta property="og:image" content="/img/lead.jpg"></head>
    <body><nav>menu</nav><article><h1>Harbour plan adopted</h1>${BODY}${BODY}<img src="/img/inline.png"></article></body></html>`,
  'https://news.example/a/2': `<html><head><title>No og image</title></head><body><article>${BODY}${BODY}<img src="https://cdn.example/x.jpg"></article></body></html>`,
  'https://news.example/a/3': `<html><body><p>too short</p></body></html>`,
};

async function viaServer(url, html) {
  globalThis.fetch = async () => ({
    ok: true,
    headers: { get: () => 'text/html; charset=utf-8' },
    body: null,
    text: async () => html,
  });
  let out;
  const res = {
    setHeader() {},
    status(code) { this.code = code; return this; },
    json(b) { out = b; },
    send() {},
  };
  await handler({ method: 'GET', query: { url } }, res);
  return out;
}

for (const [url, html] of Object.entries(PAGES)) {
  test(`shim output equals api/article.js output for ${url}`, async () => {
    const server = await viaServer(url, html);
    const local = shim.extract(html, url, (h) => parseHTML(h).document, Readability);
    assert.deepEqual(local, server);
  });
}

test('install(): /api/article is answered from /api/page; other fetches pass through', async () => {
  const calls = [];
  const win = {
    fetch: async (u) => {
      calls.push(u);
      if (String(u).startsWith('/api/page')) {
        return new Response(JSON.stringify({ html: PAGES['https://news.example/a/1'], finalUrl: 'https://news.example/a/1' }));
      }
      return new Response('plain');
    },
    DOMParser: class { parseFromString(h) { return parseHTML(h).document; } },
  };
  shim.install(win, Readability);
  const r = await win.fetch('/api/article?url=' + encodeURIComponent('https://news.example/a/1'));
  const body = await r.json();
  assert.equal(r.status, 200);
  assert.ok(body.html.includes('harbour plan'));
  assert.equal(body.leadImage, 'https://news.example/img/lead.jpg');
  assert.equal(await (await win.fetch('/i18n/en.json')).text(), 'plain');
  assert.deepEqual(calls.filter((c) => String(c).startsWith('/api/article')), []);
});

test('install(): native fetch failure becomes a 200 {error, html:null}, like the server', async () => {
  const win = {
    fetch: async (u) => { if (String(u).startsWith('/api/page')) return new Response(JSON.stringify({ error: 'Upstream timed out', html: null })); return new Response('x'); },
    DOMParser: class { parseFromString(h) { return parseHTML(h).document; } },
  };
  shim.install(win, Readability);
  const r = await win.fetch('/api/article?url=' + encodeURIComponent('https://news.example/slow'));
  assert.equal(r.status, 200);
  assert.deepEqual(await r.json(), { error: 'Upstream timed out', html: null });
});
