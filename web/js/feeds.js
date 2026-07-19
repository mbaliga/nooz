// feeds.js -- fetch and parse RSS 2.0 / Atom / RDF (RSS 1.0) feeds.
//
// Fully client-side: fetchFeed() calls the browser's fetch() directly against
// source.url. There is no proxy and no backend server -- this app has no
// account and no sync, so "reach the source" means "the reader's own browser
// reaches the source." A feed that blocks cross-origin requests, or is simply
// offline, will fail here, and fetchFeed reports that honestly instead of
// pretending the source is empty.
//
// "Never silent": fetchFeed NEVER throws. Every failure path -- a network
// error, a CORS block, a non-2xx response, a body DOMParser can't make sense
// of -- resolves to { ok: false, error: <short reason> }. Callers can rely on
// always getting a settled, well-shaped result.

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
    return { ok: false, error: 'CORS blocked or unreachable' };
  }

  if (!response.ok) {
    return { ok: false, error: `HTTP ${response.status}` };
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
  return fetch(url, {
    method: 'GET',
    headers: { Accept: 'application/rss+xml, application/atom+xml, application/xml, text/xml, */*' },
    redirect: 'follow',
    credentials: 'omit',
    cache: 'no-store',
    signal: controller.signal,
  }).finally(() => clearTimeout(timer));
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
  // content:encoded (local name "encoded") usually carries the fuller body.
  const summaryEl = firstChildByLocal(item, 'encoded') || firstChildByLocal(item, 'description');

  return {
    id: makeItemId(sourceId, link, title),
    sourceId,
    title: stripHtml(title),
    link,
    author: author ? stripHtml(author) : null,
    publishedAt: parseDate(date) ?? Date.now(),
    summary: summaryEl ? stripHtml(elementText(summaryEl)) : '',
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
  const summaryEl = firstChildByLocal(entry, 'summary') || firstChildByLocal(entry, 'content');

  return {
    id: makeItemId(sourceId, link, title),
    sourceId,
    title: stripHtml(title),
    link,
    author: author ? stripHtml(author) : null,
    publishedAt: parseDate(date) ?? Date.now(),
    summary: summaryEl ? stripHtml(elementText(summaryEl)) : '',
  };
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
