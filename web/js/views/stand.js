// stand.js -- the "Stand": a newspaper front page. A masthead across the top
// (the Nooz wordmark between hairline rules, with an edition line of date ·
// sources · region), a sources rail down the left (the reader's chosen feeds,
// standing behind the paper the way a masthead's contributor column does), and
// the day's stories set in newspaper columns to the right -- a lead story up
// top, the rest flowing beneath it.
//
// state.items arrives already filtered (enabled sources → region → search);
// this module only lays it out. All feed-derived text is assigned via
// .textContent (never innerHTML), and images come only from item.image, which
// feeds.js already restricted to https URLs.

import { classifyItem, TOPIC_LABEL } from '../topics.js';

export function render(container, state, actions) {
  container.replaceChildren();

  const front = document.createElement('div');
  front.className = 'nooz-front';

  front.appendChild(buildRail(state, actions));

  const paper = document.createElement('div');
  paper.className = 'nooz-paper';
  paper.appendChild(buildMasthead(state));

  if (state.sources.length === 0) {
    paper.appendChild(buildNoSourcesEmptyState(actions));
  } else {
    const notice = buildFetchErrorNotice(state);
    if (notice) paper.appendChild(notice);

    if (state.items.length === 0) {
      paper.appendChild(
        state.searchQuery ? buildNoResultsEmptyState(state) : buildNothingFlowedEmptyState(actions)
      );
    } else {
      paper.appendChild(buildFrontPage(state, actions));
    }
  }

  front.appendChild(paper);
  container.appendChild(front);
}

// ---------------------------------------------------------------------------
// Left rail: search + refresh, region filter, the sources themselves
// ---------------------------------------------------------------------------

function buildRail(state, actions) {
  const rail = document.createElement('aside');
  rail.className = 'nooz-rail';
  rail.setAttribute('aria-label', 'Your sources');

  const search = document.createElement('input');
  search.type = 'search';
  search.className = 'nooz-input nooz-rail-search';
  search.placeholder = 'Search';
  search.value = state.searchQuery || '';
  search.setAttribute('aria-label', 'Search your stand');
  search.addEventListener('input', (e) => actions.setSearchQuery(e.target.value));
  rail.appendChild(search);

  const regions = Array.from(
    new Set(state.sources.filter((s) => s.region).map((s) => s.region))
  ).sort((a, b) => a.localeCompare(b));
  if (regions.length > 0) {
    const chipRow = document.createElement('div');
    chipRow.className = 'nooz-rail-regions';
    chipRow.appendChild(regionChip('All', state.regionFilter === null, () => actions.setRegionFilter(null)));
    for (const region of regions) {
      chipRow.appendChild(
        regionChip(region, state.regionFilter === region, () => actions.setRegionFilter(region))
      );
    }
    rail.appendChild(chipRow);
  }

  const heading = document.createElement('p');
  heading.className = 'nooz-rail-heading';
  heading.textContent = 'Sources';
  rail.appendChild(heading);

  const list = document.createElement('ul');
  list.className = 'nooz-rail-sources';
  const sorted = state.sources.slice().sort((a, b) => (a.title || '').localeCompare(b.title || ''));
  for (const source of sorted) {
    list.appendChild(buildRailSource(source, state, actions));
  }
  rail.appendChild(list);

  const manage = document.createElement('button');
  manage.type = 'button';
  manage.className = 'nooz-button nooz-rail-manage';
  manage.textContent = state.sources.length ? 'Manage sources' : 'Add sources';
  manage.addEventListener('click', () => actions.goTo('sources'));
  rail.appendChild(manage);

  const refresh = document.createElement('button');
  refresh.type = 'button';
  refresh.className = 'nooz-button nooz-rail-refresh';
  refresh.textContent = 'Refresh';
  refresh.addEventListener('click', () => actions.refreshAll());
  rail.appendChild(refresh);

  return rail;
}

function regionChip(label, active, onClick) {
  const chip = document.createElement('button');
  chip.type = 'button';
  chip.className = active ? 'nooz-chip is-active' : 'nooz-chip';
  chip.textContent = label;
  chip.setAttribute('aria-pressed', active ? 'true' : 'false');
  chip.addEventListener('click', onClick);
  return chip;
}

function buildRailSource(source, state, actions) {
  const li = document.createElement('li');
  const row = document.createElement('button');
  row.type = 'button';
  row.className = 'nooz-rail-source';
  if (!source.enabled) row.classList.add('is-off');
  row.setAttribute('aria-pressed', source.enabled ? 'true' : 'false');
  row.setAttribute('aria-label', `${source.enabled ? 'Disable' : 'Enable'} ${source.title || source.url}`);

  const dot = document.createElement('span');
  dot.className = 'nooz-rail-dot';
  const status = (state.fetchStatus || {})[source.id];
  if (status === 'error') dot.classList.add('is-error');
  else if (status === 'loading') dot.classList.add('is-loading');
  row.appendChild(dot);

  const name = document.createElement('span');
  name.className = 'nooz-rail-source-name';
  name.textContent = source.title || source.url;
  row.appendChild(name);

  row.addEventListener('click', () => actions.toggleSourceEnabled(source.id));
  li.appendChild(row);
  return li;
}

// ---------------------------------------------------------------------------
// Masthead
// ---------------------------------------------------------------------------

function buildMasthead(state) {
  const masthead = document.createElement('header');
  masthead.className = 'nooz-masthead';

  const topRule = document.createElement('div');
  topRule.className = 'nooz-masthead-rule';
  masthead.appendChild(topRule);

  const wordmark = document.createElement('h1');
  wordmark.className = 'nooz-masthead-wordmark';
  wordmark.textContent = 'Nooz';
  masthead.appendChild(wordmark);

  const edition = document.createElement('p');
  edition.className = 'nooz-masthead-edition';
  const enabled = state.sources.filter((s) => s.enabled).length;
  const parts = [
    fullToday(),
    `${enabled} ${enabled === 1 ? 'source' : 'sources'}`,
    state.regionFilter || 'All regions',
  ];
  edition.textContent = parts.join('  ·  ');
  masthead.appendChild(edition);

  const botRule = document.createElement('div');
  botRule.className = 'nooz-masthead-rule nooz-masthead-rule--double';
  masthead.appendChild(botRule);

  return masthead;
}

function fullToday() {
  return new Date().toLocaleDateString(undefined, {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    year: 'numeric',
  });
}

// ---------------------------------------------------------------------------
// Front page: a lead story, then columns
// ---------------------------------------------------------------------------

function buildFrontPage(state, actions) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-frontpage';

  const items = state.items;
  const showImages = !state.settings || state.settings.showImages;

  // Lead: prefer the newest item that actually has an image, so the front page
  // has a picture; fall back to the newest item.
  let leadIndex = 0;
  if (showImages) {
    const withImg = items.findIndex((it) => it.image);
    if (withImg >= 0 && withImg < 6) leadIndex = withImg;
  }
  const lead = items[leadIndex];
  wrap.appendChild(buildLeadStory(lead, state, actions, showImages));

  const rest = items.filter((_, i) => i !== leadIndex);
  if (rest.length > 0) {
    const columns = document.createElement('div');
    columns.className = 'nooz-columns';
    for (const item of rest) {
      columns.appendChild(buildColumnStory(item, state, actions, showImages));
    }
    wrap.appendChild(columns);
  }

  return wrap;
}

function buildLeadStory(item, state, actions, showImages) {
  const story = document.createElement('article');
  story.className = 'nooz-lead';
  story.setAttribute('role', 'button');
  story.setAttribute('tabindex', '0');
  if (state.readIds.has(item.id)) story.classList.add('is-read');

  if (showImages && item.image) {
    const figure = document.createElement('figure');
    figure.className = 'nooz-lead-figure';
    const img = document.createElement('img');
    img.className = 'nooz-photo';
    img.loading = 'lazy';
    img.alt = '';
    img.src = item.image;
    img.addEventListener('error', () => figure.remove());
    figure.appendChild(img);
    story.appendChild(figure);
  }

  const body = document.createElement('div');
  body.className = 'nooz-lead-body';
  body.appendChild(kicker(item));

  const headline = document.createElement('h2');
  headline.className = 'nooz-lead-headline';
  headline.textContent = item.title || '(untitled)';
  body.appendChild(headline);

  if (item.summary) {
    const dek = document.createElement('p');
    dek.className = 'nooz-lead-dek';
    dek.textContent = clampText(item.summary, 320);
    body.appendChild(dek);
  }

  body.appendChild(byline(item, state));
  story.appendChild(body);

  wire(story, () => actions.openItem(item.id));
  return story;
}

function buildColumnStory(item, state, actions, showImages) {
  const story = document.createElement('article');
  story.className = 'nooz-col-story';
  story.setAttribute('role', 'button');
  story.setAttribute('tabindex', '0');
  if (state.readIds.has(item.id)) story.classList.add('is-read');

  story.appendChild(kicker(item));

  const headline = document.createElement('h3');
  headline.className = 'nooz-col-headline';
  headline.textContent = item.title || '(untitled)';
  story.appendChild(headline);

  if (showImages && item.image) {
    const img = document.createElement('img');
    img.className = 'nooz-photo nooz-col-photo';
    img.loading = 'lazy';
    img.alt = '';
    img.src = item.image;
    img.addEventListener('error', () => img.remove());
    story.appendChild(img);
  }

  if (item.summary) {
    const dek = document.createElement('p');
    dek.className = 'nooz-col-dek';
    dek.textContent = clampText(item.summary, 160);
    story.appendChild(dek);
  }

  story.appendChild(byline(item, state));
  wire(story, () => actions.openItem(item.id));
  return story;
}

function kicker(item) {
  const k = document.createElement('p');
  k.className = 'nooz-kicker';
  k.textContent = TOPIC_LABEL[classifyItem(item)] || 'General';
  return k;
}

function byline(item, state) {
  const line = document.createElement('p');
  line.className = 'nooz-byline';

  const source = (state.sources || []).find((s) => s.id === item.sourceId);
  const src = document.createElement('span');
  src.textContent = source ? source.title : 'Unknown source';
  line.appendChild(src);

  const when = document.createElement('span');
  when.className = 'nooz-byline-dot';
  when.textContent = formatShortDate(item.publishedAt);
  line.appendChild(when);

  return line;
}

function wire(el, activate) {
  el.addEventListener('click', activate);
  el.addEventListener('keydown', (event) => {
    if (event.target !== el) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      activate();
    }
  });
}

function clampText(text, max) {
  const t = (text || '').trim();
  if (t.length <= max) return t;
  return t.slice(0, max).replace(/\s+\S*$/, '') + '…';
}

// ---------------------------------------------------------------------------
// Fetch-error notice + empty states
// ---------------------------------------------------------------------------

function buildFetchErrorNotice(state) {
  const fetchStatus = state.fetchStatus || {};
  const fetchErrors = state.fetchErrors || {};
  const failed = state.sources.filter((s) => s.enabled && fetchStatus[s.id] === 'error');
  if (failed.length === 0) return null;

  const notice = document.createElement('div');
  notice.className = 'nooz-notice';
  notice.setAttribute('role', 'status');

  const label = document.createElement('p');
  label.className = 'nooz-notice-title';
  label.textContent =
    failed.length === 1 ? "Couldn't reach 1 source" : `Couldn't reach ${failed.length} sources`;
  notice.appendChild(label);

  const names = document.createElement('p');
  names.className = 'nooz-notice-detail';
  names.textContent = failed.map((s) => s.title).join(', ');
  notice.appendChild(names);

  return notice;
}

function buildNoSourcesEmptyState(actions) {
  const wrap = emptyState(
    'Nothing on your stand yet',
    'Add a feed, or pick from a short list of pre-checked starter sources -- this is where what flows in from them will be set in print.'
  );
  wrap.appendChild(primaryButton('Add sources', () => actions.goTo('sources')));
  return wrap;
}

function buildNothingFlowedEmptyState(actions) {
  const wrap = emptyState('Nothing has flowed yet', 'Tap refresh to check your sources.');
  wrap.appendChild(plainButton('Refresh', () => actions.refreshAll()));
  return wrap;
}

function buildNoResultsEmptyState(state) {
  return emptyState('No results', `Nothing on your stand matches "${state.searchQuery}".`);
}

function emptyState(titleText, bodyText) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-empty-state';
  const title = document.createElement('p');
  title.className = 'nooz-empty-state-title';
  title.textContent = titleText;
  wrap.appendChild(title);
  const text = document.createElement('p');
  text.className = 'nooz-empty-state-text';
  text.textContent = bodyText;
  wrap.appendChild(text);
  return wrap;
}

function primaryButton(label, onClick) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'nooz-button nooz-button--primary';
  btn.textContent = label;
  btn.addEventListener('click', onClick);
  return btn;
}

function plainButton(label, onClick) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'nooz-button';
  btn.textContent = label;
  btn.addEventListener('click', onClick);
  return btn;
}

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
