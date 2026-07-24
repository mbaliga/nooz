// db.js -- IndexedDB wrapper for nooz's local data store.
//
// This app has no account and no server. Sources, items, read state, and
// clippings all live in this browser's IndexedDB, in a database called
// "nooz-web", and nowhere else. There is no sync: a different browser or a
// different device starts empty, on purpose -- that's a deliberate choice by
// the app's owner, not a missing feature.

const DB_NAME = 'nooz-web';
const DB_VERSION = 2;

const STORE_SOURCES = 'sources';
const STORE_ITEMS = 'items';
const STORE_READS = 'reads';
const STORE_CLIPS = 'clips';
// v2: a cache of server-extracted full-article bodies, keyed by item id, so a
// re-open reads from disk instead of re-hitting /api/article every time.
const STORE_ARTICLES = 'articles';

let dbPromise = null;

/** Open (or reuse) the single shared IndexedDB connection. */
function openDB() {
  if (dbPromise) return dbPromise;

  dbPromise = new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB is not available in this browser'));
      return;
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = () => {
      const db = request.result;

      if (!db.objectStoreNames.contains(STORE_SOURCES)) {
        db.createObjectStore(STORE_SOURCES, { keyPath: 'id' });
      }

      if (!db.objectStoreNames.contains(STORE_ITEMS)) {
        const items = db.createObjectStore(STORE_ITEMS, { keyPath: 'id' });
        items.createIndex('sourceId', 'sourceId');
        items.createIndex('publishedAt', 'publishedAt');
      }

      if (!db.objectStoreNames.contains(STORE_READS)) {
        db.createObjectStore(STORE_READS, { keyPath: 'itemId' });
      }

      if (!db.objectStoreNames.contains(STORE_CLIPS)) {
        db.createObjectStore(STORE_CLIPS, { keyPath: 'id' });
      }

      if (!db.objectStoreNames.contains(STORE_ARTICLES)) {
        db.createObjectStore(STORE_ARTICLES, { keyPath: 'id' });
      }
    };

    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error || new Error('Failed to open IndexedDB'));
  });

  return dbPromise;
}

/** Wrap a single IDBRequest as a Promise resolving to its .result. */
function requestToPromise(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

/**
 * Run `fn` against a transaction over `storeNames`, resolving once the
 * transaction has fully committed. `fn` receives the live IDBTransaction and
 * may issue any number of requests on it (optionally awaiting them one at a
 * time); whatever it returns/resolves becomes the outer promise's value.
 */
async function runTransaction(storeNames, mode, fn) {
  const db = await openDB();

  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeNames, mode);
    let result;
    let settled = false;

    const fail = (err) => {
      if (settled) return;
      settled = true;
      reject(err);
    };

    tx.onerror = () => fail(tx.error);
    tx.onabort = () => fail(tx.error || new DOMException('Transaction aborted', 'AbortError'));
    tx.oncomplete = () => {
      if (settled) return;
      settled = true;
      resolve(result);
    };

    Promise.resolve(fn(tx))
      .then((value) => {
        result = value;
      })
      .catch((err) => {
        fail(err);
        try {
          tx.abort();
        } catch (_err) {
          // transaction already finished -- nothing to abort
        }
      });
  });
}

/** Ensure the database exists and is ready. Safe to call more than once. */
export async function dbInit() {
  await openDB();
}

// ---------------------------------------------------------------------------
// Sources
// ---------------------------------------------------------------------------

export async function dbGetSources() {
  try {
    const sources = await runTransaction(STORE_SOURCES, 'readonly', (tx) =>
      requestToPromise(tx.objectStore(STORE_SOURCES).getAll())
    );
    return sources || [];
  } catch (err) {
    console.error('dbGetSources failed:', err);
    return [];
  }
}

export async function dbAddSource(source) {
  await runTransaction(STORE_SOURCES, 'readwrite', (tx) =>
    requestToPromise(tx.objectStore(STORE_SOURCES).put(source))
  );
}

export async function dbUpdateSource(id, patch) {
  await runTransaction(STORE_SOURCES, 'readwrite', async (tx) => {
    const store = tx.objectStore(STORE_SOURCES);
    const existing = await requestToPromise(store.get(id));
    if (!existing) return; // nothing to patch -- not an error
    await requestToPromise(store.put({ ...existing, ...patch, id: existing.id }));
  });
}

export async function dbRemoveSource(id) {
  await runTransaction(STORE_SOURCES, 'readwrite', (tx) =>
    requestToPromise(tx.objectStore(STORE_SOURCES).delete(id))
  );
}

// ---------------------------------------------------------------------------
// Items
// ---------------------------------------------------------------------------

/** All items across all sources, newest publishedAt first. */
export async function dbGetItems() {
  try {
    const items = await runTransaction(STORE_ITEMS, 'readonly', (tx) => {
      return new Promise((resolve, reject) => {
        const index = tx.objectStore(STORE_ITEMS).index('publishedAt');
        const results = [];
        const cursorRequest = index.openCursor(null, 'prev'); // newest first
        cursorRequest.onsuccess = () => {
          const cursor = cursorRequest.result;
          if (cursor) {
            results.push(cursor.value);
            cursor.continue();
          } else {
            resolve(results);
          }
        };
        cursorRequest.onerror = () => reject(cursorRequest.error);
      });
    });
    return items || [];
  } catch (err) {
    console.error('dbGetItems failed:', err);
    return [];
  }
}

/** Upsert items by id. */
export async function dbPutItems(items) {
  if (!items || items.length === 0) return;
  await runTransaction(STORE_ITEMS, 'readwrite', (tx) => {
    const store = tx.objectStore(STORE_ITEMS);
    for (const item of items) {
      store.put(item);
    }
  });
}

// ---------------------------------------------------------------------------
// Read state
// ---------------------------------------------------------------------------

export async function dbGetReadIds() {
  try {
    const keys = await runTransaction(STORE_READS, 'readonly', (tx) =>
      requestToPromise(tx.objectStore(STORE_READS).getAllKeys())
    );
    return new Set(keys || []);
  } catch (err) {
    console.error('dbGetReadIds failed:', err);
    return new Set();
  }
}

export async function dbMarkRead(itemId) {
  await runTransaction(STORE_READS, 'readwrite', (tx) =>
    requestToPromise(tx.objectStore(STORE_READS).put({ itemId }))
  );
}

// ---------------------------------------------------------------------------
// Clippings (store the full Item so a clip survives its source being removed)
// ---------------------------------------------------------------------------

export async function dbGetClippedIds() {
  try {
    const keys = await runTransaction(STORE_CLIPS, 'readonly', (tx) =>
      requestToPromise(tx.objectStore(STORE_CLIPS).getAllKeys())
    );
    return new Set(keys || []);
  } catch (err) {
    console.error('dbGetClippedIds failed:', err);
    return new Set();
  }
}

export async function dbToggleClip(item) {
  await runTransaction(STORE_CLIPS, 'readwrite', async (tx) => {
    const store = tx.objectStore(STORE_CLIPS);
    const existing = await requestToPromise(store.get(item.id));
    if (existing) {
      await requestToPromise(store.delete(item.id));
    } else {
      await requestToPromise(store.put(item));
    }
  });
}

export async function dbGetClippings() {
  try {
    const clips = await runTransaction(STORE_CLIPS, 'readonly', (tx) =>
      requestToPromise(tx.objectStore(STORE_CLIPS).getAll())
    );
    return (clips || []).slice().sort((a, b) => b.publishedAt - a.publishedAt);
  } catch (err) {
    console.error('dbGetClippings failed:', err);
    return [];
  }
}

// ---------------------------------------------------------------------------
// Extracted-article cache ({ id, html, textLen, byline, leadImage, fetchedAt })
// ---------------------------------------------------------------------------

export async function dbGetArticle(id) {
  try {
    return await runTransaction(STORE_ARTICLES, 'readonly', (tx) =>
      requestToPromise(tx.objectStore(STORE_ARTICLES).get(id))
    );
  } catch (err) {
    console.error('dbGetArticle failed:', err);
    return null;
  }
}

export async function dbPutArticle(record) {
  if (!record || !record.id) return;
  try {
    await runTransaction(STORE_ARTICLES, 'readwrite', (tx) =>
      requestToPromise(tx.objectStore(STORE_ARTICLES).put(record))
    );
  } catch (err) {
    console.error('dbPutArticle failed:', err);
  }
}
