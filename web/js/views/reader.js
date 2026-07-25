// reader.js -- the Reader: one article, set as a single newspaper column.
//
// Body content, best first: a server-extracted full article (state.articles),
// else the feed's own content:encoded HTML (item.contentHtml), else the plain
// summary, else an honest "no content" note. Extracted/feed HTML is run
// through sanitizeHtml (a strict allowlist, parsed inertly) before it ever
// touches the page, and loaded language is then marked in place by lens.js
// when the reader has that setting on.
//
// Feed-derived text is only ever set via .textContent; the one href that
// becomes a link (the source URL) is restricted to http(s) first.

import { sanitizeHtml } from '../sanitize.js';
import { annotate } from '../lens.js';
import { classifyItem, TOPIC_LABEL } from '../topics.js';
import { frameImage, frameBodyImages } from '../images.js';

const SVG_NS = 'http://www.w3.org/2000/svg';

export function render(container, state, actions) {
  container.replaceChildren();

  const layout = document.createElement('div');
  layout.className = 'nooz-reader-layout';

  layout.appendChild(buildBackControl(actions));

  const item = findCurrentItem(state);
  if (!item) {
    layout.appendChild(buildMissingItemEmptyState(state, actions));
    container.appendChild(layout);
    return;
  }

  const article = document.createElement('article');
  article.className = 'nooz-reader';

  article.appendChild(buildKicker(item));
  article.appendChild(buildHeader(item, state, actions));
  article.appendChild(buildByline(item, state));

  const settings = state.settings || {};
  const leadImage = pickLeadImage(item, state);
  if (settings.showImages !== false && leadImage) {
    const figure = frameImage(leadImage, {
      prominent: true,
      className: 'nooz-reader-figure',
      currentStyle: settings.imageStyle || 'halftone',
      onStyle: (s) => actions.updateSetting('imageStyle', s),
    });
    if (figure) article.appendChild(figure);
  }

  article.appendChild(buildBody(item, state));
  article.appendChild(buildSourceSection(item, state));

  layout.appendChild(article);
  container.appendChild(layout);
}

function findCurrentItem(state) {
  if (!state.currentItemId) return null;
  const inView = (state.items || []).find((it) => it.id === state.currentItemId);
  if (inView) return inView;
  // Fall back to the full history / clippings so an item stays readable even
  // when a search or region filter has projected it out of the Stand list.
  return (
    (state.allItems || []).find((it) => it.id === state.currentItemId) ||
    (state.clippings || []).find((it) => it.id === state.currentItemId) ||
    null
  );
}

// ---------------------------------------------------------------------------
// Back control
// ---------------------------------------------------------------------------

function buildBackControl(actions) {
  const row = document.createElement('div');
  row.className = 'nooz-reader-back';

  const backBtn = document.createElement('button');
  backBtn.type = 'button';
  backBtn.className = 'nooz-button';
  backBtn.setAttribute('aria-label', 'Back to your stand');
  backBtn.appendChild(createBackIcon());
  const label = document.createElement('span');
  label.textContent = 'Back to your stand';
  backBtn.appendChild(label);
  backBtn.addEventListener('click', () => actions.goTo('stand'));
  row.appendChild(backBtn);

  return row;
}

function buildMissingItemEmptyState(state, actions) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-empty-state';
  const hadTarget = Boolean(state.currentItemId);

  const title = document.createElement('p');
  title.className = 'nooz-empty-state-title';
  title.textContent = hadTarget ? "This item isn't here anymore" : 'Nothing to read yet';
  wrap.appendChild(title);

  const text = document.createElement('p');
  text.className = 'nooz-empty-state-text';
  text.textContent = hadTarget
    ? "It may have dropped out of view since you opened it. Go back to your stand to find something to read."
    : 'Pick a story from your stand to read it here.';
  wrap.appendChild(text);

  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'nooz-button nooz-button--primary';
  btn.textContent = 'Back to your stand';
  btn.addEventListener('click', () => actions.goTo('stand'));
  wrap.appendChild(btn);

  return wrap;
}

// ---------------------------------------------------------------------------
// Header / kicker / byline
// ---------------------------------------------------------------------------

function buildKicker(item) {
  const k = document.createElement('p');
  k.className = 'nooz-kicker nooz-reader-kicker';
  k.textContent = TOPIC_LABEL[classifyItem(item)] || 'General';
  return k;
}

function buildHeader(item, state, actions) {
  const header = document.createElement('div');
  header.className = 'nooz-reader-header';

  const title = document.createElement('h1');
  title.className = 'nooz-reader-title';
  title.textContent = item.title || '(untitled)';
  header.appendChild(title);

  header.appendChild(buildReaderActions(item, state, actions));
  return header;
}

function buildReaderActions(item, state, actions) {
  const row = document.createElement('div');
  row.className = 'nooz-reader-actions';

  const isClipped = state.clippedIds.has(item.id);

  const shareBtn = document.createElement('button');
  shareBtn.type = 'button';
  shareBtn.className = 'nooz-button-icon';
  shareBtn.setAttribute('aria-label', 'Share this item');
  shareBtn.appendChild(createShareIcon());
  shareBtn.addEventListener('click', () => actions.shareItem(item.id));
  row.appendChild(shareBtn);

  const clipBtn = document.createElement('button');
  clipBtn.type = 'button';
  clipBtn.className = 'nooz-button-icon';
  clipBtn.setAttribute('aria-pressed', isClipped ? 'true' : 'false');
  clipBtn.setAttribute('aria-label', isClipped ? 'Remove clipping' : 'Clip this item');
  clipBtn.appendChild(createClipIcon(isClipped));
  clipBtn.addEventListener('click', () => actions.toggleClip(item.id));
  row.appendChild(clipBtn);

  return row;
}

function buildByline(item, state) {
  const byline = document.createElement('div');
  byline.className = 'nooz-byline nooz-reader-byline';

  const extracted = state.articles && state.articles[item.id];
  const authorText = item.author || (extracted && extracted.byline) || null;
  if (authorText) {
    const authorSpan = document.createElement('span');
    authorSpan.textContent = authorText;
    byline.appendChild(authorSpan);
    const sep = document.createElement('span');
    sep.textContent = '|';
    byline.appendChild(sep);
  }

  const source = (state.sources || []).find((s) => s.id === item.sourceId);
  const sourceSpan = document.createElement('span');
  sourceSpan.textContent = source ? source.title : 'Unknown source';
  byline.appendChild(sourceSpan);

  const dateSpan = document.createElement('span');
  dateSpan.className = 'nooz-byline-dot';
  dateSpan.textContent = formatFullDate(item.publishedAt);
  byline.appendChild(dateSpan);

  return byline;
}

function pickLeadImage(item, state) {
  const extracted = state.articles && state.articles[item.id];
  if (extracted && extracted.leadImage) return extracted.leadImage;
  return item.image || null;
}

// ---------------------------------------------------------------------------
// Body -- extracted article > feed HTML > plain summary
// ---------------------------------------------------------------------------

function buildBody(item, state) {
  const body = document.createElement('div');
  body.className = 'nooz-reader-body';

  const settings = state.settings || {};
  const status = (state.articleStatus || {})[item.id];
  const extracted = state.articles && state.articles[item.id];

  let renderedHtml = false;

  if (extracted && extracted.html) {
    body.appendChild(sanitizeHtml(extracted.html, { allowImages: settings.showImages !== false }));
    renderedHtml = true;
  } else if (item.contentHtml) {
    body.appendChild(sanitizeHtml(item.contentHtml, { allowImages: settings.showImages !== false }));
    renderedHtml = true;
  } else if (item.summary) {
    for (const para of splitParagraphs(item.summary)) {
      const p = document.createElement('p');
      p.textContent = para;
      body.appendChild(p);
    }
    renderedHtml = true;
  }

  // If the body is thin and extraction is still running (or just failed),
  // say so honestly rather than leaving a near-empty page unexplained.
  if (status === 'loading') {
    const loading = document.createElement('p');
    loading.className = 'nooz-reader-loading';
    loading.textContent = 'Fetching the full article…';
    body.appendChild(loading);
  } else if (!renderedHtml) {
    const p = document.createElement('p');
    p.className = 'nooz-reader-thin';
    p.textContent = "This source's feed didn't include the article text. Read the full piece at the source below.";
    body.appendChild(p);
  }

  // Give body images the same halftone/B&W/colour frame as the lead, then mark
  // loaded language in place when the reader wants it.
  if (settings.showImages !== false) frameBodyImages(body);
  if (settings.highlightLoaded !== false) annotate(body);

  return body;
}

function splitParagraphs(text) {
  const parts = text
    .split(/\n\s*\n/)
    .map((p) => p.trim())
    .filter(Boolean);
  return parts.length ? parts : [text.trim()];
}

// ---------------------------------------------------------------------------
// Read at the source
// ---------------------------------------------------------------------------

function buildSourceSection(item, state) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-reader-source';

  const divider = document.createElement('hr');
  divider.className = 'nooz-divider';
  wrap.appendChild(divider);

  const url = safeHttpUrl(item.link);
  if (url) {
    const link = document.createElement('a');
    link.className = 'nooz-button nooz-button--primary';
    link.href = url;
    link.target = '_blank';
    link.rel = 'noopener noreferrer';
    link.textContent = 'Read at the source';
    wrap.appendChild(link);
  } else {
    const missing = document.createElement('p');
    missing.className = 'nooz-empty-state-text';
    missing.style.margin = '0';
    missing.textContent = "This item's feed didn't include a usable link back to its source.";
    wrap.appendChild(missing);
  }

  return wrap;
}

function safeHttpUrl(link) {
  if (!link || typeof link !== 'string') return null;
  try {
    const parsed = new URL(link, window.location.href);
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:') return parsed.href;
  } catch (_err) {
    /* unparseable */
  }
  return null;
}

// ---------------------------------------------------------------------------
// Icons
// ---------------------------------------------------------------------------

function createBackIcon() {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 16 16');
  svg.setAttribute('class', 'nooz-icon');
  svg.setAttribute('aria-hidden', 'true');
  const path = document.createElementNS(SVG_NS, 'path');
  path.setAttribute('d', 'M9.5 3L5 8l4.5 5');
  path.setAttribute('fill', 'none');
  path.setAttribute('stroke', 'currentColor');
  path.setAttribute('stroke-width', '1.3');
  path.setAttribute('stroke-linecap', 'round');
  path.setAttribute('stroke-linejoin', 'round');
  svg.appendChild(path);
  return svg;
}

function createShareIcon() {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 16 16');
  svg.setAttribute('class', 'nooz-icon');
  svg.setAttribute('aria-hidden', 'true');
  const tray = document.createElementNS(SVG_NS, 'path');
  tray.setAttribute('d', 'M3.5 7.5v4.3c0 .66.54 1.2 1.2 1.2h6.6c.66 0 1.2-.54 1.2-1.2V7.5');
  tray.setAttribute('fill', 'none');
  tray.setAttribute('stroke', 'currentColor');
  tray.setAttribute('stroke-width', '1.2');
  tray.setAttribute('stroke-linecap', 'round');
  tray.setAttribute('stroke-linejoin', 'round');
  svg.appendChild(tray);
  const arrow = document.createElementNS(SVG_NS, 'path');
  arrow.setAttribute('d', 'M8 9.5V2M8 2L5.6 4.4M8 2l2.4 2.4');
  arrow.setAttribute('fill', 'none');
  arrow.setAttribute('stroke', 'currentColor');
  arrow.setAttribute('stroke-width', '1.2');
  arrow.setAttribute('stroke-linecap', 'round');
  arrow.setAttribute('stroke-linejoin', 'round');
  svg.appendChild(arrow);
  return svg;
}

function createClipIcon(isClipped) {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 16 16');
  svg.setAttribute('class', 'nooz-icon');
  svg.setAttribute('aria-hidden', 'true');
  const path = document.createElementNS(SVG_NS, 'path');
  path.setAttribute(
    'd',
    'M4.5 2C3.67 2 3 2.67 3 3.5v10.2c0 .4.44.65.78.44L8 11.6l4.22 2.54c.34.21.78-.04.78-.44V3.5c0-.83-.67-1.5-1.5-1.5h-7z'
  );
  if (isClipped) {
    path.setAttribute('fill', 'currentColor');
  } else {
    path.setAttribute('fill', 'none');
    path.setAttribute('stroke', 'currentColor');
    path.setAttribute('stroke-width', '1.2');
    path.setAttribute('stroke-linejoin', 'round');
  }
  svg.appendChild(path);
  return svg;
}

function formatFullDate(publishedAt) {
  if (typeof publishedAt !== 'number' || Number.isNaN(publishedAt)) return 'Unknown date';
  const date = new Date(publishedAt);
  if (Number.isNaN(date.getTime())) return 'Unknown date';
  return date.toLocaleString(undefined, {
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}
