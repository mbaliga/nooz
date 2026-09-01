// A DOM for the view modules to build into.
//
// The views are plain DOM builders — they take a container, a state and an
// actions object and append elements — so linkedom (already a dependency, for
// the article extractor) is enough to run them for real and inspect what they
// produce. That matters for accessibility assertions in particular: the claim
// "the headline is a heading and a link" is only worth anything if it is made
// against the markup the browser would actually get.
import { parseHTML } from 'linkedom';

export function installDom() {
  const { window } = parseHTML('<!doctype html><html><body><div id="app"></div></body></html>');
  globalThis.window = window;
  globalThis.document = window.document;
  globalThis.Node = window.Node;
  globalThis.Element = window.Element;
  globalThis.HTMLElement = window.HTMLElement;
  globalThis.DocumentFragment = window.DocumentFragment;
  globalThis.getComputedStyle = () => ({ getPropertyValue: () => '' });
  globalThis.requestAnimationFrame = (fn) => { fn(0); return 0; };
  globalThis.cancelAnimationFrame = () => {};
  globalThis.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {} });
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} };
  globalThis.IntersectionObserver = class { observe() {} unobserve() {} disconnect() {} };
  return window.document;
}

/** A minimal `actions` object: every call is recorded, nothing is required. */
export function recordingActions() {
  const calls = [];
  return new Proxy({ calls }, {
    get(target, prop) {
      if (prop in target) return target[prop];
      return (...args) => { calls.push([String(prop), ...args]); };
    },
  });
}

export function item(over = {}) {
  return {
    id: 'i1',
    sourceId: 's1',
    title: 'Council votes to open the reservoir',
    summary: 'A short dek about the vote and what happens next.',
    publishedAt: Date.parse('2026-08-31T09:00:00Z'),
    ...over,
  };
}

export function baseState(over = {}) {
  return {
    sources: [{ id: 's1', title: 'The Chronicle', enabled: true }],
    items: [],
    readIds: new Set(),
    clippedIds: new Set(),
    clippings: [],
    articles: {},
    settings: { readingMode: 'continuous', articleDisplay: 'excerpt', imageStyle: 'halftone', showImages: false },
    ...over,
  };
}

/** Every element that a screen reader would treat as a control. */
export function controls(root) {
  return [...root.querySelectorAll('[role="button"], button, a[href], [tabindex]')];
}
