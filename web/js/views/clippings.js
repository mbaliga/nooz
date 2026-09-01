// clippings.js -- the "Clippings" view: articles the reader saved.
//
// Clippings are stored as full Item snapshots (see db.js), so a saved item
// stays readable here even after its source is later removed or disabled --
// state.clippings is that full, unfiltered list, never intersected with the
// enabled-sources-only state.items the Stand uses.
//
// All feed-derived text is only ever assigned via .textContent, matching
// the same "never innerHTML on untrusted content" rule the other views follow.

import { t } from '../i18n.js';

const SVG_NS = 'http://www.w3.org/2000/svg';

/**
 * @param {HTMLElement} container
 * @param {object} state
 * @param {object} actions
 */
export function render(container, state, actions) {
  container.replaceChildren();

  const layout = document.createElement('div');
  layout.className = 'nooz-layout nooz-stack';

  const heading = document.createElement('h1');
  heading.textContent = t('screen_clippings', 'Clippings');
  layout.appendChild(heading);

  const clippings = state.clippings || [];

  if (clippings.length === 0) {
    layout.appendChild(buildEmptyState());
  } else {
    layout.appendChild(buildList(clippings, state, actions));
  }

  container.appendChild(layout);
}

function buildEmptyState() {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-empty-state';

  const title = document.createElement('p');
  title.className = 'nooz-empty-state-title';
  title.textContent = t('clippings_empty', 'Nothing clipped yet');
  wrap.appendChild(title);

  const text = document.createElement('p');
  text.className = 'nooz-empty-state-text';
  text.textContent =
    t('clippings_empty_body', 'Tap the clip icon on any item to save it here — it stays even if you later remove or disable its source.');
  wrap.appendChild(text);

  return wrap;
}

function buildList(clippings, state, actions) {
  const list = document.createElement('ul');
  list.className = 'nooz-list';
  for (const item of clippings) {
    list.appendChild(buildListItem(item, state, actions));
  }
  return list;
}

function buildListItem(item, state, actions) {
  const li = document.createElement('li');
  const isRead = state.readIds.has(item.id);

  const row = document.createElement('div');
  row.className = 'nooz-list-item is-clipped';
  if (isRead) row.classList.add('is-read');

  const topRow = document.createElement('div');
  topRow.className = 'nooz-row nooz-row--sm';
  topRow.style.flexWrap = 'nowrap';
  topRow.style.justifyContent = 'space-between';

  // The title is the row's control, not the row. `role="button"` on the row
  // took its accessible name from everything inside it -- title, source, date
  // and the "Remove clipping" button all read as one string -- and buried a
  // real <button> inside a fake one. A link names itself with just the title
  // and lands in the links list, which is how a clipping gets found again.
  const title = document.createElement('h3');
  title.className = 'nooz-list-item-title';
  if (isRead) {
    const mark = document.createElement('span');
    mark.className = 'nooz-visually-hidden';
    mark.textContent = t('list_read', 'Read') + '. ';
    title.appendChild(mark);
  }
  const link = document.createElement('a');
  link.className = 'nooz-headline-link';
  // setAttribute, not the .href setter: the setter resolves against the
  // document URL and re-serialises, which can decode escapes an item id needs
  // to keep. The attribute is stored exactly as written.
  link.setAttribute('href', `#/reader/${encodeURIComponent(item.id)}`);
  link.textContent = item.title || '(untitled)';
  link.addEventListener('click', (event) => {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey ||
        event.shiftKey || event.altKey) return;
    event.preventDefault();
    actions.openItem(item.id);
  });
  title.appendChild(link);
  topRow.appendChild(title);

  const unclipBtn = document.createElement('button');
  unclipBtn.type = 'button';
  unclipBtn.className = 'nooz-button-icon';
  unclipBtn.setAttribute('aria-pressed', 'true');
  unclipBtn.setAttribute('aria-label', t('clippings_remove', 'Remove clipping'));
  unclipBtn.appendChild(createClipIcon());
  unclipBtn.addEventListener('click', (event) => {
    event.stopPropagation();
    actions.toggleClip(item.id);
  });
  topRow.appendChild(unclipBtn);

  row.appendChild(topRow);
  row.appendChild(buildByline(item, state));

  // The whole row stays a pointer target -- it is just no longer pretending to
  // be a control. Clicks that land on the title link or the unclip button are
  // theirs to handle.
  row.addEventListener('click', (event) => {
    if (event.target.closest('a, button')) return;
    actions.openItem(item.id);
  });

  li.appendChild(row);
  return li;
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
  dateSpan.textContent = formatDate(item.publishedAt);
  byline.appendChild(dateSpan);

  return byline;
}

function formatDate(publishedAt) {
  if (typeof publishedAt !== 'number' || Number.isNaN(publishedAt)) return 'Unknown date';
  const date = new Date(publishedAt);
  if (Number.isNaN(date.getTime())) return 'Unknown date';
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

function createClipIcon() {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 16 16');
  svg.setAttribute('class', 'nooz-icon');
  svg.setAttribute('aria-hidden', 'true');

  const path = document.createElementNS(SVG_NS, 'path');
  path.setAttribute(
    'd',
    'M4.5 2C3.67 2 3 2.67 3 3.5v10.2c0 .4.44.65.78.44L8 11.6l4.22 2.54c.34.21.78-.04.78-.44V3.5c0-.83-.67-1.5-1.5-1.5h-7z'
  );
  path.setAttribute('fill', 'currentColor');
  svg.appendChild(path);

  return svg;
}
