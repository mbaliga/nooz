// sanitize.js -- turn an untrusted HTML string (a feed's content:encoded body,
// or a server-extracted article) into a safe DocumentFragment containing only
// a small allowlist of formatting elements. Everything else -- scripts, event
// handlers, iframes, styles, javascript: URLs, on* attributes -- is dropped.
//
// The parse happens in an inert document (DOMParser 'text/html'), never by
// assigning innerHTML into the live page, so nothing runs during parsing. We
// then rebuild an allowlisted tree by hand rather than trusting the parsed
// nodes directly.

const ALLOWED = new Set([
  'P', 'BR', 'HR', 'BLOCKQUOTE', 'STRONG', 'B', 'EM', 'I', 'U', 'SPAN',
  'UL', 'OL', 'LI', 'H2', 'H3', 'H4', 'A', 'FIGURE', 'FIGCAPTION', 'IMG',
]);
const BLOCK_STRIP = new Set(['SCRIPT', 'STYLE', 'IFRAME', 'NOSCRIPT', 'FORM', 'INPUT', 'BUTTON', 'SVG']);

/**
 * @param {string} html
 * @param {{allowImages?: boolean}} [opts]
 * @returns {DocumentFragment}
 */
export function sanitizeHtml(html, opts = {}) {
  const allowImages = opts.allowImages !== false;
  const frag = document.createDocumentFragment();
  if (!html || typeof html !== 'string') return frag;

  let doc;
  try {
    doc = new DOMParser().parseFromString(html, 'text/html');
  } catch (_err) {
    return frag;
  }
  if (!doc || !doc.body) return frag;

  for (const child of Array.from(doc.body.childNodes)) {
    const clean = cleanNode(child, allowImages);
    if (clean) frag.appendChild(clean);
  }
  return frag;
}

function cleanNode(node, allowImages) {
  if (node.nodeType === Node.TEXT_NODE) {
    return document.createTextNode(node.nodeValue);
  }
  if (node.nodeType !== Node.ELEMENT_NODE) return null;

  const tag = node.tagName;
  if (BLOCK_STRIP.has(tag)) return null;

  if (!ALLOWED.has(tag)) {
    // Unknown wrapper (div, section, article, table…): drop the tag but keep
    // any usable children, so we don't lose the actual prose inside it.
    const frag = document.createDocumentFragment();
    for (const child of Array.from(node.childNodes)) {
      const clean = cleanNode(child, allowImages);
      if (clean) frag.appendChild(clean);
    }
    return frag.childNodes.length ? frag : null;
  }

  if (tag === 'IMG') {
    if (!allowImages) return null;
    const src = safeImageUrl(node.getAttribute('src'));
    if (!src) return null;
    const img = document.createElement('img');
    img.src = src;
    img.loading = 'lazy';
    img.alt = node.getAttribute('alt') || '';
    return img;
  }

  const el = document.createElement(tag.toLowerCase());
  if (tag === 'A') {
    const href = safeLinkUrl(node.getAttribute('href'));
    if (href) {
      el.href = href;
      el.target = '_blank';
      el.rel = 'noopener noreferrer';
    }
  }
  for (const child of Array.from(node.childNodes)) {
    const clean = cleanNode(child, allowImages);
    if (clean) el.appendChild(clean);
  }
  return el;
}

function safeLinkUrl(href) {
  if (!href) return null;
  try {
    const u = new URL(href, window.location.href);
    if (u.protocol === 'http:' || u.protocol === 'https:') return u.href;
  } catch (_err) {
    /* unparseable */
  }
  return null;
}

// Only https images render on an https page (http would be mixed-content
// blocked), and we require an absolute URL so a feed can't smuggle a data: or
// javascript: payload into an <img>.
function safeImageUrl(src) {
  if (!src) return null;
  try {
    const u = new URL(src, window.location.href);
    if (u.protocol === 'https:') return u.href;
  } catch (_err) {
    /* unparseable */
  }
  return null;
}
