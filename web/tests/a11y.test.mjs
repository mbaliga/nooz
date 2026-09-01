import assert from 'node:assert/strict';
import test from 'node:test';

import { installDom, recordingActions, baseState, item, controls } from './dom.mjs';

/**
 * The web reader's story cards, asserted as markup rather than as intention.
 *
 * Every story on the Paper used to be `role="button"` on the <article> itself,
 * with the whole article — kicker, headline, photo, byline, and in Continuous
 * mode the full extracted text — nested inside it. Two consequences, both
 * severe, and neither visible to a sighted tester:
 *
 *  - `button` computes its accessible name from its contents and flattens their
 *    structure. Each card's name was an entire article read as one unbroken
 *    string, and the <h2> inside it stopped being a heading. Heading navigation
 *    on the Paper returned the masthead and nothing else; the links list was
 *    empty. Those are the two ways a screen-reader user skims a page, and the
 *    front page offered neither.
 *  - The bookmark button and the image-style chips became interactive elements
 *    nested inside a button — invalid, and handled differently by every engine.
 *
 * The same shape was on the Clippings rows, where the card's name additionally
 * swallowed its own "Remove clipping" button.
 *
 * These tests run the real view modules against a real DOM and inspect what
 * comes out, so they fail if anyone reintroduces the pattern anywhere on these
 * two screens — including in a branch (Newspaper mode) a manual pass would
 * probably not have opened.
 */

const doc = installDom();

/**
 * A primary, unmodified left-click. linkedom has no MouseEvent, and the
 * handlers read `button` and the modifier keys to let ctrl/cmd-click open a new
 * tab, so a bare Event would be skipped as a modified click and the test would
 * pass for the wrong reason.
 */
function click() {
  const event = new doc.defaultView.Event('click', { bubbles: true });
  Object.assign(event, { button: 0, metaKey: false, ctrlKey: false, shiftKey: false, altKey: false });
  return event;
}

function renderStand(state) {
  const container = doc.createElement('div');
  return import('../js/views/stand.js').then((stand) => {
    stand.render(container, state, recordingActions());
    return container;
  });
}

function renderClippings(state) {
  const container = doc.createElement('div');
  return import('../js/views/clippings.js').then((clippings) => {
    clippings.render(container, state, recordingActions());
    return container;
  });
}

const threeStories = [
  item(),
  item({ id: 'i2', title: 'Second story' }),
  item({ id: 'i3', title: 'Third story' }),
];

for (const readingMode of ['continuous', 'newspaper']) {
  test(`${readingMode} mode: no story card claims to be a button`, async () => {
    const el = await renderStand(baseState({
      items: threeStories,
      settings: { readingMode, articleDisplay: 'excerpt' },
    }));

    const fakeButtons = [...el.querySelectorAll('[role="button"]')];
    assert.deepEqual(
      fakeButtons.map((n) => n.className),
      [],
      'a story card is pretending to be a button again',
    );
    // The corollary: nothing nests a real control inside a fake one.
    for (const control of controls(el)) {
      assert.equal(
        control.parentElement?.closest('[role="button"]'),
        null,
        `${control.tagName} is nested inside a role="button"`,
      );
    }
  });

  test(`${readingMode} mode: every headline is a heading containing a link`, async () => {
    const el = await renderStand(baseState({
      items: threeStories,
      settings: { readingMode, articleDisplay: 'excerpt' },
    }));

    const links = [...el.querySelectorAll('a.nooz-headline-link')];
    assert.equal(links.length, threeStories.length, 'one headline link per story');

    for (const link of links) {
      const heading = link.closest('h1, h2, h3, h4, h5, h6');
      assert.ok(heading, `"${link.textContent}" is a link but not inside a heading`);
      // The name a screen reader announces for the link is the headline alone —
      // not the article, which is the whole point of the change.
      assert.ok(link.getAttribute('href').startsWith('#/reader/'), 'the link goes to the reader');
      assert.ok(link.textContent.trim().length > 0, 'the link has a name');
    }

    // Every story's title is reachable by heading navigation.
    const headingText = [...el.querySelectorAll('h1, h2, h3')].map((h) => h.textContent);
    for (const story of threeStories) {
      assert.ok(
        headingText.some((t) => t.includes(story.title)),
        `"${story.title}" is not reachable by heading navigation`,
      );
    }
  });
}

test('an item id with a #, ? or space still produces a usable link', async () => {
  const awkward = 'https://example.com/a b?c=1#frag';
  const el = await renderStand(baseState({
    items: [item({ id: awkward })],
    settings: { readingMode: 'continuous', articleDisplay: 'excerpt' },
  }));
  const link = el.querySelector('a.nooz-headline-link');
  assert.equal(link.getAttribute('href'), `#/reader/${encodeURIComponent(awkward)}`);
});

test('read state is spoken, not only dimmed', async () => {
  const el = await renderStand(baseState({
    items: threeStories,
    readIds: new Set(['i2']),
    settings: { readingMode: 'continuous', articleDisplay: 'excerpt' },
  }));

  // `.is-read` only greys the headline, so before this the answer to "have I
  // read this?" was carried by colour alone.
  const marks = [...el.querySelectorAll('.nooz-visually-hidden')]
    .filter((n) => n.textContent.trim() === 'Read.');
  assert.equal(marks.length, 1, 'exactly the read story carries a spoken read marker');
  assert.ok(
    marks[0].closest('h1, h2, h3')?.textContent.includes('Second story'),
    'the marker sits on the story that was actually read',
  );
});

test('clippings rows are headings and links, not buttons wrapping buttons', async () => {
  const el = await renderClippings(baseState({
    clippings: [item(), item({ id: 'i2', title: 'Second clipping' })],
    readIds: new Set(['i1']),
  }));

  assert.deepEqual([...el.querySelectorAll('[role="button"]')], [], 'no fake buttons remain');

  const links = [...el.querySelectorAll('a.nooz-headline-link')];
  assert.equal(links.length, 2);
  for (const link of links) {
    assert.ok(link.closest('h1, h2, h3'), 'the clipping title is a heading');
  }

  // The real control that used to be buried inside the fake one.
  const unclip = el.querySelector('button[aria-label="Remove clipping"]');
  assert.ok(unclip, 'the unclip button is still there');
  assert.equal(unclip.closest('[role="button"]'), null, 'and is no longer inside a fake button');
});

test('the card is still clickable, and defers to the controls inside it', async () => {
  const actions = recordingActions();
  const container = doc.createElement('div');
  const stand = await import('../js/views/stand.js');
  stand.render(container, baseState({
    items: [item()],
    settings: { readingMode: 'continuous', articleDisplay: 'excerpt' },
  }), actions);

  const card = container.querySelector('.nooz-art-head, .nooz-lead, .nooz-col-story');
  assert.ok(card, 'a card element exists');

  // A click on the card's own chrome opens the story: the large pointer target
  // survives the change.
  const byline = card.querySelector('.nooz-byline');
  byline.dispatchEvent(click());
  assert.deepEqual(actions.calls.at(-1), ['openItem', 'i1']);

  // A click that starts on the headline link is the link's, and must not be
  // handled twice by the card behind it.
  const before = actions.calls.length;
  const link = card.querySelector('a.nooz-headline-link');
  link.dispatchEvent(click());
  assert.equal(
    actions.calls.length - before,
    1,
    'the click opened the item once, not once per handler',
  );
});

/**
 * The Loom, the app's centrepiece, had the same defect on the web that it had
 * on Android — and a worse one on top.
 *
 * Each tube was an SVG <path> carrying `role="button"`, `tabindex="0"` and its
 * own counts in an `aria-label`. None of that was ever reachable: the <svg>
 * around them declares `role="img"`, which makes its entire subtree one leaf in
 * the accessibility tree, and Safari does not honour `tabindex` on SVG shapes
 * regardless. The per-stream numbers — the whole point of the screen — existed
 * nowhere a screen reader or a keyboard could get at them, while the summary
 * ended "Tap a stream for its counts", instructing a gesture that had no
 * target.
 *
 * The summary was also enumerating only `bands.filter(b => b.consumed)`, so a
 * topic that flooded the feed and was never opened went unnamed — precisely the
 * omission the screen exists to show.
 */

function storiesAbout(prefix, title, n) {
  return Array.from({ length: n }, (_, i) => item({ id: `${prefix}${i}`, title: `${title} ${i}` }));
}

// 46 politics (5 read), 12 conflict (none read).
const loomItems = [
  ...storiesAbout('p', 'Parliament votes on legislation', 46),
  ...storiesAbout('c', 'Airstrike hits militant position', 12),
];
const loomState = () => baseState({
  items: loomItems,
  readIds: new Set(['p0', 'p1', 'p2', 'p3', 'p4']),
  sources: [
    { id: 's1', title: 'The Chronicle', enabled: true },
    { id: 's2', title: 'The Gazette', enabled: true },
  ],
});

function renderLoom(state) {
  const container = doc.createElement('div');
  return import('../js/views/loom.js').then((loom) => {
    loom.render(container, state, recordingActions());
    return container;
  });
}

test('the loom drawing exposes nothing it cannot deliver', async () => {
  const el = await renderLoom(loomState());
  const svg = el.querySelector('.nooz-loom-svg');
  assert.ok(svg, 'the loom drew');
  assert.equal(svg.getAttribute('role'), 'img', 'the drawing is a picture with one name');

  // role="img" prunes the subtree, so anything marked up as interactive inside
  // it is unreachable by definition. Nothing should claim otherwise.
  assert.equal(
    svg.querySelectorAll('[role], [tabindex], [aria-label]').length,
    0,
    'a descendant of the role="img" svg is advertising itself as reachable',
  );
});

test('every stream is reachable as a real, named control', async () => {
  const el = await renderLoom(loomState());
  const keys = [...el.querySelectorAll('.nooz-loom-key')];

  assert.equal(keys.length, 2, 'one key per stream that flowed');
  for (const key of keys) {
    assert.equal(key.tagName, 'BUTTON', 'the key is an ordinary button, not an SVG shape');
    assert.equal(key.getAttribute('aria-pressed'), 'false');
  }

  const labels = keys.map((k) => k.textContent);
  // Named with their numbers, not positionally: these are read in sequence, so
  // "stream two" would be useless.
  assert.ok(
    labels.some((l) => l.includes('Politics') && l.includes('46') && l.includes('5 read')),
    `politics carries its counts: ${labels}`,
  );
  assert.ok(
    labels.some((l) => l.includes('Conflict') && l.includes('12') && l.includes('none read')),
    `a stream that flowed but was never read is still reachable: ${labels}`,
  );
});

test('selecting a stream is announced, not only faded', async () => {
  const el = await renderLoom(loomState());
  const keys = [...el.querySelectorAll('.nooz-loom-key')];
  const politics = keys.find((k) => k.textContent.startsWith('Politics'));

  politics.dispatchEvent(click());
  assert.equal(politics.getAttribute('aria-pressed'), 'true');
  for (const other of keys.filter((k) => k !== politics)) {
    assert.equal(other.getAttribute('aria-pressed'), 'false');
  }

  politics.dispatchEvent(click());
  assert.equal(politics.getAttribute('aria-pressed'), 'false', 'clicking again deselects');
});

test('the spoken summary names the supply side, and instructs nothing it cannot do', async () => {
  const el = await renderLoom(loomState());
  const summary = el.querySelector('.nooz-loom-svg').getAttribute('aria-label');

  assert.ok(summary.includes('Conflict'), `names the unread flood: ${summary}`);
  assert.ok(summary.includes('58'), `gives the supply total: ${summary}`);
  assert.ok(summary.includes('You read 5'), `gives what was read: ${summary}`);
  assert.ok(!summary.includes('Tap a stream'), `must not instruct a tap: ${summary}`);
  assert.ok(summary.includes('2 sources'), `counts sources, and pluralises them: ${summary}`);
});
