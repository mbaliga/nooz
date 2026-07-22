// reader.js -- the Reader view: a single article, read one at a time.
//
// This view shows exactly one Item: whichever one state.currentItemId points
// at. It never re-filters or re-fetches anything -- that's app.js's job. If
// the item named by currentItemId isn't in state.items (its source was
// removed, or a search/region change since it was opened filtered it back
// out), that's not a bug to hide: it's told to the reader plainly, with a
// clear way back to the stand.
//
// There is no full-content extraction in this app (that's a bigger, separate
// piece of work). What's shown here is only whatever the feed itself
// provided as a summary -- so the "Read at the source" link is the primary
// way to read past it, and this view says so rather than pretending the
// summary is the whole story.
//
// All feed-derived text (title, author, source title, summary) is untrusted
// external content and is only ever assigned via .textContent -- never
// innerHTML. The one place untrusted data becomes markup at all is the
// source-link href, which is restricted to plain http(s) URLs before it's
// ever used, so a feed can't smuggle a javascript: URI into a clickable link.

const SVG_NS = 'http://www.w3.org/2000/svg';

/**
 * @param {HTMLElement} container
 * @param {object} state
 * @param {object} actions
 */
export function render(container, state, actions) {
  container.innerHTML = '';

  const layout = document.createElement('div');
  layout.className = 'nooz-layout nooz-stack';

  layout.appendChild(buildBackControl(actions));

  const item = findCurrentItem(state);

  if (!item) {
    layout.appendChild(buildMissingItemEmptyState(state, actions));
    container.appendChild(layout);
    return;
  }

  const article = document.createElement('article');
  article.className = 'nooz-reader';

  article.appendChild(buildHeader(item, state, actions));
  article.appendChild(buildBody(item));

  const divider = document.createElement('hr');
  divider.className = 'nooz-divider';
  article.appendChild(divider);

  article.appendChild(buildSourceSection(item));

  layout.appendChild(article);
  container.appendChild(layout);
}

function findCurrentItem(state) {
  if (!state.currentItemId) return null;
  const items = state.items || [];
  return items.find((it) => it.id === state.currentItemId) || null;
}

// ---------------------------------------------------------------------------
// Back control
// ---------------------------------------------------------------------------

function buildBackControl(actions) {
  const row = document.createElement('div');
  row.className = 'nooz-row nooz-row--xs';

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

// ---------------------------------------------------------------------------
// Missing-item empty state -- never a silent blank page
// ---------------------------------------------------------------------------

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
    ? "It may have dropped out of view since you opened it -- a search or region filter change, or its source being removed. Go back to your stand to find something to read."
    : 'Pick an item from your stand to read it here.';
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
// Header: headline, byline, share/clip actions
// ---------------------------------------------------------------------------

function buildHeader(item, state, actions) {
  const header = document.createElement('div');
  header.className = 'nooz-reader-header';

  const topRow = document.createElement('div');
  topRow.className = 'nooz-row';
  topRow.style.flexWrap = 'nowrap';
  topRow.style.alignItems = 'flex-start';
  topRow.style.justifyContent = 'space-between';

  const title = document.createElement('h1');
  title.className = 'nooz-reader-title';
  title.style.flex = '1';
  title.style.minWidth = '0';
  title.textContent = item.title || '(untitled)';
  topRow.appendChild(title);

  topRow.appendChild(buildReaderActions(item, state, actions));

  header.appendChild(topRow);
  header.appendChild(buildByline(item, state));

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
  byline.className = 'nooz-byline';

  if (item.author) {
    const authorSpan = document.createElement('span');
    authorSpan.textContent = item.author;
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

// ---------------------------------------------------------------------------
// Body -- plain paragraph text from the feed's summary, nothing more
// ---------------------------------------------------------------------------

function buildBody(item) {
  const body = document.createElement('div');
  body.className = 'nooz-reader-body';

  const summary = typeof item.summary === 'string' ? item.summary.trim() : '';

  if (!summary) {
    const p = document.createElement('p');
    p.style.color = 'var(--paper-ink-dim)';
    p.textContent = "This source's feed doesn't include full content -- read it at the source.";
    body.appendChild(p);
    return body;
  }

  const paragraphs = summary
    .split(/\n\s*\n/)
    .map((part) => part.trim())
    .filter((part) => part.length > 0);

  if (paragraphs.length === 0) paragraphs.push(summary);

  for (const paragraph of paragraphs) {
    const p = document.createElement('p');
    p.textContent = paragraph;
    body.appendChild(p);
  }

  return body;
}

// ---------------------------------------------------------------------------
// "Read at the source" -- the primary way to read past the summary
// ---------------------------------------------------------------------------

function buildSourceSection(item) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-stack nooz-stack--sm';

  const note = document.createElement('p');
  note.className = 'nooz-summary';
  note.textContent =
    "This reader only shows what the source's feed provided -- there's no full-article extraction here yet. Read the complete piece at the source.";
  wrap.appendChild(note);

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

/** Only ever treat plain http(s) URLs as links -- feed data is untrusted. */
function safeHttpUrl(link) {
  if (!link || typeof link !== 'string') return null;
  try {
    const parsed = new URL(link, window.location.href);
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:') return parsed.href;
  } catch (_err) {
    // unparseable -- treat as no link
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

// ---------------------------------------------------------------------------
// Date formatting -- full, unambiguous (unlike the short form in the list)
// ---------------------------------------------------------------------------

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
