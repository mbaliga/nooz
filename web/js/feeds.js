// feeds.js -- fetch and parse RSS 2.0 / Atom / RDF (RSS 1.0) feeds.
//
// fetchFeed() calls /api/feed?url=..., a same-origin serverless function
// (see api/feed.js) that fetches source.url server-side and re-serves it.
// Most feed servers don't send Access-Control-Allow-Origin, so a direct
// browser fetch() against them is blocked by CORS even though the request
// itself succeeds -- routing through a same-origin proxy sidesteps that
// (CORS is a browser policy, not a server one). This app still has no
// account and no sync; the proxy is stateless and only relays bytes.
//
// "Never silent": fetchFeed NEVER throws. Every failure path -- a network
// error, a proxy/upstream error, a non-2xx response, a body DOMParser can't
// make sense of -- resolves to { ok: false, error: <short reason> }. Callers
// can rely on always getting a settled, well-shaped result.

const FETCH_TIMEOUT_MS = 20000;

/**
 * Fetch and parse a single feed source.
 * @param {{id: string, url: string}} source
 * @returns {Promise<{ok: true, items: object[]} | {ok: false, error: string}>}
 */
export async function fetchFeed(source) {
  let response;
  try {
    response = await fetchWithTimeout(source.url, FETCH_TIMEOUT_MS);
  } catch (err) {
    if (err && err.name === 'AbortError') {
      return { ok: false, error: 'Timed out' };
    }
    return { ok: false, error: 'Unreachable' };
  }

  if (!response.ok) {
    return { ok: false, error: await proxyErrorReason(response) };
  }

  let body;
  try {
    body = await response.text();
  } catch (_err) {
    return { ok: false, error: 'Could not read response body' };
  }

  if (!body || !body.trim()) {
    return { ok: false, error: 'Empty response' };
  }

  try {
    const items = parseFeedBody(source.id, body);
    if (items === null) {
      return { ok: false, error: 'Could not parse feed' };
    }
    return { ok: true, items };
  } catch (_err) {
    return { ok: false, error: 'Could not parse feed' };
  }
}

function fetchWithTimeout(url, timeoutMs) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const proxied = `/api/feed?url=${encodeURIComponent(url)}`;
  return fetch(proxied, {
    method: 'GET',
    headers: { Accept: 'application/rss+xml, application/atom+xml, application/xml, text/xml, */*' },
    redirect: 'follow',
    credentials: 'omit',
    cache: 'no-store',
    signal: controller.signal,
  }).finally(() => clearTimeout(timer));
}

// api/feed.js answers non-2xx with a JSON { error } body -- surface that
// reason directly instead of a generic "HTTP 502" when we can.
async function proxyErrorReason(response) {
  try {
    const body = await response.json();
    if (body && typeof body.error === 'string') return body.error;
  } catch (_err) {
    // Not JSON (or already consumed) -- fall through to the plain status.
  }
  return `HTTP ${response.status}`;
}

// ---- XML (RSS 2.0 / Atom / RDF) --------------------------------------

// Returns Item[] on success, or null if the body isn't a feed DOMParser can
// make sense of. Never throws (callers wrap in try/catch regardless).
function parseFeedBody(sourceId, body) {
  const doc = new DOMParser().parseFromString(body, 'application/xml');
  if (doc.querySelector('parsererror')) return null;

  const root = doc.documentElement;
  if (!root) return null;

  // Atom feeds are made of <entry>; RSS and RDF (RSS 1.0) are made of <item>,
  // wherever in the tree they live (RDF puts them as siblings of <channel>,
  // not nested inside it).
  const entryNodes = descendantsByLocal(root, 'entry');
  const itemNodes = descendantsByLocal(root, 'item');
  const isAtom = entryNodes.length > 0 && itemNodes.length === 0;
  const nodes = isAtom ? entryNodes : itemNodes;

  const items = [];
  for (const node of nodes) {
    const parsed = isAtom ? parseAtomEntry(sourceId, node) : parseRssItem(sourceId, node);
    if (parsed) items.push(parsed);
  }
  return items;
}

function parseRssItem(sourceId, item) {
  const title = elementText(firstChildByLocal(item, 'title'));

  let link = elementText(firstChildByLocal(item, 'link'));
  if (!link) {
    const guid = childrenByLocal(item, 'guid').find((g) => attrOf(g, 'isPermaLink') !== 'false');
    if (guid) link = elementText(guid);
  }
  if (!title && !link) return null;

  // dc:date is a fallback for feeds without a plain pubDate.
  const date = elementText(firstChildByLocal(item, 'pubDate')) || elementText(firstChildByLocal(item, 'date'));
  // dc:creator (mapped to local name "creator") takes precedence over <author>.
  const author = elementText(firstChildByLocal(item, 'creator')) || elementText(firstChildByLocal(item, 'author'));
  // content:encoded (local name "encoded") usually carries the fuller body;
  // description is the shorter dek. Keep the richest HTML we're given -- this
  // is what turns an "almost empty" reader page into a real article body for
  // the many feeds (Guardian and others) that actually ship full content.
  const encodedEl = firstChildByLocal(item, 'encoded');
  const descriptionEl = firstChildByLocal(item, 'description');
  const contentHtml = elementText(encodedEl) || elementText(descriptionEl) || '';
  const summarySource = elementText(descriptionEl) || elementText(encodedEl) || '';

  return {
    id: makeItemId(sourceId, link, title),
    sourceId,
    title: stripHtml(title),
    link,
    author: author ? stripHtml(author) : null,
    publishedAt: parseDate(date) ?? Date.now(),
    summary: stripHtml(summarySource),
    contentHtml: contentHtml || null,
    image: extractImage(item, contentHtml),
    category: extractCategory(item),
  };
}

function parseAtomEntry(sourceId, entry) {
  const title = elementText(firstChildByLocal(entry, 'title'));

  const links = childrenByLocal(entry, 'link');
  const linkEl =
    links.find((l) => attrOf(l, 'rel') === 'alternate') ||
    links.find((l) => attrOf(l, 'rel') === null) ||
    links[0] ||
    null;
  const link = linkEl ? (attrOf(linkEl, 'href') || '').trim() : '';
  if (!title && !link) return null;

  const date = elementText(firstChildByLocal(entry, 'published')) || elementText(firstChildByLocal(entry, 'updated'));
  const authorEl = firstChildByLocal(entry, 'author');
  const author = authorEl ? elementText(firstChildByLocal(authorEl, 'name')) : '';
  // Atom <content> holds the fuller body; <summary> the dek. Prefer content
  // for the article HTML, summary for the plain-text dek.
  const contentEl = firstChildByLocal(entry, 'content');
  const summaryEl = firstChildByLocal(entry, 'summary');
  const contentHtml = elementText(contentEl) || elementText(summaryEl) || '';
  const summarySource = elementText(summaryEl) || elementText(contentEl) || '';

  return {
    id: makeItemId(sourceId, link, title),
    sourceId,
    title: stripHtml(title),
    link,
    author: author ? stripHtml(author) : null,
    publishedAt: parseDate(date) ?? Date.now(),
    summary: stripHtml(summarySource),
    contentHtml: contentHtml || null,
    image: extractImage(entry, contentHtml),
    category: extractCategory(entry),
  };
}

// ---- media: images & categories ---------------------------------------
//
// Feeds advertise a lead image in several competing ways -- media:content,
// media:thumbnail, an <enclosure type="image/...">, an itunes <image href>,
// or just the first <img> inside the content HTML. We check them in that
// rough order of reliability and take the first https URL we find (http would
// be mixed-content blocked on our https page).

function extractImage(node, contentHtml) {
  // media:content / media:thumbnail carry a url attribute (which is how we tell
  // media:content apart from an Atom <content> element -- same local name).
  for (const local of ['content', 'thumbnail']) {
    for (const el of descendantsByLocal(node, local)) {
      const url = attrOf(el, 'url');
      if (!url) continue;
      const medium = (attrOf(el, 'medium') || attrOf(el, 'type') || '').toLowerCase();
      if (local === 'thumbnail' || medium.startsWith('image') || /\.(jpe?g|png|webp|gif)(\?|$)/i.test(url)) {
        const safe = httpsImage(url);
        if (safe) return safe;
      }
    }
  }

  for (const enclosure of descendantsByLocal(node, 'enclosure')) {
    const url = attrOf(enclosure, 'url');
    const type = (attrOf(enclosure, 'type') || '').toLowerCase();
    if (url && (type.startsWith('image') || /\.(jpe?g|png|webp|gif)(\?|$)/i.test(url))) {
      const safe = httpsImage(url);
      if (safe) return safe;
    }
  }

  for (const image of descendantsByLocal(node, 'image')) {
    const href = attrOf(image, 'href');
    if (href) {
      const safe = httpsImage(href);
      if (safe) return safe;
    }
  }

  if (contentHtml) {
    const m = contentHtml.match(/<img[^>]+src\s*=\s*["']([^"']+)["']/i);
    if (m) {
      const safe = httpsImage(m[1]);
      if (safe) return safe;
    }
  }
  return null;
}

function httpsImage(url) {
  try {
    const u = new URL(url);
    if (u.protocol === 'https:') return u.href;
  } catch (_err) {
    /* unparseable */
  }
  return null;
}

function extractCategory(node) {
  const cat = firstChildByLocal(node, 'category');
  if (!cat) return null;
  const text = elementText(cat) || attrOf(cat, 'term') || attrOf(cat, 'label');
  return text ? text.trim() : null;
}

// ---- dates -------------------------------------------------------------

// Parses RFC-822/1123 (RSS pubDate) and ISO-8601/RFC-3339 (Atom) dates.
// Defensive: any unparseable input yields null so the caller can fall back
// to Date.now() rather than propagate a NaN timestamp.
function parseDate(raw) {
  const s = raw && raw.trim();
  if (!s) return null;

  const direct = Date.parse(s);
  if (!Number.isNaN(direct)) return direct;

  // Some RSS feeds emit slightly malformed RFC-822 (missing/garbled weekday,
  // or a named zone Date.parse doesn't know). Strip a leading weekday and
  // retry before giving up.
  const withoutWeekday = s.replace(/^[A-Za-z]+,\s*/, '');
  const retry = Date.parse(withoutWeekday);
  if (!Number.isNaN(retry)) return retry;

  return null;
}

// ---- id hashing ----------------------------------------------------------

// Stable Item.id: a short deterministic hash of sourceId + link (falling
// back to sourceId + title when a feed omits a link). No crypto needed --
// this only has to be stable and collision-unlikely within one source, not
// cryptographically secure.
function makeItemId(sourceId, link, title) {
  const basis = link && link.trim() ? `${sourceId}\u0000${link.trim()}` : `${sourceId}\u0000${title || ''}`;
  return hashString(basis);
}

// djb2 string hash, folded to an unsigned 32-bit int and rendered base36.
function hashString(str) {
  let hash = 5381;
  for (let i = 0; i < str.length; i++) {
    hash = (hash * 33 + str.charCodeAt(i)) | 0;
  }
  return (hash >>> 0).toString(36);
}

// ---- HTML stripping ------------------------------------------------------

// Turns an escaped/marked-up feed snippet into clean display text, using the
// browser's own HTML parser rather than hand-rolled entity tables -- it
// handles tags, named entities, and numeric entities uniformly and never
// throws on malformed markup.
function stripHtml(raw) {
  if (!raw) return '';
  const parsed = new DOMParser().parseFromString(raw, 'text/html');
  const text = parsed.body ? parsed.body.textContent || '' : '';
  return text.replace(/\s+/g, ' ').trim();
}

// ---- namespace-agnostic DOM helpers -------------------------------------
//
// Feeds mix bare, prefixed, and default-namespaced elements inconsistently
// across the wild (dc:creator, content:encoded, plain <link>, atom <link
// href>...). Matching on local name only -- ignoring namespace prefixes --
// is what makes one code path cover RSS, Atom, and RDF.

function localName(el) {
  return el.localName || el.tagName.split(':').pop();
}

function childrenByLocal(el, local) {
  const lower = local.toLowerCase();
  const out = [];
  for (const child of el.children) {
    if (localName(child).toLowerCase() === lower) out.push(child);
  }
  return out;
}

function firstChildByLocal(el, local) {
  const lower = local.toLowerCase();
  for (const child of el.children) {
    if (localName(child).toLowerCase() === lower) return child;
  }
  return null;
}

function descendantsByLocal(el, local) {
  const lower = local.toLowerCase();
  const out = [];
  (function walk(node) {
    for (const child of node.children) {
      if (localName(child).toLowerCase() === lower) out.push(child);
      walk(child);
    }
  })(el);
  return out;
}

function elementText(el) {
  return el ? (el.textContent || '').trim() : '';
}

function attrOf(el, name) {
  return el && el.hasAttribute(name) ? el.getAttribute(name) : null;
}
