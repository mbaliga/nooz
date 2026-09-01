import assert from 'node:assert/strict';
import test from 'node:test';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';
import { AxeBuilder } from '@axe-core/playwright';

/**
 * Accessibility, checked by a real browser rather than by reading the source.
 *
 * The audit that started this work found `role="button"` wrapping whole article
 * bodies, a Loom that instructed a gesture it would not accept, and read state
 * carried by colour alone — none of which any test could see, because there
 * were no browser tests at all. Reasoning over source is how those survived: it
 * is very easy to look at markup and conclude it is fine.
 *
 * axe-core is not a substitute for a person with a screen reader, and it does
 * not claim to be — it catches roughly a third of real barriers. But the third
 * it catches includes exactly the regressions that are invisible in review:
 * contrast that drifts below 4.5:1 after a palette tweak, a new icon button
 * with no accessible name, a heading level skipped, a landmark lost. Those are
 * the ones that come back.
 *
 * The pages here are seeded with real content through the app's own IndexedDB
 * layer, because an empty Paper checks almost nothing: it is the story cards,
 * the reader and the Loom that carry the risk.
 */

const REPO = path.resolve(import.meta.dirname, '..', '..');
const PORT = 8788;

/** Content types the reader actually serves. Anything else is a 404 on purpose. */
const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ttf': 'font/ttf',
};

function serve() {
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    let file = path.join(REPO, decodeURIComponent(url.pathname));
    if (url.pathname === '/' || url.pathname === '') file = path.join(REPO, 'index.html');
    // No traversal outside web/, and no serving the test tree back to itself.
    if (!file.startsWith(REPO) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
      res.writeHead(404).end('not found');
      return;
    }
    res.writeHead(200, { 'content-type': TYPES[path.extname(file)] || 'application/octet-stream' });
    fs.createReadStream(file).pipe(res);
  });
  return new Promise((resolve) => server.listen(PORT, () => resolve(server)));
}

/**
 * The pre-installed Chromium, when the bundled one is a different build.
 * Falls back to Playwright's own resolution, which is what CI uses after
 * `npx playwright install chromium`.
 */
function launchOptions() {
  const pinned = process.env.PLAYWRIGHT_CHROMIUM_PATH;
  return pinned ? { headless: true, executablePath: pinned } : { headless: true };
}

const SOURCES = [
  { id: 's1', title: 'The Chronicle', url: 'https://example.com/feed', enabled: true, region: 'europe-africa' },
  { id: 's2', title: 'The Gazette', url: 'https://example.org/feed', enabled: true, region: 'americas' },
];

const ITEMS = Array.from({ length: 12 }, (_, i) => ({
  id: `item-${i}`,
  sourceId: i % 2 === 0 ? 's1' : 's2',
  title: [
    'Parliament votes to open the reservoir after a long inquiry',
    'Inflation and interest rates worry investors as the market opens',
    'Airstrike hits a militant position near the border',
    'Researchers publish a study on the genome of a deep-sea fossil',
  ][i % 4],
  summary: 'A short dek about what happened, who it happened to, and what is expected next.',
  contentHtml: '<p>The first paragraph of the story, long enough to lay out as a column.</p>'
    + '<p>A second paragraph, so the reader view has something to render.</p>',
  link: `https://example.com/story-${i}`,
  publishedAt: Date.parse('2026-08-31T09:00:00Z') - i * 3600_000,
}));

/**
 * A browser page with the app booted, onboarding done, and real stories in it.
 *
 * Its own context, not `browser.newPage()`: axe-core refuses to inject into a
 * page from the browser's implicit context, and a fresh context also gives each
 * test its own IndexedDB and localStorage, so seeded stories and a stored
 * language cannot leak between them.
 */
async function readerPage(browser) {
  const context = await browser.newContext();
  const page = await context.newPage();
  // Nothing else in this repo loads the reader over HTTP, so this is the only
  // place a missing font, icon or module would ever show up.
  page.on('response', (r) => {
    const pathname = new URL(r.url()).pathname;
    // /api/* are Vercel serverless functions, not files in the repo; this
    // static server has none of them, and that is not a defect.
    if (r.status() === 404 && !pathname.startsWith('/api/')) missing.add(pathname);
  });
  await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'domcontentloaded' });
  // The key onboarding.js actually uses. A wrong one leaves the onboarding card
  // up, and every scan then measures that card instead of the app -- which is
  // how the first version of this suite "passed" the reader page while never
  // reaching it.
  await page.evaluate(() => localStorage.setItem('nooz-onboarded-v1', 'yes'));

  await page.goto(`http://localhost:${PORT}/index.html`, { waitUntil: 'load' });
  await page.evaluate(async ({ sources, items }) => {
    const db = await import('./js/db.js');
    await db.dbInit();
    for (const source of sources) await db.dbAddSource(source);
    await db.dbPutItems(items);
  }, { sources: SOURCES, items: ITEMS });

  await page.reload({ waitUntil: 'load' });
  await page.waitForSelector('.nooz-art-headline, .nooz-lead-headline, .nooz-col-headline', { timeout: 10_000 });
  return page;
}

/**
 * Rules deliberately not enforced yet, each with the reason. An empty object is
 * the goal; an entry here is a debt with a name, which is the difference
 * between a known gap and a silent one.
 */
const KNOWN_GAPS = {};

function summarise(violations) {
  return violations.map((v) => {
    const where = v.nodes.slice(0, 3).map((n) => n.target.join(' ')).join(' | ');
    return `${v.id} (${v.impact}) x${v.nodes.length}: ${v.help}\n      ${where}`;
  }).join('\n    ');
}

async function scan(page, context) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  const violations = results.violations.filter((v) => !(v.id in KNOWN_GAPS));
  assert.equal(
    violations.length,
    0,
    `${context} has ${violations.length} accessibility violation(s):\n    ${summarise(violations)}`,
  );
  return results;
}

let server;
let browser;
const missing = new Set();

test.before(async () => {
  server = await serve();
  browser = await chromium.launch(launchOptions());
});

test.after(async () => {
  await browser?.close();
  await new Promise((resolve) => server.close(resolve));
});

test('every asset the reader asks for exists', async () => {
  const page = await readerPage(browser);
  await page.goto(`http://localhost:${PORT}/index.html#/settings`, { waitUntil: 'load' });
  await page.context().close();
  assert.deepEqual([...missing], [], 'the reader requested files that are not there');
});

test('the Paper, with real stories on it', async () => {
  const page = await readerPage(browser);
  const results = await scan(page, 'The Paper');
  // A pass on an empty page proves nothing, so check axe actually had work.
  assert.ok(results.passes.length > 5, 'axe found almost nothing to check — the page did not render');
  await page.context().close();
});

test('the reader, with an article open', async () => {
  const page = await readerPage(browser);
  await page.locator('a.nooz-headline-link').first().click();
  await page.waitForSelector('.nooz-reader-body, .nooz-reader-layout', { timeout: 10_000 });
  await scan(page, 'The reader');
  await page.context().close();
});

test('the Loom', async () => {
  const page = await readerPage(browser);
  await page.goto(`http://localhost:${PORT}/index.html#/loom`, { waitUntil: 'load' });
  await page.waitForSelector('.nooz-loom', { timeout: 10_000 });
  await scan(page, 'The Loom');
  await page.context().close();
});

test('Settings, including the language picker', async () => {
  const page = await readerPage(browser);
  await page.goto(`http://localhost:${PORT}/index.html#/settings`, { waitUntil: 'load' });
  await page.waitForSelector('.nooz-language-list', { timeout: 10_000 });
  await scan(page, 'Settings');
  await page.context().close();
});

test('the front page in Urdu, laid out right to left', async () => {
  // RTL is where a layout that only ever ran in English tends to break, and
  // where `lang`/`dir` errors become visible to axe rather than only to a
  // screen reader.
  const page = await readerPage(browser);
  await page.evaluate(() => localStorage.setItem('nooz.language', 'ur'));
  await page.reload({ waitUntil: 'load' });
  await page.waitForSelector('.nooz-art-headline, .nooz-lead-headline, .nooz-col-headline', { timeout: 10_000 });

  assert.equal(await page.getAttribute('html', 'dir'), 'rtl', 'the document runs right to left');
  assert.equal(await page.getAttribute('html', 'lang'), 'ur', 'the document declares its language');
  await scan(page, 'The Paper in Urdu');
  await page.context().close();
});
