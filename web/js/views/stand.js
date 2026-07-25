// stand.js -- the "Paper": Nooz's front page. A masthead across the top, a slim
// toolbar (search / region / refresh), and the day's stories. Two reading
// modes (Settings): Continuous sets everything in scrolling newspaper columns;
// Newspaper pages it like a real paper you turn -- a lead page, then pages you
// flip through, two-up as a spread when the screen is wide enough.
//
// Source management no longer lives here: it's the right-hand Sources drawer
// now (opened from the footer), so the Paper gets the full width for columns.
//
// All feed text is set via .textContent; images go through images.js (halftone
// by default, switchable from the chip on the lead photo); the extracted/feed
// image URLs were already restricted to https by feeds.js.

import { classifyItem, TOPIC_LABEL } from '../topics.js';
import { frameImage } from '../images.js';

// Which page of the Newspaper mode we're on; kept across background re-renders.
let newspaperPage = 0;
let lastTurnDir = 0; // -1 back, +1 forward, 0 none -- drives the turn-in animation

export function render(container, state, actions) {
  container.replaceChildren();

  const paper = document.createElement('div');
  paper.className = 'nooz-paper';

  paper.appendChild(buildMasthead(state));
  paper.appendChild(buildToolbar(state, actions));

  if (state.sources.length === 0) {
    paper.appendChild(buildNoSourcesEmptyState(actions));
    container.appendChild(paper);
    return;
  }

  const notice = buildFetchErrorNotice(state);
  if (notice) paper.appendChild(notice);

  if (state.items.length === 0) {
    paper.appendChild(
      state.searchQuery ? buildNoResultsEmptyState(state) : buildNothingFlowedEmptyState(actions)
    );
    container.appendChild(paper);
    return;
  }

  const mode = state.settings && state.settings.readingMode === 'newspaper' ? 'newspaper' : 'continuous';
  if (mode === 'newspaper') paper.appendChild(buildNewspaper(state, actions));
  else paper.appendChild(buildFrontPage(state, actions));

  container.appendChild(paper);
}

// ---------------------------------------------------------------------------
// Masthead + toolbar
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
  edition.textContent = [fullToday(), `${enabled} ${enabled === 1 ? 'source' : 'sources'}`, state.regionFilter || 'All regions'].join('  ·  ');
  masthead.appendChild(edition);

  const botRule = document.createElement('div');
  botRule.className = 'nooz-masthead-rule nooz-masthead-rule--double';
  masthead.appendChild(botRule);

  return masthead;
}

function buildToolbar(state, actions) {
  const bar = document.createElement('div');
  bar.className = 'nooz-toolbar';

  const search = document.createElement('input');
  search.type = 'search';
  search.className = 'nooz-input nooz-toolbar-search';
  search.placeholder = 'Search the paper';
  search.value = state.searchQuery || '';
  search.setAttribute('aria-label', 'Search the paper');
  search.addEventListener('input', (e) => actions.setSearchQuery(e.target.value));
  bar.appendChild(search);

  const regions = Array.from(new Set(state.sources.filter((s) => s.region).map((s) => s.region))).sort((a, b) => a.localeCompare(b));
  if (regions.length) {
    const chips = document.createElement('div');
    chips.className = 'nooz-toolbar-regions';
    chips.appendChild(regionChip('All', state.regionFilter === null, () => actions.setRegionFilter(null)));
    for (const region of regions) chips.appendChild(regionChip(region, state.regionFilter === region, () => actions.setRegionFilter(region)));
    bar.appendChild(chips);
  }

  const refresh = document.createElement('button');
  refresh.type = 'button';
  refresh.className = 'nooz-button nooz-toolbar-refresh';
  refresh.textContent = 'Refresh';
  refresh.addEventListener('click', () => actions.refreshAll());
  bar.appendChild(refresh);

  return bar;
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

// ---------------------------------------------------------------------------
// Continuous front page (scrolling columns)
// ---------------------------------------------------------------------------

function buildFrontPage(state, actions) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-frontpage';

  const items = state.items;
  const showImages = !state.settings || state.settings.showImages;
  const imageStyle = (state.settings && state.settings.imageStyle) || 'halftone';

  let leadIndex = 0;
  if (showImages) {
    const withImg = items.findIndex((it) => it.image);
    if (withImg >= 0 && withImg < 6) leadIndex = withImg;
  }
  wrap.appendChild(buildLeadStory(items[leadIndex], state, actions, showImages, imageStyle));

  const rest = items.filter((_, i) => i !== leadIndex);
  if (rest.length) {
    const columns = document.createElement('div');
    columns.className = 'nooz-columns';
    for (const item of rest) columns.appendChild(buildColumnStory(item, state, actions, showImages));
    wrap.appendChild(columns);
  }
  return wrap;
}

// ---------------------------------------------------------------------------
// Newspaper mode (paged, page-turn)
// ---------------------------------------------------------------------------

function buildNewspaper(state, actions) {
  const items = state.items;
  const showImages = !state.settings || state.settings.showImages;
  const imageStyle = (state.settings && state.settings.imageStyle) || 'halftone';

  // Page 0 leads with the lead + a few stories; later pages carry 6 each.
  const pages = [];
  let leadIndex = 0;
  if (showImages) {
    const withImg = items.findIndex((it) => it.image);
    if (withImg >= 0 && withImg < 6) leadIndex = withImg;
  }
  const lead = items[leadIndex];
  const rest = items.filter((_, i) => i !== leadIndex);
  pages.push({ lead, stories: rest.slice(0, 4) });
  for (let i = 4; i < rest.length; i += 6) pages.push({ lead: null, stories: rest.slice(i, i + 6) });

  const spread = window.innerWidth >= 1024;
  const step = spread ? 2 : 1;
  const maxPage = Math.max(0, pages.length - 1);
  if (newspaperPage > maxPage) newspaperPage = maxPage;

  const book = document.createElement('div');
  book.className = 'nooz-book' + (spread ? ' is-spread' : '');

  const visible = spread ? [newspaperPage, newspaperPage + 1] : [newspaperPage];
  visible.forEach((idx, i) => {
    if (idx > maxPage) return;
    const sheet = buildSheet(pages[idx], idx, pages.length, state, actions, showImages, imageStyle);
    // Turn-in animation on the leading sheet only, in the last turn's direction.
    if (i === 0 && lastTurnDir !== 0) sheet.classList.add(lastTurnDir > 0 ? 'turn-in-fwd' : 'turn-in-back');
    book.appendChild(sheet);
  });
  lastTurnDir = 0;

  const wrap = document.createElement('div');
  wrap.className = 'nooz-newspaper';
  wrap.appendChild(book);
  wrap.appendChild(buildPager(newspaperPage, pages.length, step, maxPage, actions));

  return wrap;
}

function buildPager(page, count, step, maxPage, actions) {
  const pager = document.createElement('div');
  pager.className = 'nooz-pager';

  const prev = document.createElement('button');
  prev.type = 'button';
  prev.className = 'nooz-button nooz-pager-btn';
  prev.textContent = '‹ Previous';
  prev.disabled = page <= 0;
  prev.addEventListener('click', () => {
    newspaperPage = Math.max(0, page - step);
    lastTurnDir = -1;
    actions.refreshView();
  });
  pager.appendChild(prev);

  const label = document.createElement('span');
  label.className = 'nooz-pager-label';
  const shownEnd = Math.min(maxPage + 1, page + step);
  label.textContent = step === 2 && shownEnd > page + 1
    ? `Pages ${page + 1}–${shownEnd} of ${count}`
    : `Page ${page + 1} of ${count}`;
  pager.appendChild(label);

  const next = document.createElement('button');
  next.type = 'button';
  next.className = 'nooz-button nooz-pager-btn';
  next.textContent = 'Next ›';
  next.disabled = page + step > maxPage;
  next.addEventListener('click', () => {
    newspaperPage = Math.min(maxPage, page + step);
    lastTurnDir = 1;
    actions.refreshView();
  });
  pager.appendChild(next);

  return pager;
}

function buildSheet(pageData, index, total, state, actions, showImages, imageStyle) {
  const sheet = document.createElement('section');
  sheet.className = 'nooz-sheet';

  const folio = document.createElement('div');
  folio.className = 'nooz-folio';
  folio.textContent = index === 0 ? 'Front Page' : `Page ${index + 1}`;
  sheet.appendChild(folio);

  if (pageData.lead) {
    sheet.appendChild(buildLeadStory(pageData.lead, state, actions, showImages, imageStyle, true));
  }
  if (pageData.stories.length) {
    const cols = document.createElement('div');
    cols.className = 'nooz-columns';
    for (const item of pageData.stories) cols.appendChild(buildColumnStory(item, state, actions, showImages));
    sheet.appendChild(cols);
  }
  return sheet;
}

// ---------------------------------------------------------------------------
// Stories
// ---------------------------------------------------------------------------

function buildLeadStory(item, state, actions, showImages, imageStyle, continued) {
  const story = document.createElement('article');
  story.className = 'nooz-lead';
  story.setAttribute('role', 'button');
  story.setAttribute('tabindex', '0');
  if (state.readIds.has(item.id)) story.classList.add('is-read');

  if (showImages && item.image) {
    const figure = frameImage(item.image, {
      prominent: true,
      className: 'nooz-lead-figure',
      currentStyle: imageStyle,
      onStyle: (s) => actions.updateSetting('imageStyle', s),
    });
    if (figure) story.appendChild(figure);
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
    dek.textContent = clampText(item.summary, continued ? 220 : 320);
    body.appendChild(dek);
  }

  body.appendChild(byline(item, state));

  const cont = document.createElement('span');
  cont.className = 'nooz-continue';
  cont.textContent = 'Continue reading →';
  body.appendChild(cont);

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
    const figure = frameImage(item.image, { className: 'nooz-col-photo' });
    if (figure) story.appendChild(figure);
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
// Notices + empty states
// ---------------------------------------------------------------------------

function buildFetchErrorNotice(state) {
  const fetchStatus = state.fetchStatus || {};
  const failed = state.sources.filter((s) => s.enabled && fetchStatus[s.id] === 'error');
  if (failed.length === 0) return null;
  const notice = document.createElement('div');
  notice.className = 'nooz-notice';
  notice.setAttribute('role', 'status');
  const label = document.createElement('p');
  label.className = 'nooz-notice-title';
  label.textContent = failed.length === 1 ? "Couldn't reach 1 source" : `Couldn't reach ${failed.length} sources`;
  notice.appendChild(label);
  const names = document.createElement('p');
  names.className = 'nooz-notice-detail';
  names.textContent = failed.map((s) => s.title).join(', ');
  notice.appendChild(names);
  return notice;
}

function buildNoSourcesEmptyState(actions) {
  const wrap = emptyState('Nothing on your paper yet', 'Add a feed, or pick from a short list of pre-checked starter sources -- open Sources from the footer.');
  wrap.appendChild(primaryButton('Open sources', () => actions.goTo('sources')));
  return wrap;
}

function buildNothingFlowedEmptyState(actions) {
  const wrap = emptyState('Nothing has flowed yet', 'Tap refresh to check your sources.');
  wrap.appendChild(plainButton('Refresh', () => actions.refreshAll()));
  return wrap;
}

function buildNoResultsEmptyState(state) {
  return emptyState('No results', `Nothing on your paper matches "${state.searchQuery}".`);
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

function fullToday() {
  return new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });
}

function formatShortDate(publishedAt) {
  const diffMin = Math.floor((Date.now() - publishedAt) / 60000);
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
