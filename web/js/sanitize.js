// sanitize.js -- turn an untrusted HTML string (a feed's content:encoded body,
// or a server-extracted article) into a safe DocumentFragment containing only
// a small allowlist of formatting elements. Everything else -- scripts, event
// handlers, iframes, styles, javascript: URLs, on* attributes -- is dropped.
//
// The parse happens in an inert document (DOMParser 'text/html'), never by
// assigning innerHTML into the live page, so nothing runs during parsing. We
// then rebuild an allowlisted tree by hand rather than trusting the parsed
// nodes directly.
//
// This is XSS-safety sanitization, not content cleaning, and used to stop
// there: a "Continue reading →" link or a WordPress syndication footer is
// perfectly safe markup, so it survived untouched (reported: "links coming
// through or some sort of ads... the article itself isn't fully clean").
// trimTrailingBoilerplate below is the second pass that removes those --
// applied to every block this rebuild produces, so it catches both the feed's
// own content:encoded HTML and whatever the server-side Readability pass
// (api/article.js) left behind.

import { stripTrailingBoilerplate, isWholeTextBoilerplate } from './articleBoilerplate.js';

const ALLOWED = new Set([
  'P', 'BR', 'HR', 'BLOCKQUOTE', 'STRONG', 'B', 'EM', 'I', 'U', 'SPAN',
  'UL', 'OL', 'LI', 'H2', 'H3', 'H4', 'A', 'FIGURE', 'FIGCAPTION', 'IMG',
]);
const BLOCK_STRIP = new Set(['SCRIPT', 'STYLE', 'IFRAME', 'NOSCRIPT', 'FORM', 'INPUT', 'BUTTON', 'SVG']);
// Elements whose own trailing content is worth checking for boilerplate --
// deliberately not the inline-emphasis tags (STRONG/EM/etc.), since a
// fragment like "Advertisement" inside a <strong> mid-sentence is someone's
// actual emphasis, not a label glued on by a feed generator.
const BOILERPLATE_CHECKED = new Set(['P', 'LI', 'BLOCKQUOTE', 'H2', 'H3', 'H4']);

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

  return cleanChildren(doc.body, allowImages);
}

/**
 * The actual tree rebuild, factored out from the string-parsing step above so
 * it can be exercised directly on an already-parsed container (see
 * sanitize.test.mjs) — not a production entry point on its own.
 * @param {Node} container
 * @param {boolean} allowImages
 * @returns {DocumentFragment}
 */
export function cleanChildren(container, allowImages) {
  const frag = document.createDocumentFragment();
  for (const child of Array.from(container.childNodes)) {
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

  if (tag === 'A' && isWholeTextBoilerplate(node.textContent)) {
    // A "Continue reading →"/"Read more" link's whole label IS the ad copy --
    // drop the link (and its text) entirely rather than merely de-linking it.
    return null;
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
  if (BOILERPLATE_CHECKED.has(tag)) {
    trimTrailingBoilerplate(el);
    if (!el.textContent.trim()) return null;
  }
  return el;
}

/**
 * Walks [el]'s children from the end, dropping a trailing child that is
 * itself nothing but boilerplate (a lone "Continue reading" <a>, a bare
 * tracking-link text node) and trimming a boilerplate fragment glued onto
 * the tail of the last remaining text node ("...real last sentence.
 * Continue reading →" in one run, no separate <a> at all) -- a feed can
 * chain more than one, so this keeps walking back until a real child stops
 * it. Mutates [el] in place; the caller checks afterward whether anything
 * real is left.
 */
function trimTrailingBoilerplate(el) {
  while (el.lastChild) {
    const last = el.lastChild;
    if (last.nodeType === Node.TEXT_NODE) {
      if (!(last.nodeValue || '').trim()) {
        // Whitespace only (pretty-printed markup's trailing newline/indent)
        // -- never meaningful, and never a reason to stop walking back: a
        // boilerplate <a> can easily sit right before one of these.
        el.removeChild(last);
        continue;
      }
      const stripped = stripTrailingBoilerplate(last.nodeValue);
      if (stripped === last.nodeValue.trim()) return; // nothing trailing to strip here
      if (stripped) {
        last.nodeValue = stripped;
        return;
      }
      el.removeChild(last);
      continue;
    }
    if (last.nodeType === Node.ELEMENT_NODE && isWholeTextBoilerplate(last.textContent)) {
      el.removeChild(last);
      continue;
    }
    return;
  }
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
