// foundQuote.js -- a quiet aside every few minutes of active reading: a real
// sentence pulled from an article the reader has actually opened, never a
// canned quote and never AI-generated. It's a plain editorial device (the
// pull-quote a real newspaper sets in larger type to break up a long piece),
// not a reward for reading -- no counter, no streak, nothing to "unlock".
//
// Two presentations, chosen in Settings (house: "found quote" default):
//   - quote:    a small blockquote-style break, hairline rules above/below.
//   - dateline: a single wire-style line, SOURCE NAME in small caps then the
//               line, like a wire-service insert rather than a set-off block.
// Both read from the exact same picked sentence -- only the wrapping differs.

import { sanitizeHtml } from './sanitize.js';

const MIN_LEN = 50;
const MAX_LEN = 220;

// Good enough for picking a plausible pull-quote, not a linguistic parser:
// split on sentence-ending punctuation followed by whitespace and a capital
// letter (or an opening quote), then filter to a length that reads well set
// apart on its own.
function sentencesFrom(html) {
  const frag = sanitizeHtml(html, { allowImages: false });
  // frag.textContent alone glues adjacent block elements together with no
  // separator at all ("...come.Supporters say...") since <p> boundaries carry
  // no whitespace of their own -- joining each top-level node's own text with
  // a space keeps paragraph breaks from silently vanishing into one run-on
  // blob the length filter below would then reject outright.
  const text = Array.from(frag.childNodes)
    .map((node) => (node.textContent || '').trim())
    .filter(Boolean)
    .join(' ')
    .replace(/\s+/g, ' ')
    .trim();
  if (!text) return [];
  return text
    .split(/(?<=[.!?])\s+(?=[A-Z"“])/)
    .map((s) => s.trim())
    .filter((s) => s.length >= MIN_LEN && s.length <= MAX_LEN)
    .filter((s) => !/https?:\/\//.test(s))
    .filter((s) => (s.match(/[A-Z]{5,}/g) || []).length === 0); // skip all-caps runs (credits/bylines that slipped through)
}

/**
 * Picks one real sentence from something the reader has actually read.
 * Prefers whichever item is currently open (the quote then reads as "pulled
 * from the piece in front of you"); otherwise a random already-read item with
 * real extracted text. Returns null rather than reaching for thin content --
 * no candidate this cycle just means no aside this cycle.
 */
export function pickFoundQuote(state) {
  const items = state.items || [];
  const readIds = state.readIds || new Set();
  const articles = state.articles || {};

  const candidates = items.filter((it) => {
    if (!readIds.has(it.id)) return false;
    const extracted = articles[it.id];
    return Boolean((extracted && extracted.html) || it.contentHtml);
  });
  if (!candidates.length) return null;

  const current = candidates.find((it) => it.id === state.currentItemId);
  const pool = current ? [current] : candidates;
  const item = pool[Math.floor(Math.random() * pool.length)];

  const extracted = articles[item.id];
  const html = (extracted && extracted.html) || item.contentHtml;
  const sentences = sentencesFrom(html);
  if (!sentences.length) return null;

  const source = (state.sources || []).find((s) => s.id === item.sourceId);
  return {
    itemId: item.id,
    text: sentences[Math.floor(Math.random() * sentences.length)],
    sourceTitle: (source && source.title) || item.author || 'Unknown source',
  };
}

export function buildFoundQuoteAside(quote, style) {
  const aside = document.createElement('aside');
  aside.setAttribute('aria-label', 'A line from what you have been reading');

  if (style === 'dateline') {
    aside.className = 'nooz-aside-dateline';
    const p = document.createElement('p');
    const tag = document.createElement('span');
    tag.className = 'nooz-aside-dateline-tag';
    tag.textContent = quote.sourceTitle.toUpperCase();
    p.appendChild(tag);
    const rest = document.createElement('span');
    rest.textContent = ` — “${quote.text}”`;
    p.appendChild(rest);
    aside.appendChild(p);
    return aside;
  }

  aside.className = 'nooz-aside-quote';
  const rule1 = document.createElement('hr');
  rule1.className = 'nooz-divider nooz-aside-quote-rule';
  aside.appendChild(rule1);
  const q = document.createElement('p');
  q.className = 'nooz-aside-quote-text';
  q.textContent = `“${quote.text}”`;
  aside.appendChild(q);
  const attr = document.createElement('p');
  attr.className = 'nooz-aside-quote-attr';
  attr.textContent = quote.sourceTitle;
  aside.appendChild(attr);
  const rule2 = document.createElement('hr');
  rule2.className = 'nooz-divider nooz-aside-quote-rule';
  aside.appendChild(rule2);
  return aside;
}

/** Splices the aside in after the paragraph roughly at the middle of the body -- a stable, deterministic spot, the way a real pull-quote breaks up a long column. */
export function insertFoundQuoteAside(bodyEl, asideNode) {
  const paras = Array.from(bodyEl.children).filter((el) => el.tagName === 'P');
  if (!paras.length) {
    bodyEl.appendChild(asideNode);
    return;
  }
  paras[Math.floor(paras.length / 2)].after(asideNode);
}
