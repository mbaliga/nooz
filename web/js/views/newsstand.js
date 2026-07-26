// newsstand.js -- "The Stand": a rack of papers. The reader chooses how the
// papers are arranged -- by Category, by Publisher, or by Region -- and each
// pile is a little folded newspaper showing its count and its latest
// headline. Picking one focuses the Paper on that slice and hands back to it.
//
// It's a browse surface, not a reader: everything here is derived from the
// same visible items, so it can only ever show what actually flowed.

import { classifyItem, TOPIC_LABEL, TOPICS } from '../topics.js';

// Kept across re-renders so a background refresh doesn't reset the arrangement.
let grouping = 'category';

const GROUPINGS = [
  { key: 'category', label: 'Category' },
  { key: 'publisher', label: 'Publisher' },
  { key: 'region', label: 'Region' },
];

export function render(container, state, actions) {
  container.replaceChildren();

  const root = document.createElement('div');
  root.className = 'nooz-newsstand';

  const header = document.createElement('header');
  header.className = 'nooz-newsstand-header';
  const title = document.createElement('h1');
  title.className = 'nooz-newsstand-title';
  title.textContent = 'The Stand';
  header.appendChild(title);
  const sub = document.createElement('p');
  sub.className = 'nooz-newsstand-sub';
  sub.textContent = 'Browse the papers. Pick a pile to read that slice.';
  header.appendChild(sub);
  root.appendChild(header);

  // Arrangement switch.
  const seg = document.createElement('div');
  seg.className = 'nooz-seg';
  for (const g of GROUPINGS) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'nooz-seg-btn' + (grouping === g.key ? ' is-active' : '');
    btn.textContent = g.label;
    btn.setAttribute('aria-pressed', grouping === g.key ? 'true' : 'false');
    btn.addEventListener('click', () => {
      grouping = g.key;
      actions.refreshView();
    });
    seg.appendChild(btn);
  }
  root.appendChild(seg);

  const items = (state.items || []);
  // The newsstand shows the whole shelf, not the currently-focused slice:
  // build from allItems restricted to enabled sources, ignoring topic/source
  // focus so you can always switch piles.
  const shelfItems = shelf(state);

  if (shelfItems.length === 0) {
    root.appendChild(empty(state));
    container.appendChild(root);
    return;
  }

  const groups = buildGroups(grouping, shelfItems, state);
  const rack = document.createElement('div');
  rack.className = 'nooz-rack';
  for (const group of groups) rack.appendChild(buildPaper(group, actions));
  root.appendChild(rack);

  container.appendChild(root);
}

// Enabled-source items, region filter honoured, but NOT topic/source focus.
function shelf(state) {
  const enabled = new Set((state.sources || []).filter((s) => s.enabled).map((s) => s.id));
  let list = (state.allItems || []).filter((it) => enabled.has(it.sourceId));
  if (state.regionFilter) {
    const inRegion = new Set((state.sources || []).filter((s) => s.region === state.regionFilter).map((s) => s.id));
    list = list.filter((it) => inRegion.has(it.sourceId));
  }
  return list;
}

function buildGroups(mode, items, state) {
  const byKey = new Map();
  const push = (key, label, item, focus) => {
    if (!byKey.has(key)) byKey.set(key, { key, label, focus, items: [] });
    byKey.get(key).items.push(item);
  };

  if (mode === 'publisher') {
    const titleOf = new Map((state.sources || []).map((s) => [s.id, s.title || s.url]));
    for (const it of items) push(it.sourceId, titleOf.get(it.sourceId) || 'Unknown', it, { kind: 'source', value: it.sourceId });
  } else if (mode === 'region') {
    const regionOf = new Map((state.sources || []).map((s) => [s.id, s.region || 'Unknown']));
    for (const it of items) {
      const r = regionOf.get(it.sourceId) || 'Unknown';
      push(r, r, it, { kind: 'region', value: r === 'Unknown' ? null : r });
    }
  } else {
    for (const it of items) {
      const t = classifyItem(it);
      push(t, TOPIC_LABEL[t] || 'General', it, { kind: 'topic', value: t });
    }
  }

  const groups = Array.from(byKey.values());
  // Category piles follow the taxonomy order; others by size.
  if (mode === 'category') {
    const order = new Map(TOPICS.map((t, i) => [t.key, i]));
    groups.sort((a, b) => (order.get(a.key) ?? 99) - (order.get(b.key) ?? 99));
  } else {
    groups.sort((a, b) => b.items.length - a.items.length);
  }
  // Latest headline per pile.
  for (const g of groups) g.items.sort((a, b) => b.publishedAt - a.publishedAt);
  // Brand each pile as its own paper -- "Sports Nooz", "Politics Nooz", ...
  // Publishers keep their own masthead (they're already papers).
  for (const g of groups) g.mast = mode === 'publisher' ? g.label : `${g.label} Nooz`;
  return groups;
}

// Each pile is a small folded newspaper, all the same size: a masthead
// ("Sports Nooz"), an edition line, the latest headline, and a strip of faux
// newsprint so it reads as a paper at a glance.
function buildPaper(group, actions) {
  const paper = document.createElement('button');
  paper.type = 'button';
  paper.className = 'nooz-paper-card';
  paper.setAttribute('aria-label', `Open ${group.mast} (${group.items.length})`);

  const ruleTop = document.createElement('div');
  ruleTop.className = 'nooz-np-rule';
  paper.appendChild(ruleTop);

  const name = document.createElement('div');
  name.className = 'nooz-np-name';
  name.textContent = group.mast;
  paper.appendChild(name);

  const ruleBot = document.createElement('div');
  ruleBot.className = 'nooz-np-rule nooz-np-rule--double';
  paper.appendChild(ruleBot);

  const ed = document.createElement('p');
  ed.className = 'nooz-np-ed';
  ed.textContent = `${group.items.length} ${group.items.length === 1 ? 'story' : 'stories'}`;
  paper.appendChild(ed);

  const lead = group.items[0];
  if (lead) {
    const head = document.createElement('p');
    head.className = 'nooz-np-lead';
    head.textContent = lead.title || '(untitled)';
    paper.appendChild(head);
  }

  // Faux newsprint fill so every card reads as a folded paper.
  const greek = document.createElement('div');
  greek.className = 'nooz-np-greek';
  paper.appendChild(greek);

  paper.addEventListener('click', () => {
    const f = group.focus;
    if (f.kind === 'topic') actions.focusTopic(f.value);
    else if (f.kind === 'source') actions.focusSource(f.value);
    else actions.focusRegion(f.value);
  });

  return paper;
}

function empty(state) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-empty-state';
  const title = document.createElement('p');
  title.className = 'nooz-empty-state-title';
  title.textContent = 'The stand is empty';
  wrap.appendChild(title);
  const text = document.createElement('p');
  text.className = 'nooz-empty-state-text';
  text.textContent = state.sources && state.sources.length
    ? 'Nothing has flowed yet -- refresh the Paper and the piles will fill in.'
    : 'Add some sources first, then the papers stack up here by category, publisher, and region.';
  wrap.appendChild(text);
  return wrap;
}
