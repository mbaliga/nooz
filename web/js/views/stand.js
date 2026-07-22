// stand.js -- the "Stand" view: the main article list.
//
// This is the front page of the app -- everything that has flowed in from
// the reader's chosen sources, filtered by whatever search/region the reader
// has set. state.items arrives already filtered (search + region) per the
// view contract; this module's job is purely to lay it out, never to
// re-filter it.
//
// "Never a silent blank": three distinct empty states below (no sources at
// all, nothing fetched yet, nothing matching a search) so the reader is
// always told *why* the list is empty, and per-source fetch failures get
// their own small, honest notice instead of just vanishing from the list.
//
// All feed-derived text (titles, author names, source titles, error
// strings) is untrusted external content and is only ever assigned via
// .textContent -- never innerHTML -- so a malicious feed can't inject
// markup into the page.

/**
 * @param {HTMLElement} container
 * @param {object} state
 * @param {object} actions
 */
export function render(container, state, actions) {
  container.innerHTML = '';

  const layout = document.createElement('div');
  layout.className = 'nooz-layout nooz-stack';

  layout.appendChild(buildSearchRow(state, actions));

  if (state.sources.length === 0) {
    layout.appendChild(buildNoSourcesEmptyState(actions));
    container.appendChild(layout);
    return;
  }

  const notice = buildFetchErrorNotice(state);
  if (notice) layout.appendChild(notice);

  layout.appendChild(buildRegionChipRow(state, actions));

  if (state.items.length === 0) {
    if (state.searchQuery) {
      layout.appendChild(buildNoResultsEmptyState(state));
    } else {
      layout.appendChild(buildNothingFlowedEmptyState(actions));
    }
  } else {
    layout.appendChild(buildList(state, actions));
  }

  container.appendChild(layout);
}

// ---------------------------------------------------------------------------
// Search + refresh row (the app-level nav bar carries the wordmark/nav; this
// row is Stand-specific and scrolls with the list beneath it)
// ---------------------------------------------------------------------------

function buildSearchRow(state, actions) {
  const group = document.createElement('div');
  group.className = 'nooz-input-group';

  const searchInput = document.createElement('input');
  searchInput.type = 'search';
  searchInput.className = 'nooz-input';
  searchInput.placeholder = 'Search your stand';
  searchInput.value = state.searchQuery || '';
  searchInput.setAttribute('aria-label', 'Search your stand');
  searchInput.addEventListener('input', (event) => {
    actions.setSearchQuery(event.target.value);
  });
  group.appendChild(searchInput);

  const refreshBtn = document.createElement('button');
  refreshBtn.type = 'button';
  refreshBtn.className = 'nooz-button';
  refreshBtn.textContent = 'Refresh';
  refreshBtn.setAttribute('aria-label', 'Refresh all sources');
  refreshBtn.addEventListener('click', () => actions.refreshAll());
  group.appendChild(refreshBtn);

  return group;
}

// ---------------------------------------------------------------------------
// Region filter row
// ---------------------------------------------------------------------------

function buildRegionChipRow(state, actions) {
  const regionsSet = new Set();
  for (const source of state.sources) {
    if (source.region) regionsSet.add(source.region);
  }
  const regions = Array.from(regionsSet).sort((a, b) => a.localeCompare(b));

  const row = document.createElement('div');
  row.className = 'nooz-chip-row';
  row.setAttribute('role', 'group');
  row.setAttribute('aria-label', 'Filter by region');

  row.appendChild(
    createChip('All', state.regionFilter === null, () => actions.setRegionFilter(null))
  );
  for (const region of regions) {
    row.appendChild(
      createChip(region, state.regionFilter === region, () => actions.setRegionFilter(region))
    );
  }

  return row;
}

function createChip(label, active, onClick) {
  const chip = document.createElement('button');
  chip.type = 'button';
  chip.className = active ? 'nooz-chip is-active' : 'nooz-chip';
  chip.textContent = label;
  chip.setAttribute('aria-pressed', active ? 'true' : 'false');
  chip.addEventListener('click', onClick);
  return chip;
}

// ---------------------------------------------------------------------------
// Fetch-error notice -- never hide a failure silently
// ---------------------------------------------------------------------------

function buildFetchErrorNotice(state) {
  const fetchStatus = state.fetchStatus || {};
  const fetchErrors = state.fetchErrors || {};

  const failed = state.sources.filter(
    (source) => source.enabled && fetchStatus[source.id] === 'error'
  );
  if (failed.length === 0) return null;

  const notice = document.createElement('div');
  notice.className = 'nooz-stack nooz-stack--xs';
  notice.setAttribute('role', 'status');
  notice.style.paddingBottom = 'var(--space-sm)';
  notice.style.borderBottom = '1px solid var(--paper-hairline)';

  const label = document.createElement('p');
  label.className = 'nooz-section-title';
  label.style.color = 'var(--paper-signal-danger)';
  label.textContent =
    failed.length === 1 ? "Couldn't reach 1 source" : `Couldn't reach ${failed.length} sources`;
  notice.appendChild(label);

  for (const source of failed) {
    const line = document.createElement('p');
    line.className = 'nooz-byline';
    line.style.color = 'var(--paper-signal-danger)';

    const titleSpan = document.createElement('span');
    titleSpan.textContent = source.title;
    line.appendChild(titleSpan);

    const reasonSpan = document.createElement('span');
    reasonSpan.className = 'nooz-byline-dot';
    reasonSpan.textContent = fetchErrors[source.id] || 'Unknown error';
    line.appendChild(reasonSpan);

    notice.appendChild(line);
  }

  return notice;
}

// ---------------------------------------------------------------------------
// The list
// ---------------------------------------------------------------------------

function buildList(state, actions) {
  const list = document.createElement('ul');
  list.className = 'nooz-list';

  for (const item of state.items) {
    list.appendChild(buildListItem(item, state, actions));
  }

  return list;
}

function buildListItem(item, state, actions) {
  const li = document.createElement('li');

  const isRead = state.readIds.has(item.id);
  const isClipped = state.clippedIds.has(item.id);

  const row = document.createElement('div');
  row.className = 'nooz-list-item';
  if (isRead) row.classList.add('is-read');
  if (isClipped) row.classList.add('is-clipped');
  row.setAttribute('role', 'button');
  row.setAttribute('tabindex', '0');

  const topRow = document.createElement('div');
  topRow.className = 'nooz-row nooz-row--sm';
  topRow.style.flexWrap = 'nowrap';
  topRow.style.justifyContent = 'space-between';

  const title = document.createElement('span');
  title.className = 'nooz-list-item-title';
  title.textContent = item.title || '(untitled)';
  topRow.appendChild(title);

  const clipBtn = document.createElement('button');
  clipBtn.type = 'button';
  clipBtn.className = 'nooz-button-icon';
  clipBtn.setAttribute('aria-pressed', isClipped ? 'true' : 'false');
  clipBtn.setAttribute('aria-label', isClipped ? 'Remove clipping' : 'Clip this item');
  clipBtn.appendChild(createClipIcon(isClipped));
  clipBtn.addEventListener('click', (event) => {
    event.stopPropagation();
    actions.toggleClip(item.id);
  });
  topRow.appendChild(clipBtn);

  row.appendChild(topRow);
  row.appendChild(buildByline(item, state));

  const activate = () => actions.openItem(item.id);
  row.addEventListener('click', activate);
  row.addEventListener('keydown', (event) => {
    if (event.target !== row) return; // let the nested clip button handle its own keys
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      activate();
    }
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

  const source = state.sources.find((s) => s.id === item.sourceId);
  const sourceSpan = document.createElement('span');
  sourceSpan.textContent = source ? source.title : 'Unknown source';
  byline.appendChild(sourceSpan);

  const dateSpan = document.createElement('span');
  dateSpan.className = 'nooz-byline-dot';
  dateSpan.textContent = formatShortDate(item.publishedAt);
  byline.appendChild(dateSpan);

  return byline;
}

function createClipIcon(isClipped) {
  const SVG_NS = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 16 16');
  svg.setAttribute('class', 'nooz-icon');
  svg.setAttribute('aria-hidden', 'true');

  const path = document.createElementNS(SVG_NS, 'path');
  path.setAttribute('d', 'M4.5 2C3.67 2 3 2.67 3 3.5v10.2c0 .4.44.65.78.44L8 11.6l4.22 2.54c.34.21.78-.04.78-.44V3.5c0-.83-.67-1.5-1.5-1.5h-7z');
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
// Empty states
// ---------------------------------------------------------------------------

function buildNoSourcesEmptyState(actions) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-empty-state';

  const title = document.createElement('p');
  title.className = 'nooz-empty-state-title';
  title.textContent = 'Nothing on your stand yet';
  wrap.appendChild(title);

  const text = document.createElement('p');
  text.className = 'nooz-empty-state-text';
  text.textContent =
    'Add a feed by URL, or pick from a short list of pre-checked starter sources -- this is where what flows in from them will show up.';
  wrap.appendChild(text);

  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'nooz-button nooz-button--primary';
  btn.textContent = 'Add sources';
  btn.addEventListener('click', () => actions.goTo('sources'));
  wrap.appendChild(btn);

  return wrap;
}

function buildNothingFlowedEmptyState(actions) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-empty-state';

  const title = document.createElement('p');
  title.className = 'nooz-empty-state-title';
  title.textContent = 'Nothing has flowed yet';
  wrap.appendChild(title);

  const text = document.createElement('p');
  text.className = 'nooz-empty-state-text';
  text.textContent = "Nothing has flowed yet -- tap refresh to check your sources.";
  wrap.appendChild(text);

  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'nooz-button';
  btn.textContent = 'Refresh';
  btn.addEventListener('click', () => actions.refreshAll());
  wrap.appendChild(btn);

  return wrap;
}

function buildNoResultsEmptyState(state) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-empty-state';

  const title = document.createElement('p');
  title.className = 'nooz-empty-state-title';
  title.textContent = 'No results';
  wrap.appendChild(title);

  const text = document.createElement('p');
  text.className = 'nooz-empty-state-text';
  text.textContent = `No results for "${state.searchQuery}".`;
  wrap.appendChild(text);

  return wrap;
}

// ---------------------------------------------------------------------------
// Date formatting -- short, relative for recent items, calendar-ish past that
// ---------------------------------------------------------------------------

function formatShortDate(publishedAt) {
  const diffMs = Date.now() - publishedAt;
  const diffMin = Math.floor(diffMs / 60000);

  if (diffMin <= 0) return 'just now';
  if (diffMin < 60) return `${diffMin}m`;

  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return `${diffHr}h`;

  const diffDay = Math.floor(diffHr / 24);
  if (diffDay < 7) return `${diffDay}d`;

  const date = new Date(publishedAt);
  const now = new Date();
  const options = { month: 'short', day: 'numeric' };
  if (date.getFullYear() !== now.getFullYear()) options.year = 'numeric';
  return date.toLocaleDateString(undefined, options);
}
