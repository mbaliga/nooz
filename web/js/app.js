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
import { loadSettings, getSettings, setSetting } from './settings.js';
import { installSelectionSearch } from './selection.js';
import { render as renderStand } from './views/stand.js';
import { render as renderReader } from './views/reader.js';
import { render as renderSources } from './views/sources.js';
import { render as renderClippings } from './views/clippings.js';
import { render as renderLoom } from './views/loom.js';
import { render as renderSettings } from './views/settings.js';
import { parseRoute, navigate, onRoute } from './router.js';

const VIEW_RENDERERS = {
  stand: renderStand,
  loom: renderLoom,
  reader: renderReader,
  sources: renderSources,
  clippings: renderClippings,
  settings: renderSettings,
};
// Footer nav order. Reader has no nav entry (it's reached by opening an item);
// Settings sits at the end, slightly apart. "Stand" is called "Paper" on the
// web -- the front page IS the paper.
const NAV_VIEWS = ['stand', 'loom', 'sources', 'clippings', 'settings'];
const NAV_LABELS = { stand: 'Paper', loom: 'Loom', sources: 'Sources', clippings: 'Clippings', settings: 'Settings' };
// The option views open as a right-hand drawer over the Paper (which slides
// aside), rather than replacing it. Reader and Paper are full-stage.
const DRAWER_VIEWS = new Set(['loom', 'sources', 'clippings', 'settings']);

// A feed whose own body already runs to at least this many characters of plain
// text is treated as "full enough" -- we don't bother the extraction endpoint
// for it. Below it (BBC-style one-liners), we try to extract the real article.
const RICH_ENOUGH_CHARS = 900;

let allItems = [];
const itemsById = new Map();

const currentState = {
  sources: [],
  clippings: [],
  readIds: new Set(),
  clippedIds: new Set(),
  searchQuery: '',
  regionFilter: null,
  starters: STARTERS,
  fetchStatus: {},
  fetchErrors: {},
  articles: {}, // itemId -> { id, html, byline, leadImage, textLen, fetchedAt }
  articleStatus: {}, // itemId -> 'loading' | 'ready' | 'error' | 'skip'
  settings: null,
};

let viewEl = null;
let stageEl = null;
let drawerEl = null;
let shellEl = null;
let navLinksEl = {};
let toastEl = null;
let toastTimer = null;

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
  refreshAll();
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
    nav.appendChild(link);
    navLinksEl[view] = link;
  }
  footer.appendChild(nav);
  shell.appendChild(footer);

  toastEl = document.createElement('div');
  toastEl.className = 'nooz-toast';
  toastEl.setAttribute('role', 'status');
  shell.appendChild(toastEl);

  app.appendChild(shell);

  // Escape closes an open drawer.
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && DRAWER_VIEWS.has(parseRoute().view)) navigate('stand');
  });
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
      : view === 'stand' && (route.view === 'stand' || route.view === 'reader');
    if (active) navLinksEl[view].setAttribute('aria-current', 'page');
    else navLinksEl[view].removeAttribute('aria-current');
  }
  // Opening an article starts at the top of the page.
  if (route.view === 'reader') window.scrollTo({ top: 0 });
  rerender();
}

function rerender() {
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
    starters: currentState.starters,
    fetchStatus: currentState.fetchStatus,
    fetchErrors: currentState.fetchErrors,
    articles: currentState.articles,
    articleStatus: currentState.articleStatus,
    settings: getSettings(),
  };

  // Stage: the reader when reading an item, otherwise the Paper (which stays
  // mounted behind an open drawer).
  const stageView = route.view === 'reader' ? 'reader' : 'stand';
  VIEW_RENDERERS[stageView](stageEl, stateForView, actions);

  // Drawer: an option view slides in from the right; the Paper shifts aside.
  const drawerView = DRAWER_VIEWS.has(route.view) ? route.view : null;
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
    rerender();
    return;
  }

  currentState.articleStatus[id] = 'loading';
  rerender();

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
  rerender();
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
  const shareData = { title: item.title || 'Nooz', text: item.summary || '', url: item.link || undefined };
  if (navigator.share) {
    try {
      await navigator.share(shareData);
    } catch (_err) {
      /* cancelled/unsupported -- not worth surfacing */
    }
    return;
  }
  const text = item.link || item.title || '';
  if (!text) return;
  try {
    await navigator.clipboard.writeText(text);
    showToast('Link copied');
  } catch (_err) {
    showToast('Could not copy link');
  }
}

function setSearchQuery(query) {
  currentState.searchQuery = query;
  rerender();
}

function setRegionFilter(region) {
  currentState.regionFilter = region;
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
  updateSetting,
  ensureArticle,
  refreshView,
};

boot();
