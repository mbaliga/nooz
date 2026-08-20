// app.js -- integration glue. Owns the persistent app chrome (now a FOOTER,
// not a header -- the wordmark and Stand/Loom/Sources/Clippings/Settings nav
// sit at the bottom of the frame, matching asystemofcells), the single
// in-memory state object, and every action the view modules call. Each view
// module only renders; app.js is the only place that touches IndexedDB,
// fetches feeds, extracts articles, or decides what's currently "visible".

import {
  dbInit,
  dbGetSources,
  dbAddSource,
  dbUpdateSource,
  dbRemoveSource,
  dbGetItems,
  dbPutItems,
  dbGetReadIds,
  dbMarkRead,
  dbGetClippedIds,
  dbToggleClip,
  dbGetClippings,
  dbGetArticle,
  dbPutArticle,
} from './db.js';
import { fetchFeed } from './feeds.js';
import { STARTERS } from './starters.js';
import { renderNewspaperClipping } from './newspaperShare.js';
import { shouldShowOnboarding, showOnboarding } from './onboarding.js';
import { pickFoundQuote } from './foundQuote.js';
import { loadSettings, getSettings, setSetting } from './settings.js';
import { installSelectionSearch } from './selection.js';
import { render as renderStand } from './views/stand.js';
import { render as renderReader } from './views/reader.js';
import { render as renderSources } from './views/sources.js';
import { render as renderClippings } from './views/clippings.js';
import { render as renderLoom } from './views/loom.js';
import { render as renderSettings } from './views/settings.js';
import { render as renderNewsstand } from './views/newsstand.js';
import { classifyItem } from './topics.js';
import { parseRoute, navigate, onRoute } from './router.js';

const VIEW_RENDERERS = {
  stand: renderStand,
  newsstand: renderNewsstand,
  loom: renderLoom,
  reader: renderReader,
  sources: renderSources,
  clippings: renderClippings,
  settings: renderSettings,
};
// Footer nav order. Reader has no nav entry (it's reached by opening an item).
// The newsstand ("Stand") sits first (left-most) -- it's where you pick a paper;
// "Paper" is the front page you read. Settings sits at the end, slightly apart.
const NAV_VIEWS = ['newsstand', 'stand', 'loom', 'sources', 'clippings', 'settings'];
const NAV_LABELS = { stand: 'Paper', loom: 'Loom', sources: 'Sources', clippings: 'Clippings', settings: 'Settings', newsstand: 'Stand' };
// Full-stage views replace the Paper; everything else opens as a right drawer.
const STAGE_VIEWS = new Set(['stand', 'reader', 'newsstand']);
// The option views open as a right-hand drawer over the Paper (which slides
// aside), rather than replacing it. Reader and Paper are full-stage.
const DRAWER_VIEWS = new Set(['loom', 'sources', 'clippings', 'settings']);


// A feed whose own body already runs to at least this many characters of plain
// text is treated as "full enough" -- we don't bother the extraction endpoint
// for it. Below it (BBC-style one-liners), we try to extract the real article.
const RICH_ENOUGH_CHARS = 900;

// Settings > Articles > "Full articles": how many of the currently-visible
// items get proactively extracted per render, top of the list first. Bounded
// so a very large feed list can't fire hundreds of extraction requests at
// once -- the browser's own per-origin connection limit throttles what does
// run, but there's no reason to even queue more than a page's worth; items
// past the cap still extract normally the moment they're opened.
const FULL_ARTICLE_PREFETCH_CAP = 60;

// The prefetch loop above queues up to a page's worth of extractions, but only
// this many run at once -- otherwise a large feed list fires dozens of
// concurrent /api/article requests and dozens of near-simultaneous 'loading'
// state transitions (each calling rerender()) the instant the page loads,
// which reads as the whole paper flickering and makes the first load feel
// slow. Queued items still extract, just a few at a time.
const ARTICLE_PREFETCH_CONCURRENCY = 3;
const articlePrefetchQueue = [];
const queuedArticleIds = new Set();
let articlePrefetchActive = 0;

function queueArticlePrefetch(item) {
  if (!item || !item.link) return;
  const id = item.id;
  if (currentState.articles[id] || currentState.articleStatus[id] || queuedArticleIds.has(id)) return;
  queuedArticleIds.add(id);
  articlePrefetchQueue.push(item);
  pumpArticlePrefetch();
}

function pumpArticlePrefetch() {
  while (articlePrefetchActive < ARTICLE_PREFETCH_CONCURRENCY && articlePrefetchQueue.length) {
    const item = articlePrefetchQueue.shift();
    queuedArticleIds.delete(item.id);
    articlePrefetchActive += 1;
    ensureArticle(item).finally(() => {
      articlePrefetchActive -= 1;
      pumpArticlePrefetch();
      // The whole background queue just drained -- flush any rerender a
      // burst of completions was coalescing rather than leave the last
      // batch sitting out the rest of its timer for nothing.
      if (articlePrefetchQueue.length === 0 && articlePrefetchActive === 0) flushArticleCoalesce();
    });
  }
}

let allItems = [];
const itemsById = new Map();

const currentState = {
  sources: [],
  clippings: [],
  readIds: new Set(),
  clippedIds: new Set(),
  searchQuery: '',
  regionFilter: null,
  topicFilter: null, // set from the newsstand (Category)
  sourceFilter: null, // set from the newsstand (Publisher)
  starters: STARTERS,
  fetchStatus: {},
  fetchErrors: {},
  articles: {}, // itemId -> { id, html, byline, leadImage, textLen, fetchedAt }
  articleStatus: {}, // itemId -> 'loading' | 'ready' | 'error' | 'skip'
  settings: null,
  readingAside: null, // the current found-quote/dateline pick (see foundQuote.js), or null
};

let viewEl = null;
let stageEl = null;
let drawerEl = null;
let shellEl = null;
let navLinksEl = {};
let sourcesBadgeEl = null;
let toastEl = null;
let toastTimer = null;
let searchToggleEl = null;
let searchBarEl = null;
let searchInputEl = null;

async function boot() {
  currentState.settings = loadSettings();
  await dbInit();
  const [sources, items, readIds, clippedIds, clippings] = await Promise.all([
    dbGetSources(),
    dbGetItems(),
    dbGetReadIds(),
    dbGetClippedIds(),
    dbGetClippings(),
  ]);
  currentState.sources = sources;
  currentState.readIds = readIds;
  currentState.clippedIds = clippedIds;
  currentState.clippings = clippings;
  allItems = items;
  rebuildItemsById();

  buildShell();
  installSelectionSearch();
  onRoute(handleRoute);
  installResizeReflow();
  installReadingClock();
  refreshAll();
  if (shouldShowOnboarding()) showOnboarding();
}

// Every so often while actually reading, a quiet aside: a real line pulled
// from something already read (see foundQuote.js) -- not a reward, no
// counter kept anywhere, just an editorial break the way a long newspaper
// column gets a pull-quote. The clock only runs while a reading view is on
// screen and the tab is actually visible, so switching away or sitting in
// Sources/Settings/the Loom doesn't quietly rack up "reading" time.
const READING_ASIDE_INTERVAL_MS = 6 * 60 * 1000;
const READING_CLOCK_TICK_MS = 5000;
let readingClockMs = 0;

function installReadingClock() {
  setInterval(() => {
    if (document.visibilityState !== 'visible') return;
    if (!STAGE_VIEWS.has(parseRoute().view)) return;
    readingClockMs += READING_CLOCK_TICK_MS;
    if (readingClockMs < READING_ASIDE_INTERVAL_MS) return;
    readingClockMs = 0;
    const quote = pickFoundQuote({
      items: computeVisibleItems(),
      readIds: currentState.readIds,
      articles: currentState.articles,
      sources: currentState.sources,
      currentItemId: parseRoute().itemId,
    });
    if (!quote) return; // nothing read yet with real text to pull from -- try again next interval
    currentState.readingAside = quote;
    rerender();
  }, READING_CLOCK_TICK_MS);
}

// The Newspaper layout measures the masthead and fits a spread to the frame, so
// it has to re-lay-out when the window changes size (and when the wide/narrow
// two-up threshold is crossed). Debounced so a drag-resize doesn't thrash.
//
// iPadOS (and other mobile WebKit) browsers collapse and expand their own
// toolbar chrome as the page scrolls, which fires plain 'resize' events with
// only the viewport HEIGHT changing -- nothing in this app's layout depends
// on that (the Newspaper's own page height already accounts for it via vh
// units at layout time), so reacting to it just means rebuilding the whole
// stage, including recreating the visible article's image, on every toolbar
// twitch. Only a WIDTH change can move the wide/narrow column threshold, so
// that's the only change worth a rerender.
function installResizeReflow() {
  let timer = null;
  let lastWidth = window.innerWidth;
  window.addEventListener('resize', () => {
    const width = window.innerWidth;
    if (width === lastWidth) return;
    lastWidth = width;
    clearTimeout(timer);
    timer = setTimeout(() => rerender(), 150);
  });
}

function rebuildItemsById() {
  itemsById.clear();
  for (const item of allItems) itemsById.set(item.id, item);
  for (const item of currentState.clippings) {
    if (!itemsById.has(item.id)) itemsById.set(item.id, item);
  }
}

// ---------------------------------------------------------------------------
// Persistent chrome -- content region on top, footer nav pinned at the bottom
// ---------------------------------------------------------------------------

function buildShell() {
  const app = document.getElementById('app');
  app.innerHTML = '';

  const shell = document.createElement('div');
  shell.className = 'nooz-app-shell';
  shellEl = shell;

  viewEl = document.createElement('main');
  viewEl.id = 'view';
  viewEl.className = 'nooz-view';

  // The stage holds the Paper (or the reader); the drawer slides in from the
  // right for option views, pushing the stage aside.
  stageEl = document.createElement('div');
  stageEl.className = 'nooz-stage';
  viewEl.appendChild(stageEl);

  drawerEl = document.createElement('aside');
  drawerEl.className = 'nooz-drawer';
  drawerEl.setAttribute('aria-label', 'Options');
  viewEl.appendChild(drawerEl);

  shell.appendChild(viewEl);

  const footer = document.createElement('footer');
  footer.className = 'nooz-footerbar';

  const wordmark = document.createElement('button');
  wordmark.type = 'button';
  wordmark.className = 'nooz-footer-wordmark';
  wordmark.textContent = 'Nooz';
  wordmark.setAttribute('aria-label', 'Nooz home');
  wordmark.addEventListener('click', () => navigate('stand'));
  footer.appendChild(wordmark);

  const nav = document.createElement('nav');
  nav.className = 'nooz-footer-nav';
  nav.setAttribute('aria-label', 'Primary');

  // Search is just an icon, to the left of the Stand. Tapping it opens a plain
  // search bar centred at the bottom -- nothing between you and the paper.
  searchToggleEl = document.createElement('button');
  searchToggleEl.type = 'button';
  searchToggleEl.className = 'nooz-footer-search';
  searchToggleEl.setAttribute('aria-label', 'Search the paper');
  searchToggleEl.appendChild(makeSearchIcon());
  searchToggleEl.addEventListener('click', toggleSearch);
  nav.appendChild(searchToggleEl);

  navLinksEl = {};
  for (const view of NAV_VIEWS) {
    const link = document.createElement('button');
    link.type = 'button';
    link.className = 'nooz-footer-link';
    if (view === 'settings') link.classList.add('nooz-footer-link--end');
    link.textContent = NAV_LABELS[view];
    // Clicking the already-open drawer tab closes it (back to the bare Paper).
    link.addEventListener('click', () => {
      const route = parseRoute();
      if (DRAWER_VIEWS.has(view) && route.view === view) navigate('stand');
      else navigate(view);
    });
    // The Sources tab carries an alert dot when a source is failing (that's
    // where you fix it) -- see updateSourceHealth. No banner on the paper.
    if (view === 'sources') {
      sourcesBadgeEl = document.createElement('span');
      sourcesBadgeEl.className = 'nooz-footer-badge';
      sourcesBadgeEl.hidden = true;
      sourcesBadgeEl.setAttribute('aria-hidden', 'true');
      link.appendChild(sourcesBadgeEl);
    }
    nav.appendChild(link);
    navLinksEl[view] = link;
  }
  footer.appendChild(nav);
  shell.appendChild(footer);

  // The search bar: hidden until the footer icon is tapped, then it opens
  // centred just above the footer.
  searchBarEl = document.createElement('div');
  searchBarEl.className = 'nooz-searchbar';
  const searchForm = document.createElement('form');
  searchForm.className = 'nooz-searchbar-form';
  searchForm.addEventListener('submit', (e) => e.preventDefault());
  searchInputEl = document.createElement('input');
  searchInputEl.type = 'search';
  searchInputEl.className = 'nooz-searchbar-input';
  searchInputEl.placeholder = 'Search the paper';
  searchInputEl.setAttribute('aria-label', 'Search the paper');
  searchInputEl.addEventListener('input', (e) => setSearchQuery(e.target.value));
  searchInputEl.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeSearch(); });
  searchForm.appendChild(searchInputEl);
  const searchClear = document.createElement('button');
  searchClear.type = 'button';
  searchClear.className = 'nooz-searchbar-clear';
  searchClear.setAttribute('aria-label', 'Close search');
  searchClear.textContent = '×';
  searchClear.addEventListener('click', () => { setSearchQuery(''); closeSearch(); });
  searchForm.appendChild(searchClear);
  searchBarEl.appendChild(searchForm);
  shell.appendChild(searchBarEl);

  toastEl = document.createElement('div');
  toastEl.className = 'nooz-toast';
  toastEl.setAttribute('role', 'status');
  shell.appendChild(toastEl);

  app.appendChild(shell);

  // Escape closes an open drawer or the search bar.
  document.addEventListener('keydown', (e) => {
    if (e.key !== 'Escape') return;
    if (shellEl.classList.contains('has-search')) closeSearch();
    else if (DRAWER_VIEWS.has(parseRoute().view)) navigate('stand');
  });
}

function makeSearchIcon() {
  const NS = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('viewBox', '0 0 16 16');
  svg.setAttribute('class', 'nooz-icon');
  svg.setAttribute('aria-hidden', 'true');
  const c = document.createElementNS(NS, 'circle');
  c.setAttribute('cx', '7'); c.setAttribute('cy', '7'); c.setAttribute('r', '4.4');
  c.setAttribute('fill', 'none'); c.setAttribute('stroke', 'currentColor'); c.setAttribute('stroke-width', '1.4');
  svg.appendChild(c);
  const l = document.createElementNS(NS, 'line');
  l.setAttribute('x1', '10.4'); l.setAttribute('y1', '10.4'); l.setAttribute('x2', '14'); l.setAttribute('y2', '14');
  l.setAttribute('stroke', 'currentColor'); l.setAttribute('stroke-width', '1.4'); l.setAttribute('stroke-linecap', 'round');
  svg.appendChild(l);
  return svg;
}

function toggleSearch() {
  if (shellEl.classList.contains('has-search')) closeSearch();
  else openSearch();
}

function openSearch() {
  shellEl.classList.add('has-search');
  searchToggleEl.classList.add('is-active');
  searchToggleEl.setAttribute('aria-expanded', 'true');
  searchInputEl.value = currentState.searchQuery || '';
  searchInputEl.focus();
}

function closeSearch() {
  shellEl.classList.remove('has-search');
  searchToggleEl.classList.toggle('is-active', !!currentState.searchQuery);
  searchToggleEl.setAttribute('aria-expanded', 'false');
}

function buildDrawerClose() {
  const bar = document.createElement('div');
  bar.className = 'nooz-drawer-close';
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'nooz-button-icon';
  btn.setAttribute('aria-label', 'Close');
  btn.textContent = '×'; // ×
  btn.addEventListener('click', () => navigate('stand'));
  bar.appendChild(btn);
  return bar;
}

// ---------------------------------------------------------------------------
// Routing + render
// ---------------------------------------------------------------------------

function handleRoute(route) {
  for (const view of NAV_VIEWS) {
    // Paper tab stays lit for the Paper and the reader; a drawer tab lights
    // only while its own drawer is open.
    const active = DRAWER_VIEWS.has(view)
      ? view === route.view
      : view === route.view || (view === 'stand' && route.view === 'reader');
    if (active) navLinksEl[view].setAttribute('aria-current', 'page');
    else navLinksEl[view].removeAttribute('aria-current');
  }
  // Opening an article starts at the top of the page.
  if (route.view === 'reader') window.scrollTo({ top: 0 });
  rerender();
}

// A failing enabled source raises a small alert dot on the Sources tab -- the
// place it gets resolved -- with the "Couldn't reach ..." detail on hover,
// rather than a notice banner across the top of the paper.
function updateSourceHealth() {
  if (!sourcesBadgeEl || !navLinksEl.sources) return;
  const fetchStatus = currentState.fetchStatus || {};
  const failed = (currentState.sources || []).filter(
    (s) => s.enabled && fetchStatus[s.id] === 'error'
  );
  const link = navLinksEl.sources;
  if (failed.length === 0) {
    sourcesBadgeEl.hidden = true;
    link.removeAttribute('title');
    link.removeAttribute('aria-label');
    return;
  }
  sourcesBadgeEl.hidden = false;
  const summary = failed.length === 1
    ? "Couldn't reach 1 source"
    : `Couldn't reach ${failed.length} sources`;
  link.title = `${summary}: ${failed.map((s) => s.title || s.url).join(', ')}`;
  link.setAttribute('aria-label', `${NAV_LABELS.sources} — ${summary}`);
}

// Many state changes (a burst of article extractions finishing, a fetch tick)
// can each call rerender() within the same instant. Coalescing them into one
// rAF-scheduled pass means one DOM rebuild for the whole burst instead of one
// per change -- the difference between a single repaint and the UI visibly
// flickering as it rebuilds itself over and over.
let renderScheduled = false;

function rerender() {
  if (renderScheduled) return;
  renderScheduled = true;
  requestAnimationFrame(() => {
    renderScheduled = false;
    renderNow();
  });
}

function renderNow() {
  updateSourceHealth();
  const active = document.activeElement;
  let restore = null;
  if (active && viewEl.contains(active) && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA')) {
    const tag = active.tagName.toLowerCase();
    const siblings = Array.prototype.filter.call(viewEl.querySelectorAll(tag), (el) => el.type === active.type);
    restore = {
      tag,
      type: active.type,
      index: siblings.indexOf(active),
      selectionStart: active.selectionStart,
      selectionEnd: active.selectionEnd,
    };
  }

  const route = parseRoute();
  const stateForView = {
    sources: currentState.sources,
    items: computeVisibleItems(),
    allItems,
    clippings: currentState.clippings,
    readIds: currentState.readIds,
    clippedIds: currentState.clippedIds,
    currentItemId: route.itemId,
    searchQuery: currentState.searchQuery,
    regionFilter: currentState.regionFilter,
    topicFilter: currentState.topicFilter,
    sourceFilter: currentState.sourceFilter,
    starters: currentState.starters,
    fetchStatus: currentState.fetchStatus,
    fetchErrors: currentState.fetchErrors,
    articles: currentState.articles,
    articleStatus: currentState.articleStatus,
    settings: getSettings(),
    readingAside: currentState.readingAside,
    // Which drawer (if any) is open -- the Paper stays mounted behind it, so it
    // needs this to know when to quiet its own on-page echo of a drawer's
    // content (e.g. the loom strip, once the Loom drawer has its own bar open).
    activeDrawer: DRAWER_VIEWS.has(route.view) ? route.view : null,
  };

  if (stateForView.settings.articleDisplay !== 'excerpt') {
    for (const item of stateForView.items.slice(0, FULL_ARTICLE_PREFETCH_CAP)) queueArticlePrefetch(item);
  }

  // Stage: reader / newsstand / Paper (which stays mounted behind an open drawer).
  const stageView = STAGE_VIEWS.has(route.view) ? route.view : 'stand';
  VIEW_RENDERERS[stageView](stageEl, stateForView, actions);

  // Drawer: an option view slides in from the right; the Paper shifts aside.
  const drawerView = stateForView.activeDrawer;
  if (drawerView) {
    drawerEl.replaceChildren();
    drawerEl.appendChild(buildDrawerClose());
    const content = document.createElement('div');
    content.className = 'nooz-drawer-content';
    VIEW_RENDERERS[drawerView](content, stateForView, actions);
    drawerEl.appendChild(content);
    shellEl.classList.add('has-drawer');
    drawerEl.dataset.view = drawerView;
  } else {
    shellEl.classList.remove('has-drawer');
    drawerEl.replaceChildren();
    delete drawerEl.dataset.view;
  }

  if (restore && restore.index >= 0) {
    const candidates = Array.prototype.filter.call(viewEl.querySelectorAll(restore.tag), (el) => el.type === restore.type);
    const el = candidates[restore.index];
    if (el) {
      el.focus();
      if (typeof el.setSelectionRange === 'function' && restore.selectionStart != null) {
        try {
          el.setSelectionRange(restore.selectionStart, restore.selectionEnd);
        } catch (_err) {
          /* some input types reject this -- fine to skip */
        }
      }
    }
  }
}

/** Stand's items: enabled sources only (the honest denominator), then region, then search. */
function computeVisibleItems() {
  const enabledIds = new Set(currentState.sources.filter((s) => s.enabled).map((s) => s.id));
  let list = allItems.filter((item) => enabledIds.has(item.sourceId));

  if (currentState.regionFilter) {
    const idsInRegion = new Set(
      currentState.sources.filter((s) => s.region === currentState.regionFilter).map((s) => s.id)
    );
    list = list.filter((item) => idsInRegion.has(item.sourceId));
  }

  if (currentState.sourceFilter) {
    list = list.filter((item) => item.sourceId === currentState.sourceFilter);
  }

  if (currentState.topicFilter) {
    list = list.filter((item) => classifyItem(item) === currentState.topicFilter);
  }

  const q = (currentState.searchQuery || '').trim().toLowerCase();
  if (q) {
    list = list.filter(
      (item) =>
        (item.title || '').toLowerCase().includes(q) ||
        (item.summary || '').toLowerCase().includes(q) ||
        (item.author || '').toLowerCase().includes(q)
    );
  }

  return list;
}

// ---------------------------------------------------------------------------
// Fetching feeds
// ---------------------------------------------------------------------------

async function refreshAll() {
  const enabled = currentState.sources.filter((s) => s.enabled);
  if (enabled.length === 0) return;
  for (const source of enabled) currentState.fetchStatus[source.id] = 'loading';
  rerender();
  await Promise.all(enabled.map((source) => refreshOne(source)));
}

async function refreshOne(source) {
  const result = await fetchFeed(source);
  if (result.ok) {
    if (result.items.length > 0) {
      await dbPutItems(result.items);
      allItems = await dbGetItems();
      rebuildItemsById();
    }
    currentState.fetchStatus[source.id] = 'ok';
    delete currentState.fetchErrors[source.id];
  } else {
    currentState.fetchStatus[source.id] = 'error';
    currentState.fetchErrors[source.id] = result.error;
  }
  rerender();
}

// ---------------------------------------------------------------------------
// Full-article extraction (server-side /api/article, cached in IndexedDB)
//
// Only reached for items whose own feed body is too thin to read (BBC-style
// one-liners). Feeds that already ship a full body skip this entirely -- we
// never re-fetch what we were already given.
// ---------------------------------------------------------------------------

function plainLength(html) {
  if (!html) return 0;
  return html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim().length;
}

// ---------------------------------------------------------------------------
// Rerender policy for article completions
//
// The background prefetch pump (pumpArticlePrefetch, up to 3 at a time, a
// queue up to FULL_ARTICLE_PREFETCH_CAP deep) runs ensureArticle() for every
// visible item so "Full articles" can upgrade in place -- but a bare
// rerender() on every single one of those completions means a full stage
// rebuild once per item, spread over however long the whole queue takes to
// drain. Each rebuild recreates the on-screen article's <img> from scratch;
// Chrome keeps the already-decoded bitmap hot across that, so it's invisible
// there, but WebKit re-decodes a freshly-created <img> node even when the
// bytes are already cached, which reads as the lead photo popping out and
// back in, roughly once per completion, for as long as the queue is running.
//
// The fix is to only rerender when a result can actually change what's on
// screen right now, and to coalesce the rest into one rebuild instead of one
// per item.
const ARTICLE_COALESCE_MS = 1500;
let articleCoalesceTimer = null;

// Whether the view currently on screen lays out extracted article bodies at
// all -- if it doesn't, a background item resolving has nothing to redraw.
// 'reader' shows exactly one article (route.itemId); a different item
// finishing has nothing to add there, and the itemId-matches case is handled
// separately, before this is ever consulted. 'stand' inlines every visible
// item's full extracted text when Settings > Articles > "Full articles" is
// on (the default), so any item resolving there can extend what's already
// showing; in excerpt mode the Paper only ever reads the feed's own summary,
// never state.articles, so it has nothing to gain from a rerender either.
// 'newsstand' is a browse surface (titles and counts only) and never reads
// state.articles at all.
function viewRendersArticleBodies(route) {
  return route.view === 'stand' && getSettings().articleDisplay !== 'excerpt';
}

function flushArticleCoalesce() {
  if (articleCoalesceTimer === null) return;
  clearTimeout(articleCoalesceTimer);
  articleCoalesceTimer = null;
  rerender();
}

function rerenderForArticleResult(item) {
  const route = parseRoute();
  if (route.itemId === item.id) {
    // The article on screen just resolved (or changed loading state) --
    // nothing coalesces this, it should show up right away.
    rerender();
    return;
  }
  if (!viewRendersArticleBodies(route)) {
    // Nothing on screen reads state.articles/articleStatus for this item
    // right now. The result still lands in currentState, so the next real
    // render (opening the item, a nav, a setting change) picks it up for
    // free -- no rebuild needed to make that true.
    return;
  }
  // One trailing rerender for however many of these land in the same burst,
  // reset on every new arrival; pumpArticlePrefetch's finally callback
  // flushes it immediately once the queue is fully drained so the last
  // batch in a run isn't left waiting out a timer nobody will reset again.
  clearTimeout(articleCoalesceTimer);
  articleCoalesceTimer = setTimeout(() => {
    articleCoalesceTimer = null;
    rerender();
  }, ARTICLE_COALESCE_MS);
}

async function ensureArticle(item) {
  if (!item || !item.link) return;
  const id = item.id;
  if (currentState.articles[id] || currentState.articleStatus[id]) return;

  if (plainLength(item.contentHtml) >= RICH_ENOUGH_CHARS) {
    currentState.articleStatus[id] = 'skip'; // the feed body is already enough
    return;
  }

  const cached = await dbGetArticle(id);
  if (cached && cached.html) {
    currentState.articles[id] = cached;
    currentState.articleStatus[id] = 'ready';
    rerenderForArticleResult(item);
    return;
  }

  currentState.articleStatus[id] = 'loading';
  rerenderForArticleResult(item);

  try {
    const res = await fetch(`/api/article?url=${encodeURIComponent(item.link)}`, {
      headers: { Accept: 'application/json' },
      cache: 'no-store',
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    if (!data || !data.html || plainLength(data.html) < 200) {
      currentState.articleStatus[id] = 'error';
    } else {
      const record = {
        id,
        html: data.html,
        byline: data.byline || null,
        leadImage: data.leadImage || null,
        textLen: plainLength(data.html),
        fetchedAt: Date.now(),
      };
      currentState.articles[id] = record;
      currentState.articleStatus[id] = 'ready';
      dbPutArticle(record);
    }
  } catch (_err) {
    currentState.articleStatus[id] = 'error';
  }
  rerenderForArticleResult(item);
}

// ---------------------------------------------------------------------------
// Actions
// ---------------------------------------------------------------------------

function makeSourceId() {
  return `src_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

function hostnameOf(url) {
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch (_err) {
    return null;
  }
}

async function addSourceByUrl(url) {
  const trimmed = (url || '').trim();
  if (!trimmed || currentState.sources.some((s) => s.url === trimmed)) return;
  const source = {
    id: makeSourceId(),
    title: hostnameOf(trimmed) || trimmed,
    url: trimmed,
    kind: 'rss',
    region: null,
    enabled: true,
    addedAt: Date.now(),
  };
  await dbAddSource(source);
  currentState.sources.push(source);
  rerender();
  await refreshOne(source);
}

async function addStarter(starter) {
  if (currentState.sources.some((s) => s.url === starter.url)) return;
  const source = {
    id: makeSourceId(),
    title: starter.title,
    url: starter.url,
    kind: 'rss',
    region: starter.region || null,
    enabled: true,
    addedAt: Date.now(),
  };
  await dbAddSource(source);
  currentState.sources.push(source);
  rerender();
  await refreshOne(source);
}

async function removeSource(id) {
  await dbRemoveSource(id);
  currentState.sources = currentState.sources.filter((s) => s.id !== id);
  delete currentState.fetchStatus[id];
  delete currentState.fetchErrors[id];
  rerender();
}

async function toggleSourceEnabled(id) {
  const source = currentState.sources.find((s) => s.id === id);
  if (!source) return;
  source.enabled = !source.enabled;
  await dbUpdateSource(id, { enabled: source.enabled });
  rerender();
}

async function openItem(itemId) {
  currentState.readIds.add(itemId);
  await dbMarkRead(itemId);
  navigate('reader', itemId);
  const item = itemsById.get(itemId);
  if (item) ensureArticle(item);
}

async function toggleClip(itemId) {
  const item = itemsById.get(itemId);
  if (!item) return;
  await dbToggleClip(item);
  if (currentState.clippedIds.has(itemId)) currentState.clippedIds.delete(itemId);
  else currentState.clippedIds.add(itemId);
  currentState.clippings = await dbGetClippings();
  rerender();
}

function goTo(view) {
  navigate(view);
}

async function shareItem(itemId) {
  const item = itemsById.get(itemId);
  if (!item) return;
  const source = (currentState.sources || []).find((s) => s.id === item.sourceId);
  const shareText = item.link ? `${item.title || 'Nooz'} — ${item.link}` : (item.title || 'Nooz');

  // A "newspaper clipping" mockup image, matching the Android app's own
  // share -- rendered client-side. Never dead-ends: if rendering fails, or
  // the platform can't share files, this falls through to the plain
  // link/text share (and, with no Web Share API at all, a direct download
  // plus a copied link) exactly as share worked before this existed.
  let blob = null;
  try {
    blob = await renderNewspaperClipping({
      title: item.title,
      sourceTitle: source ? source.title : null,
      author: item.author,
    });
  } catch (_err) {
    blob = null;
  }

  if (blob && navigator.canShare) {
    const file = new File([blob], 'nooz-clipping.png', { type: 'image/png' });
    if (navigator.canShare({ files: [file] })) {
      try {
        await navigator.share({ files: [file], title: item.title || 'Nooz', text: shareText });
        return;
      } catch (_err) {
        /* cancelled -- fall through to the plain share below */
      }
    }
  }

  if (navigator.share) {
    try {
      await navigator.share({ title: item.title || 'Nooz', text: item.summary || '', url: item.link || undefined });
    } catch (_err) {
      /* cancelled/unsupported -- not worth surfacing */
    }
    return;
  }

  // No Web Share API at all (desktop Safari/Firefox): download the clipping
  // if one rendered, and copy the link either way.
  if (blob) {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'nooz-clipping.png';
    a.click();
    URL.revokeObjectURL(a.href);
  }
  const text = item.link || item.title || '';
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    showToast(blob ? 'Clipping downloaded, link copied' : 'Link copied');
  } catch (_err) {
    showToast('Could not copy link');
  }
}

function setSearchQuery(query) {
  currentState.searchQuery = query;
  if (searchToggleEl) {
    const active = !!query || shellEl.classList.contains('has-search');
    searchToggleEl.classList.toggle('is-active', active);
  }
  rerender();
}

function setRegionFilter(region) {
  currentState.regionFilter = region;
  rerender();
}

// The newsstand focuses the Paper on one category / publisher / region, then
// hands back to the Paper.
function focusTopic(topic) {
  currentState.topicFilter = topic;
  currentState.sourceFilter = null;
  navigate('stand');
}
function focusSource(sourceId) {
  currentState.sourceFilter = sourceId;
  currentState.topicFilter = null;
  navigate('stand');
}
function focusRegion(region) {
  currentState.regionFilter = region;
  currentState.topicFilter = null;
  currentState.sourceFilter = null;
  navigate('stand');
}
function clearFocus() {
  currentState.topicFilter = null;
  currentState.sourceFilter = null;
  rerender();
}

function updateSetting(key, value) {
  setSetting(key, value);
  currentState.settings = getSettings();
  rerender();
}

// Views that keep their own local paging state (the Newspaper mode) trigger a
// re-render through this rather than reaching into app internals.
function refreshView() {
  rerender();
}

function showToast(message) {
  if (!toastEl) return;
  toastEl.textContent = message;
  toastEl.classList.add('is-visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.remove('is-visible'), 2400);
}

const actions = {
  addSourceByUrl,
  addStarter,
  removeSource,
  toggleSourceEnabled,
  refreshAll,
  openItem,
  toggleClip,
  goTo,
  shareItem,
  setSearchQuery,
  setRegionFilter,
  focusTopic,
  focusSource,
  focusRegion,
  clearFocus,
  updateSetting,
  ensureArticle,
  refreshView,
};

boot();
