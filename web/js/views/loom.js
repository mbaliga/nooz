// loom.js -- the Loom, the app's omission view brought faithfully to the web.
// The whole point of Nooz: set what *flowed* from your sources against what you
// actually *read*. The app's Loom has two ways of reading the same day, and this
// mirrors them:
//
//   - "Loom"     -- the woven surface: one tube per topic, its width at the top
//                   its share of everything that flowed; every tube plunges to a
//                   waist, and the topics you *read* pass through as thin stems
//                   and fan back out below with width by share of your reading,
//                   while topics you never opened pinch to nothing at the waist.
//                   The streams fade into the top and bottom of the page. The
//                   supply total sits top-left (SOURCE), your reading total sits
//                   bottom-right (CONSUMPTION); tap a stream for its own counts.
//   - "Contrast" -- the stark ledger: a nested Reach funnel (flowed -> read), a
//                   dumbbell per topic (its share of the stream vs its share of
//                   your reading, the gap between drawn as a line), and the
//                   read-by-region heat strip.
//
// The woven form and its geometry are a direct port of the Android app's pure
// DayLoomLayout (core/model) and DayLoomCanvas (feature/river); the contrast
// dumbbells and reach funnel port ContrastPanel. Everything is computed from the
// same local data the Stand uses -- items that flowed (state.items) and what's
// been opened (state.readIds) -- so it's honest by construction: it can only
// show what your own sources served and what you did with it.

import { classifyItem, TOPICS, TOPIC_LABEL } from '../topics.js';

const SVG_NS = 'http://www.w3.org/2000/svg';
const REGIONS = ['Europe', 'Americas', 'Asia', 'Middle East', 'Africa', 'Australia/Pacific'];

// A distinct hue per topic -- the loom is the one place the paper allows
// colour, matching the app's multi-coloured day bar.
const PALETTE = {
  politics: '#4C6EF5', conflict: '#E8590C', business: '#2B8A3E', tech: '#7048E8',
  science: '#1098AD', climate: '#66A80F', health: '#E64980', culture: '#F08C00',
  sport: '#0CA678', general: '#868E96',
};

// The pure geometry, ported verbatim from core/model/DayLoomLayout.kt. Coords
// live in the reference's own 420x880 viewBox; the SVG scales uniformly.
const W = 420, H = 880, MARGIN = 24, WAIST_Y = 520, GAP = 9;
const EASE = [[0.5, 0.55], [0.55, 0.5]]; // EASE_TOP, EASE_BOT

// Which way to read the day. Module-level so it survives the app's frequent
// re-renders (a background fetch completing shouldn't snap you back to "Loom").
let loomMode = 'loom'; // 'loom' | 'contrast'
let contrastSort = 'flowed'; // 'flowed' | 'read' | 'gap'
let loomRevealed = false; // the curtain sweep plays once per session, not per re-render

export function render(container, state, actions) {
  container.replaceChildren();

  const root = document.createElement('div');
  root.className = 'nooz-loom';

  const header = document.createElement('header');
  header.className = 'nooz-loom-header';
  const title = document.createElement('h1');
  title.className = 'nooz-loom-title';
  title.textContent = 'The Loom';
  header.appendChild(title);
  const sub = document.createElement('p');
  sub.className = 'nooz-loom-sub';
  sub.textContent = 'What flowed from your sources, woven against what you actually read.';
  header.appendChild(sub);
  root.appendChild(header);

  const items = state.items || [];
  if (items.length === 0) {
    root.appendChild(emptyState(state));
    container.appendChild(root);
    return;
  }

  // Flowed and read counts per topic key.
  const flowed = new Map();
  const read = new Map();
  for (const item of items) {
    const topic = classifyItem(item);
    flowed.set(topic, (flowed.get(topic) || 0) + 1);
    if (state.readIds.has(item.id)) read.set(topic, (read.get(topic) || 0) + 1);
  }

  const enabledSourceCount = (state.sources || []).filter((s) => s.enabled).length;

  // The multi-colour strip stays the drawer's first element, so opening the
  // Loom reads as the reader/paper strip expanding into it (see buildLoomStrip
  // and its shared-element flight animation).
  const rows = topicRowsFrom(flowed, read).sort((a, b) => b.flowed - a.flowed);
  const bar = buildLoomBar(rows, items.length);
  bar.classList.add('nooz-loombar-wrap--landing');
  root.appendChild(bar);

  // The two ways to read the day, as tabs (the app's Loom / Contrast toggle).
  const tabs = document.createElement('div');
  tabs.className = 'nooz-loom-tabs';
  tabs.setAttribute('role', 'tablist');
  root.appendChild(tabs);

  const panel = document.createElement('div');
  panel.className = 'nooz-loom-panel';
  root.appendChild(panel);

  const tabButtons = {};
  function makeTab(mode, label) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'nooz-loom-tab';
    b.setAttribute('role', 'tab');
    b.textContent = label;
    b.addEventListener('click', () => { loomMode = mode; draw(); });
    tabs.appendChild(b);
    tabButtons[mode] = b;
    return b;
  }
  makeTab('loom', 'Loom');
  makeTab('contrast', 'Contrast');

  function draw() {
    for (const [mode, b] of Object.entries(tabButtons)) {
      const active = mode === loomMode;
      b.classList.toggle('is-active', active);
      b.setAttribute('aria-selected', active ? 'true' : 'false');
    }
    panel.replaceChildren();
    if (loomMode === 'contrast') {
      panel.appendChild(buildContrastView(flowed, read, items, state, enabledSourceCount, draw));
    } else {
      panel.appendChild(buildLoomView(flowed, read, enabledSourceCount));
    }
  }
  draw();

  container.appendChild(root);
}

// ---------------------------------------------------------------------------
// The loom strip: a quiet, single-line hint -- not a card, not a button that
// shouts. It sits flush with the page, no fill, no border, so it reads as part
// of the paper rather than a control floating on top of it. Tapping it opens
// the full Loom; visually, the strip itself flies from its spot in the Paper
// to the same spot atop the Loom drawer (in sync with the paper sliding left
// to reveal the drawer beneath it), landing on -- and becoming -- the drawer's
// own bar, so it reads as one continuous motion, not a swap.
// ---------------------------------------------------------------------------

export function buildLoomStrip(state, actions) {
  const rows = topicRows(state);
  const total = (state.items || []).length;

  const strip = document.createElement('button');
  strip.type = 'button';
  strip.className = 'nooz-loomstrip';
  strip.setAttribute('aria-label', 'Open the Loom -- what flowed, woven against what you read');

  const label = document.createElement('span');
  label.className = 'nooz-loomstrip-label';
  label.textContent = 'Loom';
  strip.appendChild(label);

  const bar = document.createElement('span');
  bar.className = 'nooz-loombar nooz-loomstrip-bar';
  if (rows.length === 0) {
    bar.classList.add('is-empty');
  } else {
    for (const row of rows) {
      const seg = document.createElement('span');
      seg.className = 'nooz-loombar-seg';
      seg.style.flexGrow = String(row.flowed);
      seg.style.background = PALETTE[row.key] || PALETTE.general;
      bar.appendChild(seg);
    }
  }
  strip.appendChild(bar);

  const readCount = (state.items || []).filter((it) => state.readIds && state.readIds.has(it.id)).length;
  const hint = document.createElement('span');
  hint.className = 'nooz-loomstrip-hint';
  hint.textContent = total ? `${readCount}/${total}` : '';
  strip.appendChild(hint);

  // Fly the strip from its spot in the Paper to the drawer's bar, timed with
  // the paper's own slide-left (see .has-drawer .nooz-stage in style.css) so
  // the strip's rightward flight and the paper's leftward slide read as one
  // motion, and the strip settles into -- becomes -- the loom's own bar.
  strip.addEventListener('click', () => {
    if (strip.dataset.launching === 'yes') return;
    strip.dataset.launching = 'yes';
    flyStripToLoom(strip);
    actions.goTo('loom');
  });

  return strip;
}

function flyStripToLoom(strip) {
  const startRect = strip.getBoundingClientRect();
  const clone = strip.cloneNode(true);
  clone.removeAttribute('aria-label');
  clone.disabled = true;
  clone.className = 'nooz-loomstrip nooz-loomstrip-flight';
  clone.style.position = 'fixed';
  clone.style.margin = '0';
  clone.style.left = `${startRect.left}px`;
  clone.style.top = `${startRect.top}px`;
  clone.style.width = `${startRect.width}px`;
  document.body.appendChild(clone);
  strip.style.visibility = 'hidden';

  // Land on the drawer's own bar: same inline padding the drawer content uses,
  // near its top, at the drawer's own width (see .nooz-drawer / .nooz-drawer-content).
  requestAnimationFrame(() => {
    const drawerWidth = Math.min(440, window.innerWidth * 0.92);
    const pad = 20;
    const targetLeft = window.innerWidth - drawerWidth + pad;
    const targetTop = 84; // below the drawer's close button + Loom heading
    const targetWidth = drawerWidth - pad * 2;
    clone.style.transition = 'left 0.35s ease, top 0.35s ease, width 0.35s ease, opacity 0.2s ease 0.2s';
    clone.style.left = `${targetLeft}px`;
    clone.style.top = `${targetTop}px`;
    clone.style.width = `${targetWidth}px`;
  });

  setTimeout(() => {
    clone.style.opacity = '0';
    setTimeout(() => clone.remove(), 220);
  }, 350);
}

// Flowed/read counts per topic, ordered like the loom itself.
function topicRows(state) {
  const items = state.items || [];
  const flowed = new Map();
  const read = new Map();
  for (const item of items) {
    const topic = classifyItem(item);
    flowed.set(topic, (flowed.get(topic) || 0) + 1);
    if (state.readIds && state.readIds.has(item.id)) read.set(topic, (read.get(topic) || 0) + 1);
  }
  return topicRowsFrom(flowed, read).sort((a, b) => b.flowed - a.flowed);
}

// Rows in the canonical topic order (the loom's own order), flowed>0 only.
function topicRowsFrom(flowed, read) {
  return TOPICS.map((t) => ({
    key: t.key,
    label: t.label,
    flowed: flowed.get(t.key) || 0,
    read: read.get(t.key) || 0,
  })).filter((r) => r.flowed > 0);
}

// ---------------------------------------------------------------------------
// The loom bar: a rounded, multi-coloured strip of the day's mix -- the thing
// the loom strip flies into and becomes, atop the full weave below it.
// ---------------------------------------------------------------------------

function buildLoomBar(rows, total) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-loombar-wrap';

  const bar = document.createElement('div');
  bar.className = 'nooz-loombar';
  bar.setAttribute('role', 'img');
  bar.setAttribute('aria-label', 'The day, by topic.');

  for (const row of rows) {
    const seg = document.createElement('div');
    seg.className = 'nooz-loombar-seg';
    seg.style.flexGrow = String(row.flowed);
    seg.style.background = PALETTE[row.key] || PALETTE.general;
    seg.title = `${row.label}: ${row.flowed}`;
    bar.appendChild(seg);
  }
  wrap.appendChild(bar);

  const caption = document.createElement('p');
  caption.className = 'nooz-loombar-caption';
  caption.textContent = `${total} ${total === 1 ? 'story' : 'stories'} flowed, across ${rows.length} ${rows.length === 1 ? 'topic' : 'topics'}.`;
  wrap.appendChild(caption);

  return wrap;
}

// ---------------------------------------------------------------------------
// The woven Loom -- a direct port of the app's DayLoomLayout + DayLoomCanvas.
// ---------------------------------------------------------------------------

// layout(streamByTopic, readByTopic) -> { bands, totalFlowed, totalRead }.
// Ported from DayLoomLayout.layout (core/model).
function layoutLoom(flowed, read) {
  const entries = TOPICS.map((t) => {
    const f = flowed.get(t.key) || 0;
    if (f <= 0) return null;
    const r = Math.max(0, Math.min(read.get(t.key) || 0, f));
    return { key: t.key, label: t.label, flowed: f, read: r };
  }).filter(Boolean);

  const totalFlowed = entries.reduce((a, e) => a + e.flowed, 0);
  const totalRead = entries.reduce((a, e) => a + e.read, 0);
  if (totalFlowed === 0) return { bands: [], totalFlowed: 0, totalRead: 0 };

  const usable = W - MARGIN * 2 - GAP * (entries.length - 1);
  const consumed = entries.filter((e) => e.read > 0).slice().sort((a, b) => b.read - a.read);

  // Bottom fan: read topics spread across the width, widest first, width by
  // share of reads (the reference's 262px fan budget).
  const fanBudget = 262, fanGap = 24;
  const fanWidths = consumed.map((e) => (e.read / totalRead) * fanBudget);
  const fanTotal = fanWidths.reduce((a, w) => a + w, 0) + fanGap * Math.max(0, consumed.length - 1);
  let fanCursor = W / 2 - fanTotal / 2;
  const botCenter = new Map(), botWidth = new Map();
  consumed.forEach((e, i) => {
    botCenter.set(e.key, fanCursor + fanWidths[i] / 2);
    botWidth.set(e.key, fanWidths[i]);
    fanCursor += fanWidths[i] + fanGap;
  });

  // Stems: consumed tubes pass the waist as thin offset threads.
  const stemSpread = 3;
  const stemOffset = new Map(), stemWidth = new Map();
  consumed.forEach((e, i) => {
    const t = consumed.length === 1 ? 0.5 : i / (consumed.length - 1);
    stemOffset.set(e.key, -stemSpread + t * 2 * stemSpread);
    stemWidth.set(e.key, 1.5 + 2.0 * (e.read / consumed[0].read));
  });

  let cursor = MARGIN;
  const bands = entries.map((e) => {
    const topW = Math.max((e.flowed / totalFlowed) * usable, 3);
    const topX = cursor + topW / 2;
    cursor += topW + GAP;
    let stations;
    if (e.read > 0) {
      stations = [
        { y: 0, x: topX, w: topW },
        { y: WAIST_Y, x: W / 2 + stemOffset.get(e.key), w: stemWidth.get(e.key) },
        { y: H, x: botCenter.get(e.key), w: botWidth.get(e.key) },
      ];
    } else {
      const drift = ((topX - W / 2) / (W / 2)) * 4.0;
      stations = [
        { y: 0, x: topX, w: topW },
        { y: WAIST_Y, x: W / 2 + drift, w: 0.5 },
      ];
    }
    return { ...e, consumed: e.read > 0, stations };
  });

  // Reference draw order: unread first, then consumed ascending by read so the
  // largest consumed band lands on top.
  const ordered = bands.filter((b) => !b.consumed)
    .concat(bands.filter((b) => b.consumed).sort((a, b) => a.read - b.read));
  return { bands: ordered, totalFlowed, totalRead };
}

// One tube's closed SVG path from its stations (port of tubePath()).
function tubePathD(st) {
  const seg = [];
  const f = st[0];
  seg.push(`M ${(f.x - f.w / 2).toFixed(2)} ${f.y.toFixed(2)}`);
  for (let i = 0; i < st.length - 1; i++) {
    const a = st[i], b = st[i + 1], dy = b.y - a.y;
    const [k1, k2] = EASE[i];
    seg.push(`C ${(a.x - a.w / 2).toFixed(2)} ${(a.y + dy * k1).toFixed(2)}, ` +
      `${(b.x - b.w / 2).toFixed(2)} ${(b.y - dy * k2).toFixed(2)}, ` +
      `${(b.x - b.w / 2).toFixed(2)} ${b.y.toFixed(2)}`);
  }
  const last = st[st.length - 1];
  seg.push(`L ${(last.x + last.w / 2).toFixed(2)} ${last.y.toFixed(2)}`);
  for (let i = st.length - 1; i >= 1; i--) {
    const a = st[i], b = st[i - 1], dy = b.y - a.y;
    const [k1, k2] = EASE[i - 1];
    seg.push(`C ${(a.x + a.w / 2).toFixed(2)} ${(a.y + dy * k2).toFixed(2)}, ` +
      `${(b.x + b.w / 2).toFixed(2)} ${(b.y - dy * k1).toFixed(2)}, ` +
      `${(b.x + b.w / 2).toFixed(2)} ${b.y.toFixed(2)}`);
  }
  seg.push('Z');
  return seg.join(' ');
}

function buildLoomView(flowed, read, enabledSourceCount) {
  const loom = layoutLoom(flowed, read);

  if (loom.totalFlowed === 0) {
    const box = document.createElement('div');
    box.className = 'nooz-empty-state';
    const t = document.createElement('p');
    t.className = 'nooz-empty-state-text';
    t.textContent = 'Nothing flowed this day. The loom weaves once your sources do.';
    box.appendChild(t);
    return box;
  }

  const wrap = document.createElement('div');
  wrap.className = 'nooz-weave';

  const stage = document.createElement('div');
  stage.className = 'nooz-loom-stage';
  if (!loomRevealed) {
    stage.classList.add('is-revealing');
    loomRevealed = true;
  }

  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('class', 'nooz-loom-svg');
  svg.setAttribute('viewBox', `0 0 ${W} ${H}`);
  svg.setAttribute('preserveAspectRatio', 'xMidYMid meet');
  svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', describeLoom(loom, enabledSourceCount));

  // Fade mask: tube ends dissolve into the page top and bottom (the app's
  // DstIn vertical gradient), so no hard stubs.
  const defs = document.createElementNS(SVG_NS, 'defs');
  const grad = document.createElementNS(SVG_NS, 'linearGradient');
  grad.setAttribute('id', 'nooz-loom-fade-grad');
  grad.setAttribute('x1', '0'); grad.setAttribute('y1', '0');
  grad.setAttribute('x2', '0'); grad.setAttribute('y2', '1');
  for (const [off, op] of [['0', '0'], ['0.11', '1'], ['0.89', '1'], ['1', '0']]) {
    const s = document.createElementNS(SVG_NS, 'stop');
    s.setAttribute('offset', off);
    s.setAttribute('stop-color', '#fff');
    s.setAttribute('stop-opacity', op);
    grad.appendChild(s);
  }
  defs.appendChild(grad);
  const mask = document.createElementNS(SVG_NS, 'mask');
  mask.setAttribute('id', 'nooz-loom-fade');
  mask.setAttribute('maskUnits', 'userSpaceOnUse');
  mask.setAttribute('x', '0'); mask.setAttribute('y', '0');
  mask.setAttribute('width', String(W)); mask.setAttribute('height', String(H));
  const maskRect = document.createElementNS(SVG_NS, 'rect');
  maskRect.setAttribute('x', '0'); maskRect.setAttribute('y', '0');
  maskRect.setAttribute('width', String(W)); maskRect.setAttribute('height', String(H));
  maskRect.setAttribute('fill', 'url(#nooz-loom-fade-grad)');
  mask.appendChild(maskRect);
  defs.appendChild(mask);
  svg.appendChild(defs);

  const group = document.createElementNS(SVG_NS, 'g');
  group.setAttribute('mask', 'url(#nooz-loom-fade)');
  svg.appendChild(group);

  // Nothing read yet: a dotted ghost funnel from the waist to the bottom
  // stands in for the fan reading would draw (owner's empty-loom idiom), so the
  // lower half reads as "zero", not "broken".
  if (loom.totalRead === 0) {
    for (const c of ghostFanDots()) {
      const dot = document.createElementNS(SVG_NS, 'circle');
      dot.setAttribute('cx', c.x.toFixed(2));
      dot.setAttribute('cy', c.y.toFixed(2));
      dot.setAttribute('r', '1.6');
      dot.setAttribute('class', 'nooz-loom-ghost');
      group.appendChild(dot);
    }
  }

  // The tubes, in draw order.
  const paths = [];
  let selectedKey = null;
  for (const band of loom.bands) {
    const path = document.createElementNS(SVG_NS, 'path');
    path.setAttribute('d', tubePathD(band.stations));
    path.setAttribute('fill', PALETTE[band.key] || PALETTE.general);
    path.setAttribute('class', 'nooz-loom-tube');
    path.style.cursor = 'pointer';
    path.setAttribute('tabindex', '0');
    path.setAttribute('role', 'button');
    path.setAttribute('aria-label',
      `${band.label}: ${band.flowed} flowed${band.read > 0 ? `, ${band.read} read` : ', none read'}`);
    const pick = () => selectBand(selectedKey === band.key ? null : band);
    path.addEventListener('click', pick);
    path.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); pick(); }
    });
    group.appendChild(path);
    paths.push({ band, path });
  }

  stage.appendChild(svg);

  // Big numbers: SOURCE (supply) top-left, CONSUMPTION (your reading)
  // bottom-right -- the app's framing of the same two totals.
  const src = document.createElement('div');
  src.className = 'nooz-loom-num nooz-loom-num--src';
  src.innerHTML = `<span class="nooz-loom-num-value">${formatCompactCount(loom.totalFlowed)}</span>` +
    `<span class="nooz-loom-num-caption">SOURCE</span>`;
  stage.appendChild(src);

  const con = document.createElement('div');
  con.className = 'nooz-loom-num nooz-loom-num--con';
  con.innerHTML = `<span class="nooz-loom-num-caption">CONSUMPTION</span>` +
    `<span class="nooz-loom-num-value">${formatCompactCount(loom.totalRead)}</span>`;
  stage.appendChild(con);

  // Inspector (label mode "tap"): the selected tube's honest line.
  const inspect = document.createElement('div');
  inspect.className = 'nooz-loom-inspect';
  inspect.hidden = true;
  stage.appendChild(inspect);

  // Denominator honesty, verbatim register from the app.
  const denom = document.createElement('div');
  denom.className = 'nooz-loom-denom';
  denom.textContent = `${loom.totalFlowed} flowed · ${loom.totalRead} read · from ${enabledSourceCount} ${enabledSourceCount === 1 ? 'source' : 'sources'}`;
  stage.appendChild(denom);

  function selectBand(band) {
    selectedKey = band ? band.key : null;
    for (const { band: b, path } of paths) {
      const faded = selectedKey != null && b.key !== selectedKey;
      path.style.opacity = faded ? '0.22' : '1';
    }
    if (band) {
      inspect.hidden = false;
      inspect.innerHTML = '';
      const name = document.createElement('span');
      name.className = 'nooz-loom-inspect-name';
      name.textContent = band.label.toUpperCase();
      name.style.color = PALETTE[band.key] || PALETTE.general;
      inspect.appendChild(name);
      const detail = document.createElement('span');
      detail.className = 'nooz-loom-inspect-detail';
      detail.textContent = `${band.flowed} flowed` + (band.read > 0 ? ` · ${band.read} read` : ' · none read');
      inspect.appendChild(detail);
    } else {
      inspect.hidden = true;
    }
  }

  wrap.appendChild(stage);
  return wrap;
}

// The empty-consumption placeholder: a dot-matrix funnel from the waist to the
// bottom edge, widening the way a real read-fan would (port of drawGhostFan).
function ghostFanDots() {
  const dots = [];
  const centerX = W / 2;
  const halfAtBottom = 70;
  const stepX = 10, stepY = 14;
  for (let y = WAIST_Y + stepY; y < H; y += stepY) {
    const t = Math.min(1, Math.max(0, (y - WAIST_Y) / (H - WAIST_Y)));
    const half = halfAtBottom * t;
    for (let x = centerX - half; x <= centerX + half; x += stepX) {
      dots.push({ x, y });
    }
  }
  return dots;
}

function describeLoom(loom, enabledSourceCount) {
  if (loom.totalFlowed === 0) return `Nothing flowed from your ${enabledSourceCount} sources this day.`;
  const read = loom.bands.filter((b) => b.consumed).slice().sort((a, b) => b.read - a.read);
  const readLine = read.length === 0
    ? 'none of it read'
    : `${loom.totalRead} read: ` + read.map((b) => `${b.label} ${b.read}`).join(', ');
  return `Day loom. ${loom.totalFlowed} stories flowed from your ${enabledSourceCount} sources; ${readLine}. Tap a stream for its counts.`;
}

// ---------------------------------------------------------------------------
// The Contrast ledger -- the loom's stark counterpart (port of ContrastPanel).
// ---------------------------------------------------------------------------

function buildContrastView(flowed, read, items, state, enabledSourceCount, rerender) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-contrast';

  const rows = TOPICS.map((t) => {
    const f = flowed.get(t.key) || 0;
    const r = read.get(t.key) || 0;
    return { key: t.key, label: t.label, flowed: f, read: r };
  }).filter((row) => row.flowed > 0 || row.read > 0);

  const totalFlowed = rows.reduce((a, r) => a + r.flowed, 0);
  const totalRead = rows.reduce((a, r) => a + r.read, 0);

  for (const row of rows) {
    row.flowedShare = totalFlowed === 0 ? 0 : row.flowed / totalFlowed;
    row.readShare = totalRead === 0 ? 0 : row.read / totalRead;
    row.gap = row.flowedShare - row.readShare; // + = blind spot, - = fixation
  }

  // ---- Reach: one nested funnel bar (everything flowed -> what you read) ----
  const reach = document.createElement('section');
  reach.className = 'nooz-contrast-section';
  reach.appendChild(sectionHeading('Reach'));

  const funnel = document.createElement('div');
  funnel.className = 'nooz-funnel';
  const funnelRead = document.createElement('div');
  funnelRead.className = 'nooz-funnel-read';
  funnelRead.style.width = `${Math.round((totalFlowed === 0 ? 0 : totalRead / totalFlowed) * 100)}%`;
  funnel.appendChild(funnelRead);
  reach.appendChild(funnel);

  const funnelLegend = document.createElement('div');
  funnelLegend.className = 'nooz-funnel-legend';
  funnelLegend.appendChild(funnelKey('Flowed', totalFlowed, 'is-flowed'));
  funnelLegend.appendChild(funnelKey('Read', totalRead, 'is-read'));
  reach.appendChild(funnelLegend);

  const reachLine = document.createElement('p');
  reachLine.className = 'nooz-contrast-note';
  reachLine.textContent = `You read ${totalRead} of the ${totalFlowed} ${totalFlowed === 1 ? 'story' : 'stories'} that flowed · ` +
    `${enabledSourceCount} ${enabledSourceCount === 1 ? 'source' : 'sources'} on.`;
  reach.appendChild(reachLine);
  wrap.appendChild(reach);

  // ---- By topic: dumbbells + a spare sort lever ----
  const byTopic = document.createElement('section');
  byTopic.className = 'nooz-contrast-section';

  const head = document.createElement('div');
  head.className = 'nooz-contrast-head';
  head.appendChild(sectionHeading('By topic'));
  const sortRow = document.createElement('div');
  sortRow.className = 'nooz-sort';
  sortRow.setAttribute('role', 'radiogroup');
  for (const [key, label] of [['flowed', 'Flowed'], ['read', 'Read'], ['gap', 'Gap']]) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'nooz-sort-opt';
    b.textContent = label;
    b.setAttribute('role', 'radio');
    const chosen = contrastSort === key;
    b.classList.toggle('is-active', chosen);
    b.setAttribute('aria-checked', chosen ? 'true' : 'false');
    b.addEventListener('click', () => { contrastSort = key; rerender(); });
    sortRow.appendChild(b);
  }
  head.appendChild(sortRow);
  byTopic.appendChild(head);

  const ordered = rows.slice().sort((a, b) => {
    if (contrastSort === 'read') return b.read - a.read;
    if (contrastSort === 'gap') return b.gap - a.gap;
    return b.flowed - a.flowed;
  });

  const blindSpot = ordered.reduce((best, r) => (best == null || r.gap > best.gap ? r : best), null);
  if (totalRead > 0 && blindSpot && blindSpot.gap > 0.08) {
    const note = document.createElement('p');
    note.className = 'nooz-contrast-note';
    note.textContent = `Widest gap in ${blindSpot.label}: ${Math.round(blindSpot.flowedShare * 100)}% flowed, ${Math.round(blindSpot.readShare * 100)}% read.`;
    byTopic.appendChild(note);
  }

  const maxShare = rows.reduce((m, r) => Math.max(m, r.flowedShare, r.readShare), 0) || 1;
  for (const row of ordered) {
    byTopic.appendChild(buildDumbbell(row, maxShare));
  }

  const dotLegend = document.createElement('div');
  dotLegend.className = 'nooz-dot-legend';
  dotLegend.appendChild(dotKey(false, 'flowed'));
  dotLegend.appendChild(dotKey(true, 'read'));
  byTopic.appendChild(dotLegend);
  wrap.appendChild(byTopic);

  // ---- Regions: read-by-region heat strip ----
  wrap.appendChild(buildRegionStrip(items, state));

  return wrap;
}

// One topic as a dumbbell: a hollow ring at its share of the stream, a solid
// dot at its share of your reading, a hairline between them (the gap).
function buildDumbbell(row, maxShare) {
  const hue = PALETTE[row.key] || PALETTE.general;
  const el = document.createElement('div');
  el.className = 'nooz-dumbbell';

  const head = document.createElement('div');
  head.className = 'nooz-dumbbell-head';
  const dot = document.createElement('span');
  dot.className = 'nooz-dumbbell-topicdot';
  dot.style.background = hue;
  head.appendChild(dot);
  const name = document.createElement('span');
  name.className = 'nooz-dumbbell-name';
  name.textContent = row.label;
  head.appendChild(name);
  const pct = document.createElement('span');
  pct.className = 'nooz-dumbbell-pct';
  pct.textContent = `${Math.round(row.flowedShare * 100)}% / ${Math.round(row.readShare * 100)}%`;
  head.appendChild(pct);
  el.appendChild(head);

  const flowedFrac = Math.min(1, Math.max(0, row.flowedShare / maxShare));
  const readFrac = Math.min(1, Math.max(0, row.readShare / maxShare));
  const lo = Math.min(flowedFrac, readFrac);
  const hi = Math.max(flowedFrac, readFrac);

  const track = document.createElement('div');
  track.className = 'nooz-dumbbell-track';

  const connector = document.createElement('span');
  connector.className = 'nooz-dumbbell-connector';
  connector.style.background = hue;
  connector.style.left = `calc(var(--db-pad) + (100% - var(--db-pad) * 2) * ${lo})`;
  connector.style.width = `calc((100% - var(--db-pad) * 2) * ${hi - lo})`;
  track.appendChild(connector);

  const flowedDot = document.createElement('span');
  flowedDot.className = 'nooz-dumbbell-dot nooz-dumbbell-dot--flowed';
  flowedDot.style.borderColor = hue;
  flowedDot.style.left = `calc(var(--db-pad) + (100% - var(--db-pad) * 2) * ${flowedFrac})`;
  track.appendChild(flowedDot);

  const readDot = document.createElement('span');
  readDot.className = 'nooz-dumbbell-dot nooz-dumbbell-dot--read';
  readDot.style.background = hue;
  readDot.style.left = `calc(var(--db-pad) + (100% - var(--db-pad) * 2) * ${readFrac})`;
  track.appendChild(readDot);

  el.appendChild(track);
  return el;
}

function funnelKey(label, count, cls) {
  const item = document.createElement('div');
  item.className = 'nooz-funnel-key';
  const sw = document.createElement('span');
  sw.className = `nooz-funnel-swatch ${cls}`;
  item.appendChild(sw);
  const col = document.createElement('span');
  col.className = 'nooz-funnel-key-text';
  col.innerHTML = `<span class="nooz-funnel-key-label">${label.toUpperCase()}</span>` +
    `<span class="nooz-funnel-key-count">${count}</span>`;
  item.appendChild(col);
  return item;
}

function dotKey(filled, label) {
  const item = document.createElement('span');
  item.className = 'nooz-dot-key';
  const sw = document.createElement('span');
  sw.className = `nooz-dot-key-dot ${filled ? 'is-filled' : 'is-hollow'}`;
  item.appendChild(sw);
  const txt = document.createElement('span');
  txt.textContent = label;
  item.appendChild(txt);
  return item;
}

function sectionHeading(text) {
  const h = document.createElement('h2');
  h.className = 'nooz-section-title';
  h.textContent = text;
  return h;
}

// ---------------------------------------------------------------------------
// Region heat strip -- where your reading actually came from.
// ---------------------------------------------------------------------------

function buildRegionStrip(items, state) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-region-strip';

  const heading = document.createElement('p');
  heading.className = 'nooz-section-title';
  heading.textContent = 'Where your reading came from';
  wrap.appendChild(heading);

  const regionOf = new Map((state.sources || []).map((s) => [s.id, s.region]));
  const readByRegion = new Map();
  let anyRead = 0;
  for (const item of items) {
    if (!state.readIds.has(item.id)) continue;
    const region = regionOf.get(item.sourceId) || 'Unknown';
    readByRegion.set(region, (readByRegion.get(region) || 0) + 1);
    anyRead += 1;
  }

  if (anyRead === 0) {
    const none = document.createElement('p');
    none.className = 'nooz-empty-state-text';
    none.style.textAlign = 'left';
    none.style.maxWidth = 'none';
    none.textContent = "You haven't read anything yet -- the strip fills in as you open stories.";
    wrap.appendChild(none);
    return wrap;
  }

  const max = Math.max(...readByRegion.values(), 1);
  const grid = document.createElement('div');
  grid.className = 'nooz-region-grid';
  for (const region of REGIONS.concat(readByRegion.has('Unknown') ? ['Unknown'] : [])) {
    const count = readByRegion.get(region) || 0;
    const cell = document.createElement('div');
    cell.className = 'nooz-region-cell';

    const bar = document.createElement('div');
    bar.className = 'nooz-region-bar';
    const fill = document.createElement('div');
    fill.className = 'nooz-region-fill';
    fill.style.height = `${Math.round((count / max) * 100)}%`;
    if (count === 0) fill.classList.add('is-empty');
    bar.appendChild(fill);
    cell.appendChild(bar);

    const label = document.createElement('span');
    label.className = 'nooz-region-label';
    label.textContent = region === 'Australia/Pacific' ? 'Aus/Pac' : region === 'Middle East' ? 'Mid East' : region;
    cell.appendChild(label);

    const num = document.createElement('span');
    num.className = 'nooz-region-num';
    num.textContent = String(count);
    cell.appendChild(num);

    grid.appendChild(cell);
  }
  wrap.appendChild(grid);
  return wrap;
}

// Compact count label: 950 -> "950", 10000 -> "10k", 12345 -> "12.3k"
// (port of formatCompactCount from core/model).
function formatCompactCount(count) {
  if (count < 1000) return String(count);
  if (count < 1000000) {
    const tenths = Math.floor(count / 100);
    return tenths % 10 === 0 ? `${Math.floor(tenths / 10)}k` : `${Math.floor(tenths / 10)}.${tenths % 10}k`;
  }
  const tenths = Math.floor(count / 100000);
  return tenths % 10 === 0 ? `${Math.floor(tenths / 10)}M` : `${Math.floor(tenths / 10)}.${tenths % 10}M`;
}

function emptyState(state) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-empty-state';
  const title = document.createElement('p');
  title.className = 'nooz-empty-state-title';
  title.textContent = 'Nothing to weave yet';
  wrap.appendChild(title);
  const text = document.createElement('p');
  text.className = 'nooz-empty-state-text';
  text.textContent = state.sources && state.sources.length
    ? 'Once your sources flow, the loom weaves what came through against what you read.'
    : 'Add some sources first -- the loom weaves what flows from them against what you read.';
  wrap.appendChild(text);
  return wrap;
}
