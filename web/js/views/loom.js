// loom.js -- the Loom, the app's omission view brought to the web. The whole
// point of Nooz: set what *flowed* from your sources against what you actually
// *read*. Each topic is a woven thread whose full height is everything that
// flowed in it; the inked lower part is what you opened. The threads fade into
// the top and bottom of the page, the way the app's loom does. Below them, a
// heat strip shows which regions your reading actually came from.
//
// Everything here is computed from the same local data the Stand uses -- items
// that flowed (state.items) and what's been opened (state.readIds) -- so it's
// honest by construction: it can only show what your own sources served and
// what you did with it, never a claim about "all the news".

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

  // Flowed and read counts per topic.
  const flowed = new Map();
  const read = new Map();
  for (const item of items) {
    const topic = classifyItem(item);
    flowed.set(topic, (flowed.get(topic) || 0) + 1);
    if (state.readIds.has(item.id)) read.set(topic, (read.get(topic) || 0) + 1);
  }

  const rows = TOPICS.map((t) => ({
    key: t.key,
    label: t.label,
    flowed: flowed.get(t.key) || 0,
    read: read.get(t.key) || 0,
  })).filter((r) => r.flowed > 0)
    .sort((a, b) => b.flowed - a.flowed);

  root.appendChild(buildLoomBar(rows, items.length));
  root.appendChild(buildWeave(rows));
  root.appendChild(buildLegend(items, state));
  root.appendChild(buildRegionStrip(items, state));

  container.appendChild(root);
}

// ---------------------------------------------------------------------------
// The loom strip: the same rounded multi-colour bar, but standalone and
// clickable, dropped into the Paper and the Reader so the loom is always in
// reach. Tapping it slides it aside and opens the full Loom (the drawer, whose
// own first element is this very bar -- so it reads as the strip expanding).
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
  label.textContent = 'The Loom';
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
  hint.textContent = total ? `${readCount}/${total} read` : 'Expand';
  strip.appendChild(hint);

  const chev = document.createElement('span');
  chev.className = 'nooz-loomstrip-chev';
  chev.setAttribute('aria-hidden', 'true');
  chev.textContent = '›';
  strip.appendChild(chev);

  // Slide the strip aside, then open the Loom drawer (which slides in from the
  // right); the drawer's own bar is the same strip, so it reads as one motion.
  strip.addEventListener('click', () => {
    if (strip.dataset.launching === 'yes') return;
    strip.dataset.launching = 'yes';
    strip.classList.add('is-launching');
    let done = false;
    const go = () => { if (done) return; done = true; actions.goTo('loom'); };
    strip.addEventListener('transitionend', go, { once: true });
    setTimeout(go, 360);
  });

  return strip;
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
  return TOPICS.map((t) => ({ key: t.key, label: t.label, flowed: flowed.get(t.key) || 0, read: read.get(t.key) || 0 }))
    .filter((r) => r.flowed > 0)
    .sort((a, b) => b.flowed - a.flowed);
}

// ---------------------------------------------------------------------------
// The loom bar: a rounded, multi-coloured strip of the day's mix -- the thing
// that (in the drawer) expands into the full weave below it.
// ---------------------------------------------------------------------------

function buildLoomBar(rows, total) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-loombar-wrap';

  const bar = document.createElement('div');
  bar.className = 'nooz-loombar';
  bar.setAttribute('role', 'img');
  bar.setAttribute('aria-label', 'The day, by topic.');

  const sum = rows.reduce((a, r) => a + r.flowed, 0) || 1;
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
// The weave: one thread per topic
// ---------------------------------------------------------------------------

function buildWeave(rows) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-weave';

  const cols = rows.length;
  const colStep = 100 / cols; // percent
  const H = 300;
  const top = 26; // fade zone
  const baseline = H - 54; // room for labels
  const usable = baseline - top;
  const maxFlowed = Math.max(...rows.map((r) => r.flowed), 1);
  const barW = Math.min(48, (colStep * 0.52));

  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('class', 'nooz-weave-svg');
  svg.setAttribute('viewBox', `0 0 100 ${H}`);
  svg.setAttribute('preserveAspectRatio', 'none');
  svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', 'Topics that flowed, with the read portion inked.');

  // Gradient masks so threads fade into the top and bottom of the page.
  const defs = document.createElementNS(SVG_NS, 'defs');
  defs.appendChild(fadeGradient('nooz-fade-top', true));
  defs.appendChild(fadeGradient('nooz-fade-bot', false));
  svg.appendChild(defs);

  rows.forEach((row, i) => {
    const cx = colStep * (i + 0.5);
    const x = cx - barW / 2;
    const flowedH = (row.flowed / maxFlowed) * usable;
    const readH = row.flowed > 0 ? (row.read / row.flowed) * flowedH : 0;
    const yFlowed = baseline - flowedH;
    const yRead = baseline - readH;

    const hue = PALETTE[row.key] || PALETTE.general;

    // Full flowed thread (faint, topic-tinted).
    const faint = document.createElementNS(SVG_NS, 'rect');
    faint.setAttribute('x', x.toFixed(2));
    faint.setAttribute('y', yFlowed.toFixed(2));
    faint.setAttribute('width', barW.toFixed(2));
    faint.setAttribute('height', flowedH.toFixed(2));
    faint.setAttribute('class', 'nooz-thread-flowed');
    faint.style.fill = hue;
    svg.appendChild(faint);

    // Read portion (inked, full topic colour), rising from the baseline.
    if (readH > 0) {
      const ink = document.createElementNS(SVG_NS, 'rect');
      ink.setAttribute('x', x.toFixed(2));
      ink.setAttribute('y', yRead.toFixed(2));
      ink.setAttribute('width', barW.toFixed(2));
      ink.setAttribute('height', readH.toFixed(2));
      ink.setAttribute('class', 'nooz-thread-read');
      ink.style.fill = hue;
      svg.appendChild(ink);
    }
  });

  // Fade overlays top and bottom.
  const topFade = document.createElementNS(SVG_NS, 'rect');
  topFade.setAttribute('x', '0');
  topFade.setAttribute('y', '0');
  topFade.setAttribute('width', '100');
  topFade.setAttribute('height', String(top + 20));
  topFade.setAttribute('fill', 'url(#nooz-fade-top)');
  svg.appendChild(topFade);

  svg.appendChild(buildBaseline(baseline));
  wrap.appendChild(svg);

  // Labels are HTML (crisp text; the SVG is stretched via preserveAspectRatio).
  const labels = document.createElement('div');
  labels.className = 'nooz-weave-labels';
  for (const row of rows) {
    const cell = document.createElement('div');
    cell.className = 'nooz-weave-label';
    const name = document.createElement('span');
    name.className = 'nooz-weave-label-name';
    name.textContent = row.label;
    cell.appendChild(name);
    const count = document.createElement('span');
    count.className = 'nooz-weave-label-count';
    count.textContent = `${row.read}/${row.flowed}`;
    cell.appendChild(count);
    labels.appendChild(cell);
  }
  wrap.appendChild(labels);

  return wrap;
}

function buildBaseline(y) {
  const line = document.createElementNS(SVG_NS, 'line');
  line.setAttribute('x1', '0');
  line.setAttribute('x2', '100');
  line.setAttribute('y1', String(y));
  line.setAttribute('y2', String(y));
  line.setAttribute('class', 'nooz-weave-baseline');
  return line;
}

function fadeGradient(id, top) {
  const grad = document.createElementNS(SVG_NS, 'linearGradient');
  grad.setAttribute('id', id);
  grad.setAttribute('x1', '0');
  grad.setAttribute('y1', top ? '0' : '1');
  grad.setAttribute('x2', '0');
  grad.setAttribute('y2', top ? '1' : '0');
  const s0 = document.createElementNS(SVG_NS, 'stop');
  s0.setAttribute('offset', '0');
  s0.setAttribute('class', 'nooz-fade-stop-solid');
  const s1 = document.createElementNS(SVG_NS, 'stop');
  s1.setAttribute('offset', '1');
  s1.setAttribute('class', 'nooz-fade-stop-clear');
  grad.appendChild(s0);
  grad.appendChild(s1);
  return grad;
}

// ---------------------------------------------------------------------------
// Legend + region heat strip
// ---------------------------------------------------------------------------

function buildLegend(items, state) {
  const totalFlowed = items.length;
  const totalRead = items.filter((it) => state.readIds.has(it.id)).length;

  const legend = document.createElement('div');
  legend.className = 'nooz-loom-legend';

  legend.appendChild(legendSwatch('nooz-thread-read', `Read (${totalRead})`));
  legend.appendChild(legendSwatch('nooz-thread-flowed', `Flowed (${totalFlowed})`));

  return legend;
}

function legendSwatch(cls, label) {
  const item = document.createElement('span');
  item.className = 'nooz-legend-item';
  const sw = document.createElement('span');
  sw.className = `nooz-legend-swatch ${cls}`;
  item.appendChild(sw);
  const txt = document.createElement('span');
  txt.textContent = label;
  item.appendChild(txt);
  return item;
}

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
