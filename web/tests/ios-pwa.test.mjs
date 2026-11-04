import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

/**
 * What iOS needs to install the reader to the Home Screen and run it as an app.
 *
 * This reads the static markup and manifest only. It proves the tags are there,
 * not that Safari honours them: Add to Home Screen, the standalone chrome and the
 * safe-area insets are checked on a device, not here.
 */

const root = new URL('../', import.meta.url);
const html = readFileSync(new URL('index.html', root), 'utf8');
const manifest = JSON.parse(readFileSync(new URL('manifest.webmanifest', root), 'utf8'));

/** Every <meta> / <link> tag in the document head, as an attribute map. */
function tags(name) {
  const out = [];
  for (const m of html.matchAll(new RegExp(`<${name}\\b([^>]*)>`, 'gi'))) {
    const attrs = {};
    for (const a of m[1].matchAll(/([\w-]+)="([^"]*)"/g)) attrs[a[1]] = a[2];
    out.push(attrs);
  }
  return out;
}

const metaContent = (n) => tags('meta').find((t) => t.name === n)?.content;

/** Width and height from a PNG's IHDR chunk. */
function pngSize(rel) {
  const b = readFileSync(new URL(rel, root));
  assert.equal(b.subarray(1, 4).toString('latin1'), 'PNG', `${rel} is a PNG`);
  return [b.readUInt32BE(16), b.readUInt32BE(20)];
}

test('apple-touch-icon is a 180x180 PNG that exists', () => {
  const icon = tags('link').find((t) => t.rel === 'apple-touch-icon');
  assert.ok(icon, 'apple-touch-icon link present');
  assert.equal(icon.sizes, '180x180');
  assert.equal(icon.href, 'icons/icon-180.png');
  assert.deepEqual(pngSize(icon.href), [180, 180]);
});

test('apple-mobile-web-app-* meta tags are present', () => {
  assert.equal(metaContent('apple-mobile-web-app-capable'), 'yes');
  assert.equal(metaContent('apple-mobile-web-app-title'), 'Nooz');
  assert.ok(metaContent('apple-mobile-web-app-status-bar-style'));
});

test('viewport covers the notch with viewport-fit=cover', () => {
  assert.match(metaContent('viewport'), /viewport-fit=cover/);
});

test('manifest is linked and its icons exist as PNGs', () => {
  assert.ok(tags('link').some((t) => t.rel === 'manifest' && t.href === 'manifest.webmanifest'));
  assert.ok(manifest.icons.length >= 2);
  for (const icon of manifest.icons) {
    assert.equal(icon.type, 'image/png');
    assert.match(icon.sizes, /^\d+x\d+$/);
    pngSize(icon.src);
  }
});

test('manifest scope and start_url keep the app on one path', () => {
  assert.equal(manifest.start_url, '.');
  assert.equal(manifest.scope, '.');
  assert.equal(manifest.display, 'standalone');
});

test('the page makes no analytics or tracking calls', () => {
  assert.doesNotMatch(html, /google-analytics|googletagmanager|gtag\(|plausible|segment\.com|sentry/i);
});
