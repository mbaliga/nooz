// focus.js -- where the keyboard cursor goes when the view is rebuilt.
//
// The app re-renders by tearing the stage (and the drawer) down and building
// it again. Whatever the reader was focused on -- a headline link, a nav
// button -- stops existing, and focus falls to <body>. For anyone navigating
// by keyboard or screen reader that *is* the navigation: they activate a story
// and land silently at the top of the document, with no indication that
// anything happened, and have to walk the whole page again to get back.
//
// This module owns the decision. It is deliberately separate from app.js so it
// can be tested: app.js opens IndexedDB and starts fetching on import, and a
// rule this consequential should not be verified only by reading it.

/**
 * @param {{stage: Element, drawer: Element}} regions the two rebuilt regions
 */
export function createFocusManager({ stage, drawer }) {
  let lastRouteKey = null;
  // The control that opened the current drawer, so closing it gives focus back
  // rather than dropping the reader at the top of the Paper.
  let focusBeforeDrawer = null;

  return {
    /**
     * Called after every render.
     *
     * @param {{view: string, itemId: ?string}} route      the route just rendered
     * @param {?string} drawerView                          the open drawer, if any
     * @param {?Element} activeBeforeRender                 focus as it was before the rebuild
     * @param {boolean} restoredAnInput                     a text field already took focus back
     * @returns {?Element} the element focused, or null if focus was left alone
     */
    settle(route, drawerView, activeBeforeRender, restoredAnInput) {
      const routeKey = `${route.view}|${route.itemId || ''}`;
      const changed = routeKey !== lastRouteKey;
      const isFirstRender = lastRouteKey === null;
      const wasInDrawer = !!activeBeforeRender && drawer.contains(activeBeforeRender);
      // Recorded even when we then decline to move focus, so a route change
      // that coincided with a live text field cannot fire on some later,
      // unrelated render and pull the cursor out of whatever came next.
      lastRouteKey = routeKey;

      // Only on a genuine route change: render also runs when a fetch lands, a
      // story is marked read, or a setting toggles, and moving focus on those
      // would yank the cursor out from under someone mid-page.
      if (!changed || restoredAnInput) return null;
      // The first paint is not a navigation. The reader has not asked to go
      // anywhere, and taking focus off the top of the document would be rude.
      if (isFirstRender) return null;

      if (drawerView) {
        // Remember the door in -- unless we came from another drawer, in which
        // case the door already remembered is still the right one.
        if (!wasInDrawer && activeBeforeRender && activeBeforeRender !== activeBeforeRender.ownerDocument.body) {
          focusBeforeDrawer = activeBeforeRender;
        }
        return focusHeadingIn(drawer);
      }

      if (wasInDrawer && focusBeforeDrawer && focusBeforeDrawer.ownerDocument.contains(focusBeforeDrawer)) {
        const back = focusBeforeDrawer;
        focusBeforeDrawer = null;
        back.focus();
        return back;
      }
      focusBeforeDrawer = null;
      return focusHeadingIn(stage);
    },
  };
}

/**
 * Focus a region's own heading, or the region itself if it has none.
 *
 * Focusing the heading is what makes a route change *audible*: screen readers
 * announce the newly focused element, so the reader hears "The Loom, heading
 * level 1" rather than nothing at all.
 */
function focusHeadingIn(region) {
  const target = region.querySelector('h1') || region;
  // tabindex="-1" makes it programmatically focusable without adding it to the
  // tab order; .nooz-focus-target suppresses the ring, since nobody arrived
  // here by pressing Tab.
  target.setAttribute('tabindex', '-1');
  target.classList.add('nooz-focus-target');
  target.focus({ preventScroll: true });
  return target;
}
