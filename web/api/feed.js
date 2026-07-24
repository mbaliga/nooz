// api/feed.js -- server-side proxy for RSS/Atom/RDF feed fetches.
//
// The reader is otherwise fully client-side, but most feed servers don't
// send Access-Control-Allow-Origin, so the browser refuses to let page JS
// read the response even though the request itself succeeded. CORS is a
// browser-enforced policy, not a server one, so a server-side fetch has no
// such restriction: this function fetches the feed on the browser's behalf
// and re-serves the body with a permissive CORS header.
//
// This is a public endpoint that fetches whatever URL it's given, so it's
// an open-proxy/SSRF surface by construction (the app lets users add any
// RSS source, so a fixed host allowlist isn't an option). isBlockedTarget
// rejects the obvious cases -- localhost, loopback, link-local (including
// the cloud metadata address), and private ranges -- given as literal IPs
// or "localhost". It does NOT defend against DNS rebinding (a hostname
// that only resolves to an internal address); that needs pinning the
// resolved IP for the actual connection, which is more machinery than this
// hobby-scale reader warrants today.
//
// Only used when this directory (`web/`) is deployed as its own Vercel
// project root -- e.g. via web/vercel.json's subpath config. The live
// asystemofcells.com/nooz/read deployment keeps its own copy at that
// repo's root api/feed.js, since Vercel Functions are always rooted at the
// deployed project's root, not at this subdirectory.

const TIMEOUT_MS = 15000;

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
  let upstream;
  try {
    upstream = await fetch(parsed.toString(), {
      headers: {
        Accept: 'application/rss+xml, application/atom+xml, application/xml, text/xml, */*',
        'User-Agent': 'Mozilla/5.0 (compatible; NoozReader/1.0; +https://asystemofcells.com/nooz/read)',
      },
      redirect: 'follow',
      signal: controller.signal,
    });
  } catch (err) {
    clearTimeout(timer);
    res.status(502).json({ error: err.name === 'AbortError' ? 'Upstream timed out' : 'Upstream unreachable' });
    return;
  }
  clearTimeout(timer);

  if (!upstream.ok) {
    res.status(502).json({ error: `Upstream HTTP ${upstream.status}` });
    return;
  }

  const body = await upstream.text();
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Cache-Control', 'public, max-age=0, s-maxage=300, stale-while-revalidate=600');
  res.setHeader('Content-Type', upstream.headers.get('content-type') || 'application/xml; charset=utf-8');
  res.status(200).send(body);
};

function isBlockedTarget(hostname) {
  const lower = hostname.toLowerCase();
  if (lower === 'localhost') return true;

  const v4 = lower.match(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/);
  if (v4) {
    const [a, b] = v4.slice(1).map(Number);
    if (a === 127 || a === 10 || a === 0) return true; // loopback, RFC1918, "this network"
    if (a === 172 && b >= 16 && b <= 31) return true; // RFC1918
    if (a === 192 && b === 168) return true; // RFC1918
    if (a === 169 && b === 254) return true; // link-local, incl. cloud metadata
    return false;
  }

  if (lower === '::1' || lower.startsWith('fe80:') || lower.startsWith('fc') || lower.startsWith('fd')) {
    return true; // IPv6 loopback, link-local, unique-local
  }
  return false;
}
