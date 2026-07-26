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
import { buildLoomStrip } from './loom.js';

// Which spread of the Newspaper mode is open; kept across background re-renders.
// Spread 0 is the front page (shown on its own); spread 1 is pages 2-3, etc.
let newspaperSpread = 0;
// Direction of the last turn (-1/0/+1) so the freshly-rendered spread can play
// its turn-in animation, then it's reset.
let lastTurnDir = 0;

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

  // The loom, always in reach: a coloured strip of the day's mix that expands
  // into the full Loom when tapped.
  paper.appendChild(buildLoomStrip(state, actions));

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

  const focus = buildFocusChip(state, actions);
  if (focus) bar.appendChild(focus);

  const refresh = document.createElement('button');
  refresh.type = 'button';
  refresh.className = 'nooz-button nooz-toolbar-refresh';
  refresh.textContent = 'Refresh';
  refresh.addEventListener('click', () => actions.refreshAll());
  bar.appendChild(refresh);

  return bar;
}

// When the newsstand focuses the Paper on a category or publisher, show a
// removable chip so it's obvious what's being filtered and easy to clear.
function buildFocusChip(state, actions) {
  let label = null;
  if (state.topicFilter) label = `Category: ${TOPIC_LABEL[state.topicFilter] || state.topicFilter}`;
  else if (state.sourceFilter) {
    const src = (state.sources || []).find((s) => s.id === state.sourceFilter);
    label = `Publisher: ${src ? src.title : 'source'}`;
  }
  if (!label) return null;

  const chip = document.createElement('button');
  chip.type = 'button';
  chip.className = 'nooz-focus-chip';
  chip.setAttribute('aria-label', `Clear ${label}`);
  const text = document.createElement('span');
  text.textContent = label;
  chip.appendChild(text);
  const x = document.createElement('span');
  x.className = 'nooz-focus-x';
  x.textContent = '×';
  chip.appendChild(x);
  chip.addEventListener('click', () => actions.clearFocus());
  return chip;
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
// Newspaper mode -- a real paper you turn. Every page is exactly as wide as the
// Nooz masthead above it (measured at layout time). The front page is shown on
// its own at that full width; turning opens the 2-3 spread, then 4-5, and so on.
// An open spread is two full pages side by side, so it's wider than the frame --
// it's zoomed to fit so you see both, the way an open broadsheet reads. On a
// narrow screen a spread wouldn't be legible, so it stays a single page you turn
// one at a time.
// ---------------------------------------------------------------------------

function paginate(state) {
  const items = state.items;
  const showImages = !state.settings || state.settings.showImages;
  const imageStyle = (state.settings && state.settings.imageStyle) || 'halftone';
  let leadIndex = 0;
  if (showImages) {
    const withImg = items.findIndex((it) => it.image);
    if (withImg >= 0 && withImg < 6) leadIndex = withImg;
  }
  const lead = items[leadIndex];
  const rest = items.filter((_, i) => i !== leadIndex);
  const pages = [{ lead, stories: rest.slice(0, 5) }];
  for (let i = 5; i < rest.length; i += 6) pages.push({ lead: null, stories: rest.slice(i, i + 6) });
  return { pages, showImages, imageStyle };
}

// Room to open a two-page spread and still read it? Below this we turn single
// full-width pages instead.
function isWide() { return window.innerWidth >= 900; }

// Front page is its own spread; after that, two pages per spread when wide.
function spreadCount(pageCount, wide) {
  if (pageCount <= 0) return 1;
  return wide ? 1 + Math.ceil((pageCount - 1) / 2) : pageCount;
}

// The page indices a spread shows. Front (spread 0) is a single page shown on
// its own; each later spread is the natural pair (2-3, 4-5, ...). Narrow always
// shows one page.
function pagesForSpread(spread, wide) {
  if (!wide) return [spread];
  if (spread === 0) return [0];
  return [2 * spread - 1, 2 * spread];
}

function buildNewspaper(state, actions) {
  const { pages, showImages, imageStyle } = paginate(state);
  const ctx = { pages, state, actions, showImages, imageStyle };
  const wide = isWide();
  const maxSpread = spreadCount(pages.length, wide) - 1;
  if (newspaperSpread > maxSpread) newspaperSpread = maxSpread;
  if (newspaperSpread < 0) newspaperSpread = 0;

  const idxs = pagesForSpread(newspaperSpread, wide).filter((i) => i >= 0 && i < pages.length);
  const isSpread = idxs.length > 1;

  // frame (clips + centres) > scaler (fit-to-width) > book (the turning pages)
  const frame = document.createElement('div');
  frame.className = 'nooz-np-frame';

  const scaler = document.createElement('div');
  scaler.className = 'nooz-np-scaler';

  const book = document.createElement('div');
  book.className = 'nooz-book' + (isSpread ? ' is-spread' : '');
  for (const idx of idxs) {
    const page = document.createElement('div');
    page.className = 'nooz-page';
    page.appendChild(buildSheet(ctx, idx));
    book.appendChild(page);
  }
  // The freshly-rendered spread plays its turn-in on the side it came from.
  if (lastTurnDir > 0) book.classList.add('is-turn-fwd');
  else if (lastTurnDir < 0) book.classList.add('is-turn-back');
  lastTurnDir = 0;

  scaler.appendChild(book);
  frame.appendChild(scaler);

  const doTurn = (dir) => {
    const target = Math.min(maxSpread, Math.max(0, newspaperSpread + dir));
    if (target === newspaperSpread) return;
    lastTurnDir = dir;
    newspaperSpread = target;
    actions.refreshView();
  };

  const wrap = document.createElement('div');
  wrap.className = 'nooz-newspaper';
  wrap.appendChild(frame);
  wrap.appendChild(buildPager(newspaperSpread, maxSpread, pages.length, wide, doTurn));

  fitNewspaper(wrap, frame, scaler, book);
  return wrap;
}

// Measure the masthead so each page is exactly its width, then scale the book so
// the whole (possibly two-page) spread fits the frame. Runs after layout, and
// once more shortly after in case a lead image changes the height.
function fitNewspaper(wrap, frame, scaler, book) {
  const apply = () => {
    if (!wrap.isConnected) return;
    const paper = wrap.closest('.nooz-paper');
    const mast = paper && paper.querySelector('.nooz-masthead');
    const pageW = Math.round((mast ? mast.getBoundingClientRect().width : frame.clientWidth) || frame.clientWidth);
    if (pageW > 0) frame.style.setProperty('--page-w', pageW + 'px');
    const natural = book.scrollWidth || pageW;
    const avail = frame.clientWidth || pageW;
    const scale = Math.min(1, avail / natural);
    scaler.style.transform = scale < 0.999 ? `scale(${scale})` : 'none';
    frame.style.height = Math.ceil(scaler.getBoundingClientRect().height) + 'px';
  };
  requestAnimationFrame(apply);
  setTimeout(apply, 260);
}

function buildPager(spread, maxSpread, pageCount, wide, doTurn) {
  const pager = document.createElement('div');
  pager.className = 'nooz-pager';

  const prev = document.createElement('button');
  prev.type = 'button';
  prev.className = 'nooz-button nooz-pager-btn';
  prev.textContent = '‹ Turn back';
  prev.disabled = spread <= 0;
  prev.addEventListener('click', () => doTurn(-1));
  pager.appendChild(prev);

  const label = document.createElement('span');
  label.className = 'nooz-pager-label';
  label.textContent = pagerLabel(spread, pageCount, wide);
  pager.appendChild(label);

  const next = document.createElement('button');
  next.type = 'button';
  next.className = 'nooz-button nooz-pager-btn';
  next.textContent = 'Turn page ›';
  next.disabled = spread >= maxSpread;
  next.addEventListener('click', () => doTurn(1));
  pager.appendChild(next);

  return pager;
}

function pagerLabel(spread, pageCount, wide) {
  if (spread === 0) return `Front page  ·  ${pageCount} ${pageCount === 1 ? 'page' : 'pages'}`;
  if (!wide) return `Page ${spread + 1} of ${pageCount}`;
  const left = 2 * spread; // human page number of the left page
  const right = Math.min(pageCount, 2 * spread + 1);
  return right > left ? `Pages ${left}–${right} of ${pageCount}` : `Page ${left} of ${pageCount}`;
}

function buildSheet(ctx, index) {
  const pageData = ctx.pages[index];
  const sheet = document.createElement('section');
  sheet.className = 'nooz-sheet';

  const folio = document.createElement('div');
  folio.className = 'nooz-folio';
  folio.textContent = index === 0 ? 'Front Page' : `Page ${index + 1}`;
  sheet.appendChild(folio);

  if (pageData.lead) {
    sheet.appendChild(buildLeadStory(pageData.lead, ctx.state, ctx.actions, ctx.showImages, ctx.imageStyle, true));
  }
  if (pageData.stories.length) {
    const cols = document.createElement('div');
    cols.className = 'nooz-columns';
    for (const item of pageData.stories) cols.appendChild(buildColumnStory(item, ctx.state, ctx.actions, ctx.showImages));
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
