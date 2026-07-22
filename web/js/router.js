// router.js -- tiny hash-based router. The app has exactly four views, and
// the URL only needs to carry which one is showing (plus, for the reader,
// which item) -- so location.hash is the single source of navigational
// truth, and bookmarking/reload/back-forward all fall out of that for free.

const VALID_VIEWS = new Set(['stand', 'sources', 'clippings', 'reader']);
const DEFAULT_VIEW = 'stand';

/** Parse the current location.hash into {view, itemId}. */
export function parseRoute() {
  const raw = (window.location.hash || '').replace(/^#\/?/, '');
  const [view, itemId] = raw.split('/');
  if (!VALID_VIEWS.has(view)) return { view: DEFAULT_VIEW, itemId: null };
  if (view === 'reader' && !itemId) return { view: DEFAULT_VIEW, itemId: null };
  return { view, itemId: itemId ? decodeURIComponent(itemId) : null };
}

/** Navigate to a view (and, for 'reader', the item it shows). */
export function navigate(view, itemId) {
  const hash = view === 'reader' && itemId ? `#/reader/${encodeURIComponent(itemId)}` : `#/${view}`;
  if (window.location.hash === hash) {
    notify(); // same route requested again (e.g. re-opening the item already showing) -- still re-render
  } else {
    window.location.hash = hash;
  }
}

let listener = null;

/** Subscribe to route changes. Fires once immediately with the current route. */
export function onRoute(callback) {
  listener = callback;
  window.addEventListener('hashchange', notify);
  notify();
}

function notify() {
  if (listener) listener(parseRoute());
}
