// api/article.js -- server-side full-article extraction for the web reader.
//
// Many feeds (BBC and most wire services) put only a headline and a one-line
// summary in their RSS, so the reader page for those comes back almost empty.
// This endpoint fetches the linked article server-side and runs Mozilla's
// Readability over it to recover the real body text + lead image, which the
// client then renders (and caches in IndexedDB, so it's fetched once).
//
// Same SSRF posture as api/feed.js: it will fetch whatever URL it's given (the
// reader can add any source), so it refuses obviously-internal targets. It
// does NOT defend against DNS rebinding -- out of scope for this reader.
//
// Written in CommonJS to match api/feed.js; Readability/linkedom are pulled in
// with dynamic import() so their ESM packaging doesn't force this file (or the
// sibling feed.js) to become a module.

// Kept well under the platform's own gateway timeout for this deployment
// (undocumented in-repo, but empirically observed to sit close to 15s: at
// the old 15000ms value, a slow upstream would lose the race between this
// AbortController firing and the platform giving up on the function first,
// so the client got the platform's own opaque 502 instead of the honest
// JSON error below -- same status code, but with no error message and, on
// a background prefetch storm, indistinguishable from any other failure).
// Shorter than that outer limit means this function's own clean response
// wins the race every time.
const TIMEOUT_MS = 8000;
const MAX_BYTES = 3_000_000; // don't try to parse enormous pages

module.exports = async function handler(req, res) {
  if (req.method !== 'GET') {
    res.status(405).json({ error: 'Method not allowed' });
    return;
  }

  const target = req.query.url;
  if (!target || typeof target !== 'string') {
    res.status(400).json({ error: 'Missing url parameter' });
    return;
  }

  let parsed;
  try {
    parsed = new URL(target);
  } catch {
    res.status(400).json({ error: 'Invalid url' });
    return;
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    res.status(400).json({ error: 'Unsupported protocol' });
    return;
  }
  if (isBlockedTarget(parsed.hostname)) {
    res.status(400).json({ error: 'Refused: internal address' });
    return;
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  let html;
  try {
    const upstream = await fetch(parsed.toString(), {
      headers: {
        Accept: 'text/html,application/xhtml+xml',
        'User-Agent': 'Mozilla/5.0 (compatible; NoozReader/1.0; +https://asystemofcells.com/nooz/read)',
      },
      redirect: 'follow',
      signal: controller.signal,
    });
    if (!upstream.ok) {
      clearTimeout(timer);
      res.status(502).json({ error: `Upstream HTTP ${upstream.status}` });
      return;
    }
    const ct = (upstream.headers.get('content-type') || '').toLowerCase();
    if (ct && !ct.includes('html')) {
      clearTimeout(timer);
      res.status(415).json({ error: 'Not an HTML page' });
      return;
    }
    html = await readCapped(upstream, MAX_BYTES);
  } catch (err) {
    clearTimeout(timer);
    res.status(502).json({ error: err.name === 'AbortError' ? 'Upstream timed out' : 'Upstream unreachable' });
    return;
  }
  clearTimeout(timer);

  try {
    const { parseHTML } = await import('linkedom');
    const { Readability } = await import('@mozilla/readability');

    // A <base> tag lets Readability resolve relative image/link URLs against
    // the real page, so the extracted body's images aren't dropped later.
    const withBase = injectBase(html, parsed.toString());
    const { document } = parseHTML(withBase);

    const ogImage = metaContent(document, 'og:image') || metaContent(document, 'twitter:image');

    const reader = new Readability(document, { charThreshold: 200 });
    const article = reader.parse();

    if (!article || !article.content) {
      res.status(200).json({ error: 'Could not extract article', html: null });
      return;
    }

    const leadImage = absolute(ogImage, parsed) || firstImage(article.content, parsed);

    res.setHeader('Cache-Control', 'public, max-age=0, s-maxage=86400, stale-while-revalidate=604800');
    res.status(200).json({
      title: article.title || null,
      byline: article.byline || null,
      html: article.content,
      leadImage: leadImage || null,
    });
  } catch (_err) {
    res.status(500).json({ error: 'Extraction failed', html: null });
  }
};

async function readCapped(response, maxBytes) {
  if (!response.body || typeof response.body.getReader !== 'function') {
    const text = await response.text();
    return text.slice(0, maxBytes);
  }
  const reader = response.body.getReader();
  const chunks = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.length;
    chunks.push(value);
    if (total >= maxBytes) {
      try { reader.cancel(); } catch (_e) { /* already closing */ }
      break;
    }
  }
  return Buffer.concat(chunks).toString('utf8');
}

function injectBase(html, url) {
  const baseTag = `<base href="${url.replace(/"/g, '&quot;')}">`;
  if (/<head[^>]*>/i.test(html)) return html.replace(/<head[^>]*>/i, (m) => m + baseTag);
  if (/<html[^>]*>/i.test(html)) return html.replace(/<html[^>]*>/i, (m) => m + '<head>' + baseTag + '</head>');
  return baseTag + html;
}

function metaContent(document, prop) {
  const el =
    document.querySelector(`meta[property="${prop}"]`) ||
    document.querySelector(`meta[name="${prop}"]`);
  return el ? el.getAttribute('content') : null;
}

function firstImage(contentHtml, base) {
  const m = contentHtml.match(/<img[^>]+src\s*=\s*["']([^"']+)["']/i);
  return m ? absolute(m[1], base) : null;
}

function absolute(src, base) {
  if (!src) return null;
  try {
    const u = new URL(src, base);
    return u.protocol === 'https:' ? u.href : null;
  } catch (_err) {
    return null;
  }
}

function isBlockedTarget(hostname) {
  const lower = hostname.toLowerCase();
  if (lower === 'localhost') return true;
  const v4 = lower.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (v4) {
    const [a, b] = v4.slice(1).map(Number);
    if (a === 127 || a === 10 || a === 0) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    if (a === 192 && b === 168) return true;
    if (a === 169 && b === 254) return true;
    return false;
  }
  if (lower === '::1' || lower.startsWith('fe80:') || lower.startsWith('fc') || lower.startsWith('fd')) {
    return true;
  }
  return false;
}
