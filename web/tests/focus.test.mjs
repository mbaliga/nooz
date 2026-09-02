import assert from 'node:assert/strict';
import test from 'node:test';

import { installDom } from './dom.mjs';
import { createFocusManager } from '../js/focus.js';

/**
 * Where the keyboard cursor lands after the view is rebuilt.
 *
 * The app re-renders by destroying the stage and the drawer and building them
 * again. Whatever had focus — a headline link, a nav button — ceases to exist,
 * and focus falls to <body>. For anyone navigating by keyboard or screen reader
 * that is the entire navigation experience: activate a story, land silently at
 * the top of the document, walk the whole page again to get anywhere. Nothing
 * announces that a new view arrived, because from the accessibility tree's
 * point of view nothing *happened* — the document just changed underneath.
 *
 * These tests pin the four rules that follow, including the two negative ones,
 * which are the easy ones to lose: focus must NOT move on an ordinary re-render
 * (a fetch landing, a story marked read), and must NOT move on the first paint.
 * Getting either wrong is worse than the bug, because it steals the cursor from
 * someone mid-sentence.
 */

const doc = installDom();

/**
 * A short, printable name for an element.
 *
 * Assertions here compare these rather than the elements themselves: a failed
 * `deepEqual` on two DOM nodes sends node's formatter into a circular object
 * graph, and the run stops producing output instead of a failure message.
 */
function name(el) {
  if (!el) return String(el);
  const text = (el.textContent || '').trim().slice(0, 24);
  return `${el.tagName.toLowerCase()}${text ? `:${text}` : ''}`;
}

/** A stage and a drawer, plus a recorder for whose focus() was called. */
function harness() {
  const focused = [];
  const make = (tag, html) => {
    const el = doc.createElement(tag);
    el.innerHTML = html;
    doc.body.appendChild(el);
    return el;
  };
  const stage = make('div', '<h1>The Paper</h1><a href="#/reader/i1">A story</a>');
  const drawer = make('aside', '<h1>The Loom</h1><button>Close</button>');
  const navButton = doc.createElement('button');
  navButton.textContent = 'Loom';
  doc.body.appendChild(navButton);

  // linkedom has no focus tracking, so record the call instead: the decision is
  // the thing under test.
  for (const el of [stage, drawer, navButton, ...stage.querySelectorAll('*'), ...drawer.querySelectorAll('*')]) {
    el.focus = () => focused.push(name(el));
  }

  const manager = createFocusManager({ stage, drawer });
  // Settle the first paint, which is never a navigation.
  manager.settle({ view: 'stand', itemId: null }, null, null, false);
  return { manager, stage, drawer, navButton, focused };
}

const R = (view, itemId = null) => ({ view, itemId });

test('the first paint does not steal focus', () => {
  const focused = [];
  const stage = doc.createElement('div');
  stage.innerHTML = '<h1>The Paper</h1>';
  const drawer = doc.createElement('aside');
  doc.body.appendChild(stage);
  doc.body.appendChild(drawer);
  stage.querySelector('h1').focus = () => focused.push('h1');

  const manager = createFocusManager({ stage, drawer });
  assert.equal(manager.settle(R('stand'), null, null, false), null);
  assert.deepEqual(focused, [], 'nobody asked to go anywhere yet');
});

test('opening a story moves focus to the new view, so the change is announced', () => {
  const { manager, stage, focused } = harness();
  const link = stage.querySelector('a');

  const target = manager.settle(R('reader', 'i1'), null, link, false);
  assert.equal(name(target), 'h1:The Paper', 'focus lands on the new view’s heading');
  assert.ok(target === stage.querySelector('h1'));
  assert.equal(target.getAttribute('tabindex'), '-1', 'focusable programmatically, not by Tab');
  assert.ok(target.classList.contains('nooz-focus-target'), 'and carries no focus ring');
  assert.deepEqual(focused, ['h1:The Paper']);
});

test('an ordinary re-render leaves focus exactly where it was', () => {
  const { manager, stage, focused } = harness();
  manager.settle(R('reader', 'i1'), null, stage.querySelector('a'), false);
  focused.length = 0;

  // A fetch lands, a story is marked read, a setting toggles: same route.
  for (let i = 0; i < 3; i++) {
    assert.equal(name(manager.settle(R('reader', 'i1'), null, null, false)), 'null');
  }
  assert.deepEqual(focused, [], 'focus was not yanked out from under the reader');
});

test('a route change under a live text field is not re-fired later', () => {
  const { manager, stage, focused } = harness();

  // The reader is typing in search when a route change renders; the input takes
  // its own focus back, so we leave it alone.
  assert.equal(name(manager.settle(R('newsstand'), null, null, true)), 'null');
  assert.deepEqual(focused, []);

  // The next render is not a new route, so it must not act on the old one.
  assert.equal(name(manager.settle(R('newsstand'), null, null, false)), 'null');
  assert.deepEqual(focused, [], 'the stale route change did not fire late');

  // A genuinely new route still works.
  assert.ok(manager.settle(R('stand'), null, null, false) === stage.querySelector('h1'));
});

test('a drawer takes focus, and hands it back to the control that opened it', () => {
  const { manager, drawer, navButton, focused } = harness();

  assert.equal(
    name(manager.settle(R('loom'), 'loom', navButton, false)),
    'h1:The Loom',
    'focus moves into the drawer',
  );

  focused.length = 0;
  const closeButton = drawer.querySelector('button');
  const back = manager.settle(R('stand'), null, closeButton, false);
  assert.ok(back === navButton, 'closing gives focus back to the nav button that opened it');
  assert.deepEqual(focused, ['button:Loom']);
});

test('moving between drawers keeps the original door', () => {
  const { manager, drawer, navButton } = harness();
  manager.settle(R('loom'), 'loom', navButton, false);
  // Straight from the Loom to Settings without passing through the Paper.
  manager.settle(R('settings'), 'settings', drawer.querySelector('h1'), false);

  assert.ok(
    manager.settle(R('stand'), null, drawer.querySelector('button'), false) === navButton,
    'the way back is still the control that opened the first drawer',
  );
});

test('if the door is gone, focus falls to the stage heading rather than nowhere', () => {
  const { manager, stage, drawer, navButton } = harness();
  manager.settle(R('loom'), 'loom', navButton, false);
  navButton.remove();

  assert.ok(
    manager.settle(R('stand'), null, drawer.querySelector('button'), false) === stage.querySelector('h1'),
    'focus falls to the stage heading rather than nowhere',
  );
});

test('a region with no heading is itself the focus target', () => {
  const focused = [];
  const stage = doc.createElement('div');
  stage.innerHTML = '<p>Nothing flowed.</p>';
  const drawer = doc.createElement('aside');
  doc.body.appendChild(stage);
  doc.body.appendChild(drawer);
  stage.focus = () => focused.push('stage');

  const manager = createFocusManager({ stage, drawer });
  manager.settle(R('stand'), null, null, false);
  assert.ok(manager.settle(R('newsstand'), null, doc.body, false) === stage);
  assert.deepEqual(focused, ['stage']);
});
