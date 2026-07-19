// app.js -- integration glue. Owns the one persistent app chrome (wordmark +
// Stand/Sources/Clippings nav), the single in-memory state object, and every
// action the view modules call. Each view module only renders; app.js is the
// only place that touches IndexedDB, fetches feeds, or decides what's
// currently "visible" (enabled sources, region filter, search).

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
} from './db.js';
import { fetchFeed } from './feeds.js';
import { STARTERS } from './starters.js';
import { render as renderStand } from './views/stand.js';
import { render as renderReader } from './views/reader.js';
import { render as renderSources } from './views/sources.js';
import { render as renderClippings } from './views/clippings.js';
import { parseRoute, navigate, onRoute } from './router.js';

const VIEW_RENDERERS = {
  stand: renderStand,
  reader: renderReader,
  sources: renderSources,
  clippings: renderClippings,
};
const NAV_VIEWS = ['stand', 'sources', 'clippings'];

// Full, unfiltered item history (every item ever fetched, across all
// sources -- never pruned in this version). state.items handed to views is
// always a filtered projection of this, computed fresh on every render.
let allItems = [];
// Every item this app has ever seen, by id -- including clippings, so a
// clip stays shareable/openable even if its source is later removed and
// stops appearing in allItems' enabled-source projection.
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
};

let viewEl = null;
let navLinksEl = {};
let toastEl = null;
let toastTimer = null;

async function boot() {
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
  onRoute(handleRoute);
  refreshAll(); // background fill-in; first paint already happened from cached IndexedDB state
}

function rebuildItemsById() {
  itemsById.clear();
  for (const item of allItems) itemsById.set(item.id, item);
  for (const item of currentState.clippings) {
    if (!itemsById.has(item.id)) itemsById.set(item.id, item);
  }
}

// ---------------------------------------------------------------------------
// Persistent chrome
// ---------------------------------------------------------------------------

function buildShell() {
  const app = document.getElementById('app');
  app.innerHTML = '';

  const shell = document.createElement('div');
  shell.className = 'nooz-app-shell';

  const topbar = document.createElement('header');
  topbar.className = 'nooz-topbar';

  const wordmark = document.createElement('span');
  wordmark.className = 'nooz-wordmark';
  wordmark.textContent = 'Nooz';
  topbar.appendChild(wordmark);

  const nav = document.createElement('nav');
  nav.className = 'nooz-nav';
  nav.setAttribute('aria-label', 'Primary');

  navLinksEl = {};
  for (const view of NAV_VIEWS) {
    const link = document.createElement('button');
    link.type = 'button';
    link.className = 'nooz-nav-link';
    link.textContent = capitalize(view);
    link.addEventListener('click', () => navigate(view));
    nav.appendChild(link);
    navLinksEl[view] = link;
  }
  topbar.appendChild(nav);

  shell.appendChild(topbar);

  viewEl = document.createElement('main');
  viewEl.id = 'view';
  shell.appendChild(viewEl);

  toastEl = document.createElement('div');
  toastEl.className = 'nooz-toast';
  toastEl.setAttribute('role', 'status');
  shell.appendChild(toastEl);

  app.appendChild(shell);
}

function capitalize(s) {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

// ---------------------------------------------------------------------------
// Routing + render
// ---------------------------------------------------------------------------

function handleRoute(route) {
  for (const view of NAV_VIEWS) {
    if (view === route.view) navLinksEl[view].setAttribute('aria-current', 'page');
    else navLinksEl[view].removeAttribute('aria-current');
  }
  rerender();
}

/**
 * Re-render the current view. Preserves focus + cursor position on a
 * focused <input>/<textarea> across the rebuild -- container.innerHTML = ''
 * inside a view's render() would otherwise drop focus on every keystroke in
 * e.g. the Stand's search box, since a brand-new input element replaces the
 * old one each time.
 */
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
    clippings: currentState.clippings,
    readIds: currentState.readIds,
    clippedIds: currentState.clippedIds,
    currentItemId: route.itemId,
    searchQuery: currentState.searchQuery,
    regionFilter: currentState.regionFilter,
    starters: currentState.starters,
    fetchStatus: currentState.fetchStatus,
    fetchErrors: currentState.fetchErrors,
  };

  const renderer = VIEW_RENDERERS[route.view] || VIEW_RENDERERS.stand;
  renderer(viewEl, stateForView, actions);

  if (restore && restore.index >= 0) {
    const candidates = Array.prototype.filter.call(viewEl.querySelectorAll(restore.tag), (el) => el.type === restore.type);
    const el = candidates[restore.index];
    if (el) {
      el.focus();
      if (typeof el.setSelectionRange === 'function' && restore.selectionStart != null) {
        try {
          el.setSelectionRange(restore.selectionStart, restore.selectionEnd);
        } catch (_err) {
          // some input types (e.g. "search" in older engines) can reject this -- fine to skip
        }
      }
    }
  }
}

/** Stand's items: enabled sources only (the "honest denominator"), then region, then search. */
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
// Fetching
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
      // user cancelled, or unsupported for this data -- not an error worth surfacing
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
};

boot();
