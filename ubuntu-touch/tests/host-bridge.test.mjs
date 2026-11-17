import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { loadBridge } from './load.mjs';

const B = loadBridge();
const vectors = JSON.parse(readFileSync(new URL('../../apple/shared/host-guard-vectors.json', import.meta.url), 'utf8'));

test('isBlockedHost agrees with the shared SSRF vectors (same file as web/api and NoozCore)', () => {
  for (const c of vectors.cases) assert.equal(B.isBlockedHost(c.host), c.blocked, `${c.host} blocked=${c.blocked}`);
});

test('isBlockedHost is stricter than the reference on expanded/mapped forms', () => {
  for (const h of ['2130706433', '0x7f000001', '127.1', '0177.0.0.1', 'localhost.', 'a.localhost', '::', '::ffff:127.0.0.1']) {
    assert.equal(B.isBlockedHost(h), true, h);
  }
  for (const h of ['example.com', '8.8.8.8', 'feeds.bbci.co.uk']) assert.equal(B.isBlockedHost(h), false, h);
});

test('checkTarget: https only, no userinfo, no internal hosts, bounded', () => {
  assert.equal(B.checkTarget('https://example.com/feed.xml').ok, true);
  assert.equal(B.checkTarget('https://[2001:db8::1]:8443/x').ok, true);
  assert.deepEqual({ ...B.checkTarget('http://example.com/') }, { ok: false, error: 'Unsupported protocol' });
  assert.equal(B.checkTarget('https://user:pw@example.com/').error, 'Invalid url');
  assert.equal(B.checkTarget('https://127.0.0.1/').error, 'Refused: internal address');
  assert.equal(B.checkTarget('https://[::1]/').error, 'Refused: internal address');
  assert.equal(B.checkTarget('https://2130706433/').error, 'Refused: internal address');
  for (const bad of ['', 'file:///etc/passwd', 'javascript:alert(1)', 'https://', 'https://exa mple.com/', 'https://example.com:99999x/',
                     'https://example.com\\@evil/', 'https://' + 'a'.repeat(3000), 42, null, undefined]) {
    assert.equal(B.checkTarget(bad).ok, false, String(bad).slice(0, 40));
  }
});

test('parseConsole accepts only well-formed bridge lines', () => {
  const ok = B.parseConsole(B.PREFIX + JSON.stringify({ id: 3, op: 'feed', url: 'https://e.com/' }));
  assert.deepEqual({ ...ok }, { id: 3, op: 'feed', url: 'https://e.com/' });
  for (const bad of ['hello', B.PREFIX + 'not json', B.PREFIX + '[]', B.PREFIX + 'null',
    B.PREFIX + JSON.stringify({ id: '3', op: 'feed', url: 'x' }),
    B.PREFIX + JSON.stringify({ id: 1.5, op: 'feed', url: 'x' }),
    B.PREFIX + JSON.stringify({ id: 1, op: 'exec', url: 'x' }),
    B.PREFIX + JSON.stringify({ id: 1, op: '__proto__', url: 'x' }),
    B.PREFIX + JSON.stringify({ id: 1, op: 'feed', url: 5 }), 7, null]) {
    assert.equal(B.parseConsole(bad), null, String(bad).slice(0, 50));
  }
});

test('replyScript round-trips hostile content as one JS string literal', () => {
  const nasty = '</script>\u2028\u2029"\\\'`${x}\n';
  const js = B.replyScript({ id: 1, ok: true, body: nasty });
  let got;
  new Function('window', js)({ __noozHostReply: (s) => { got = JSON.parse(s); } });
  assert.equal(got.body, nasty);
  assert.doesNotMatch(js, /\u2028|\u2029/);
});

function run(req, r) {
  let reply, calls = 0, opts;
  B.handle(req, (url, o, cb) => { calls++; opts = { url, ...o }; cb(r); }, (x) => { reply = x; });
  return { reply, calls, opts };
}

test('feed: success, upstream failure, timeout, network error, refused target', () => {
  const ok = run({ id: 1, op: 'feed', url: 'https://e.com/f' }, { status: 200, contentType: 'application/atom+xml', body: '<feed/>' });
  assert.deepEqual({ ...ok.reply }, { id: 1, ok: true, status: 200, contentType: 'application/atom+xml', body: '<feed/>' });
  assert.equal(ok.opts.timeoutMs, 15000);
  assert.equal(run({ id: 1, op: 'feed', url: 'https://e.com/f' }, { status: 200, body: '<x/>' }).reply.contentType, 'application/xml; charset=utf-8');
  assert.deepEqual({ ...run({ id: 2, op: 'feed', url: 'https://e.com/f' }, { status: 404, body: 'no' }).reply }, { id: 2, ok: false, status: 502, error: 'Upstream HTTP 404' });
  assert.equal(run({ id: 2, op: 'feed', url: 'https://e.com/f' }, { status: 0, error: 'timeout' }).reply.error, 'Upstream timed out');
  assert.equal(run({ id: 2, op: 'feed', url: 'https://e.com/f' }, { status: 0, error: 'network' }).reply.error, 'Upstream unreachable');
  const refused = run({ id: 4, op: 'feed', url: 'https://192.168.1.1/' }, { status: 200, body: 'x' });
  assert.equal(refused.calls, 0, 'transport must not be called for a refused target');
  assert.deepEqual({ ...refused.reply }, { id: 4, ok: false, status: 400, error: 'Refused: internal address' });
});

test('page: success caps at 3 MB, failures are 200 {error, html:null}', () => {
  const ok = run({ id: 5, op: 'page', url: 'https://e.com/a' }, { status: 200, contentType: 'text/html; charset=utf-8', body: '<p>x</p>', finalUrl: 'https://e.com/b' });
  assert.deepEqual(JSON.parse(JSON.stringify(ok.reply)), { id: 5, ok: true, page: { html: '<p>x</p>', finalUrl: 'https://e.com/b' } });
  assert.equal(ok.opts.timeoutMs, 8000);
  const big = run({ id: 5, op: 'page', url: 'https://e.com/a' }, { status: 200, contentType: 'text/html', body: 'a'.repeat(B.PAGE_MAX_BYTES + 10) });
  assert.equal(big.reply.page.html.length, B.PAGE_MAX_BYTES);
  assert.equal(big.reply.page.finalUrl, 'https://e.com/a');
  const json = run({ id: 6, op: 'page', url: 'https://e.com/a' }, { status: 200, contentType: 'application/json', body: '{}' });
  assert.deepEqual(JSON.parse(JSON.stringify(json.reply.page)), { error: 'Not an HTML page', html: null });
  const fail = run({ id: 7, op: 'page', url: 'https://e.com/a' }, { status: 500, body: '' });
  assert.deepEqual(JSON.parse(JSON.stringify(fail.reply)), { id: 7, ok: true, page: { error: 'Upstream HTTP 500', html: null } });
  assert.equal(run({ id: 8, op: 'page', url: 'http://e.com/' }, null).reply.page.error, 'Unsupported protocol');
});

test('handle answers exactly once even if the transport calls back twice', () => {
  let n = 0;
  B.handle({ id: 1, op: 'feed', url: 'https://e.com/' }, (u, o, cb) => { cb({ status: 200, body: 'a' }); cb({ status: 200, body: 'b' }); }, () => { n++; });
  assert.equal(n, 1);
});

test('tracker aborts overdue requests once and leaves the rest', () => {
  const t = B.createTracker();
  const aborted = [];
  t.add('a', 100, () => aborted.push('a'));
  t.add('b', 500, () => aborted.push('b'));
  assert.deepEqual([...t.sweep(99)], []);
  assert.deepEqual([...t.sweep(100)], ['a']);
  assert.deepEqual([...t.sweep(100)], []);
  t.remove('b');
  assert.equal(t.count(), 0);
  assert.deepEqual(aborted, ['a']);
});
