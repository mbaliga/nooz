// stand.js -- the "Paper": Nooz's front page. A masthead across the top, a slim
// toolbar (search / region / refresh), and the day's stories. Two reading
// modes (Settings): Continuous sets everything in scrolling newspaper columns;
// Newspaper pages it like a real paper you turn -- a lead page, then pages you
// flip through, two-up as a spread when the screen is wide enough.
//
// Source management no longer lives here: it's the right-hand Sources drawer
// now (opened from the footer), so the Paper gets the full width for columns.
//
// All feed text is set via .textContent; images go through images.js (halftone
// by default, switchable from the chip on the lead photo); the extracted/feed
// image URLs were already restricted to https by feeds.js.

import { classifyItem, TOPIC_LABEL } from '../topics.js';
import { frameImage } from '../images.js';
import { buildLoomStrip } from './loom.js';
import { sanitizeHtml } from '../sanitize.js';

// Which spread of the Newspaper mode is open; kept across background re-renders.
// Spread 0 is the front page (shown on its own); spread 1 is pages 2-3, etc.
let newspaperSpread = 0;
// Direction of the last turn (-1/0/+1) so the freshly-rendered spread can play
// its turn-in animation, then it's reset.
let lastTurnDir = 0;
// The current pagination: complete articles measured into full-size pages.
// Cached by a signature of the content + layout so turning a page doesn't
// re-paginate; recomputed when the stories, the settings, or the size change.
// { key, pages: [{ articleIdxs, isFront, pageNum, colH }], articles, dims }
let plan = null;

export function render(container, state, actions) {
  container.replaceChildren();

  const paper = document.createElement('div');
  paper.className = 'nooz-paper';
  const mode = state.settings && state.settings.readingMode === 'newspaper' ? 'newspaper' : 'continuous';
  if (mode === 'newspaper') paper.classList.add('nooz-paper--news');

  if (state.sources.length === 0) {
    paper.appendChild(buildMasthead(state));
    paper.appendChild(buildNoSourcesEmptyState(actions));
    container.appendChild(paper);
    return;
  }

  if (state.items.length === 0) {
    paper.appendChild(buildMasthead(state));
    paper.appendChild(
      state.searchQuery ? buildNoResultsEmptyState(state) : buildNothingFlowedEmptyState(actions)
    );
    container.appendChild(paper);
    return;
  }

  // No toolbar on the paper anymore -- search is a footer icon, and there's
  // nothing between you and the news but the page itself ("just read"). The one
  // thing that can appear is a removable chip when the newsstand has focused the
  // Paper on a category/publisher/region.
  const focus = buildFocusBar(state, actions);

  if (mode === 'newspaper') {
    // The big Nooz nameplate lives on the front page itself; inner pages carry
    // a plain running head -- standard newspaper format.
    if (focus) paper.appendChild(focus);
    paper.appendChild(buildLoomStrip(state, actions));
    paper.appendChild(buildNewspaper(state, actions));
  } else {
    paper.appendChild(buildMasthead(state));
    if (focus) paper.appendChild(focus);
    paper.appendChild(buildLoomStrip(state, actions));
    paper.appendChild(buildFrontPage(state, actions));
  }

  container.appendChild(paper);
}

// ---------------------------------------------------------------------------
// Masthead + toolbar
// ---------------------------------------------------------------------------

function buildMasthead(state) {
  const masthead = document.createElement('header');
  masthead.className = 'nooz-masthead';

  const topRule = document.createElement('div');
  topRule.className = 'nooz-masthead-rule';
  masthead.appendChild(topRule);

  const wordmark = document.createElement('h1');
  wordmark.className = 'nooz-masthead-wordmark';
  wordmark.textContent = 'Nooz';
  masthead.appendChild(wordmark);

  const edition = document.createElement('p');
  edition.className = 'nooz-masthead-edition';
  const enabled = state.sources.filter((s) => s.enabled).length;
  edition.textContent = [fullToday(), `${enabled} ${enabled === 1 ? 'source' : 'sources'}`, state.regionFilter || 'All regions'].join('  ·  ');
  masthead.appendChild(edition);

  const botRule = document.createElement('div');
  botRule.className = 'nooz-masthead-rule nooz-masthead-rule--double';
  masthead.appendChild(botRule);

  return masthead;
}

// The only thing that sits above the paper now: a removable focus chip, shown
// just when the newsstand has narrowed the Paper to a slice.
function buildFocusBar(state, actions) {
  const chip = buildFocusChip(state, actions);
  if (!chip) return null;
  const bar = document.createElement('div');
  bar.className = 'nooz-focus-bar';
  bar.appendChild(chip);
  return bar;
}

// When the newsstand focuses the Paper on a category or publisher, show a
// removable chip so it's obvious what's being filtered and easy to clear.
function buildFocusChip(state, actions) {
  let label = null;
  if (state.topicFilter) label = `Category: ${TOPIC_LABEL[state.topicFilter] || state.topicFilter}`;
  else if (state.sourceFilter) {
    const src = (state.sources || []).find((s) => s.id === state.sourceFilter);
    label = `Publisher: ${src ? src.title : 'source'}`;
  }
  if (!label) return null;

  const chip = document.createElement('button');
  chip.type = 'button';
  chip.className = 'nooz-focus-chip';
  chip.setAttribute('aria-label', `Clear ${label}`);
  const text = document.createElement('span');
  text.textContent = label;
  chip.appendChild(text);
  const x = document.createElement('span');
  x.className = 'nooz-focus-x';
  x.textContent = '×';
  chip.appendChild(x);
  chip.addEventListener('click', () => actions.clearFocus());
  return chip;
}

// ---------------------------------------------------------------------------
// Continuous front page (scrolling columns)
// ---------------------------------------------------------------------------

function buildFrontPage(state, actions) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-frontpage';

  const items = state.items;
  const showImages = !state.settings || state.settings.showImages;
  const imageStyle = (state.settings && state.settings.imageStyle) || 'halftone';

  let leadIndex = 0;
  if (showImages) {
    const withImg = items.findIndex((it) => it.image);
    if (withImg >= 0 && withImg < 6) leadIndex = withImg;
  }
  wrap.appendChild(buildLeadStory(items[leadIndex], state, actions, showImages, imageStyle));

  const rest = items.filter((_, i) => i !== leadIndex);
  if (rest.length) {
    const columns = document.createElement('div');
    columns.className = 'nooz-columns';
    for (const item of rest) columns.appendChild(buildColumnStory(item, state, actions, showImages));
    wrap.appendChild(columns);
  }
  return wrap;
}

// ---------------------------------------------------------------------------
// Newspaper mode -- a real paper you turn.
//
// The front page carries the big Nooz nameplate; every other page a plain
// running head (nameplate, date, page number) -- standard newspaper format.
// Complete articles are set into full-size portrait pages by a small measuring
// pass (paginateUnits): headlines and paragraphs flow unit by unit so pages
// fill completely and a long article simply continues onto the next page (a
// headline is never orphaned at a page break). When the window has room for two
// full pages side by side you get a spread (front alone, then 2-3, 4-5, ..., a
// last single page if the count is odd); when it doesn't, you turn one full page
// at a time. Nothing is scaled -- pages are always their true size.
// ---------------------------------------------------------------------------

function computeDims() {
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  const pageW = Math.max(280, Math.min(Math.round(vw * 0.94), 680));
  // A spread's two pages join at the spine (no gutter), so two of them need
  // just their own width plus a little breathing room.
  const twoUp = vw >= pageW * 2 + 24;
  const cols = pageW >= 620 ? 4 : pageW >= 440 ? 3 : pageW >= 320 ? 2 : 1;
  const maxH = Math.max(460, vh - 168); // room for the footer + the pager
  const pageH = Math.min(Math.round(pageW * 1.3), maxH);
  return { vw, vh, pageW, pageH, cols, twoUp };
}

function planKey(state, dims) {
  const items = state.items;
  const s = state.settings || {};
  const first = items[0] ? items[0].id : '-';
  const last = items[items.length - 1] ? items[items.length - 1].id : '-';
  return [items.length, first, last, s.showImages !== false, s.imageStyle || 'halftone', s.font || 'serif', s.paper || 'cream', dims.pageW, dims.pageH, dims.cols].join('|');
}

function buildNewspaper(state, actions) {
  const dims = computeDims();
  const key = planKey(state, dims);
  const wrap = document.createElement('div');
  wrap.className = 'nooz-newspaper';

  if (!plan || plan.key !== key) {
    const compute = () => {
      const units = buildUnits(state, actions);
      const pages = paginateUnits(units, state, dims);
      plan = { key, pages, units, dims };
    };
    // Measuring needs the real fonts, or pages over/under-fill. If they aren't
    // ready yet, show a brief note and paginate once they are; normally they're
    // already loaded and we compute inline with no flash.
    if (document.fonts && document.fonts.status !== 'loaded') {
      wrap.appendChild(newspaperLoading());
      document.fonts.ready.then(() => { if (!plan || plan.key !== key) { compute(); actions.refreshView(); } });
      return wrap;
    }
    compute();
  }

  const dims2 = plan.dims;
  const pages = plan.pages;
  const spreads = spreadList(pages.length, dims2.twoUp);
  const maxSpread = spreads.length - 1;
  if (newspaperSpread > maxSpread) newspaperSpread = maxSpread;
  if (newspaperSpread < 0) newspaperSpread = 0;
  const shown = spreads[newspaperSpread];

  const book = document.createElement('div');
  book.className = 'nooz-book' + (shown.length > 1 ? ' is-spread' : '');
  for (const pIdx of shown) book.appendChild(buildVisiblePage(state, actions, pages[pIdx], dims2));
  if (lastTurnDir > 0) book.classList.add('is-turn-fwd');
  else if (lastTurnDir < 0) book.classList.add('is-turn-back');
  lastTurnDir = 0;

  const frame = document.createElement('div');
  frame.className = 'nooz-np-frame';
  frame.appendChild(book);

  const doTurn = (dir) => {
    const target = Math.min(maxSpread, Math.max(0, newspaperSpread + dir));
    if (target === newspaperSpread) return;
    lastTurnDir = dir;
    newspaperSpread = target;
    actions.refreshView();
  };
  attachMarginTurn(frame, book, doTurn);

  wrap.appendChild(frame);
  wrap.appendChild(buildPager(newspaperSpread, maxSpread, shown, pages.length, doTurn));
  return wrap;
}

// Clicking the blank margin either side of the book turns the page that way --
// the default way to move through Newspaper mode (its pages are the web's
// equivalent of the app's immersive, chrome-free reading surface). Clicking
// and holding repeats the turn, accelerating like a fast-forward/rewind, until
// released. A plain click (target === frame, i.e. the flex container's own
// background, not the book or anything in it -- headlines stay clickable)
// turns once; holding past ~480ms takes over and the trailing click is
// suppressed so a hold doesn't also count as one more single turn.
function attachMarginTurn(frame, book, doTurn) {
  let holdTimer = null;
  let heldFired = false;

  const isBlank = (evt) => evt.target === frame;
  const sideOf = (evt) => {
    const r = book.getBoundingClientRect();
    const mid = r.width > 0 ? r.left + r.width / 2 : frame.getBoundingClientRect().left + frame.getBoundingClientRect().width / 2;
    return evt.clientX < mid ? -1 : 1;
  };
  const clearHold = () => { clearTimeout(holdTimer); holdTimer = null; };

  frame.addEventListener('click', (evt) => {
    if (!isBlank(evt)) return;
    if (heldFired) { heldFired = false; return; }
    doTurn(sideOf(evt));
  });

  frame.addEventListener('pointerdown', (evt) => {
    if (!isBlank(evt)) return;
    if (evt.button !== undefined && evt.button !== 0) return;
    const dir = sideOf(evt);
    clearHold();
    heldFired = false;
    let speed = 420; // ms between repeats; shrinks each tick, i.e. accelerates
    const tick = () => {
      heldFired = true;
      doTurn(dir);
      speed = Math.max(90, speed - 35);
      holdTimer = setTimeout(tick, speed);
    };
    holdTimer = setTimeout(tick, 480); // grace period so a quick tap stays a single turn
  });
  ['pointerup', 'pointerleave', 'pointercancel'].forEach((ev) => frame.addEventListener(ev, clearHold));
}

// Front alone, then two pages per spread when there's room (else one at a time),
// with a last single page when the count is odd.
function spreadList(pageCount, twoUp) {
  if (pageCount <= 0) return [[]];
  const spreads = [[0]];
  if (!twoUp) { for (let p = 1; p < pageCount; p++) spreads.push([p]); return spreads; }
  for (let p = 1; p < pageCount; p += 2) spreads.push(p + 1 < pageCount ? [p, p + 1] : [p]);
  return spreads;
}

// ---- Article DOM as a flat stream of flow units -----------------------------
//
// Each article becomes: a head unit (kicker + headline + byline + lead image,
// kept whole), one unit per body block, and an end rule. The measuring pass
// then fills each page's columns unit by unit, so pages fill completely and a
// long article simply continues onto the next page.

function buildUnits(state, actions) {
  const items = state.items;
  const showImages = !state.settings || state.settings.showImages;
  // Editorial order: lead the front with the first pictured story, then the
  // rest in the order they flowed.
  let leadIndex = 0;
  if (showImages) {
    const withImg = items.findIndex((it) => it.image);
    if (withImg >= 0 && withImg < 6) leadIndex = withImg;
  }
  const ordered = leadIndex === 0 ? items.slice() : [items[leadIndex], ...items.filter((_, i) => i !== leadIndex)];
  const units = [];
  ordered.forEach((item, ai) => {
    const els = buildArticleEls(item, state, actions, showImages, ai === 0);
    els.forEach((el, k) => {
      const kind = k === 0 ? 'head' : k === els.length - 1 ? 'end' : 'body';
      units.push({ el, articleIdx: ai, kind });
    });
  });
  return units;
}

function buildArticleEls(item, state, actions, showImages, isLead) {
  const els = [];
  const head = document.createElement('div');
  head.className = 'nooz-art-head' + (isLead ? ' nooz-art-head--lead' : '');
  head.setAttribute('role', 'button');
  head.setAttribute('tabindex', '0');
  if (state.readIds && state.readIds.has(item.id)) head.classList.add('is-read');
  head.appendChild(kicker(item));
  const h = document.createElement(isLead ? 'h2' : 'h3');
  h.className = 'nooz-art-headline' + (isLead ? ' nooz-art-headline--lead' : '');
  h.textContent = item.title || '(untitled)';
  head.appendChild(h);
  if (showImages && item.image) {
    const fig = isLead
      ? frameImage(item.image, {
          className: 'nooz-art-photo',
          prominent: true,
          currentStyle: (state.settings && state.settings.imageStyle) || 'halftone',
          onStyle: (s) => actions.updateSetting('imageStyle', s),
        })
      : frameImage(item.image, { className: 'nooz-art-photo nooz-art-photo--col' });
    if (fig) head.appendChild(fig);
  }
  head.appendChild(byline(item, state));
  wire(head, () => actions.openItem(item.id));
  els.push(head);

  const body = buildBodyBlocks(item, state);
  // A drop cap opens the lead story, the way a front page does.
  if (isLead && body[0] && body[0].tagName === 'P') body[0].classList.add('nooz-art-b--drop');
  for (const block of body) els.push(block);

  const end = document.createElement('div');
  end.className = 'nooz-art-end';
  els.push(end);
  return els;
}

// The fullest text we have, as a list of block-level elements: the extracted
// article, else the feed's own body, else the summary, else an honest note.
function buildBodyBlocks(item, state) {
  const extracted = state.articles && state.articles[item.id];
  let frag = null;
  if (extracted && extracted.html) frag = sanitizeHtml(extracted.html, { allowImages: false });
  else if (item.contentHtml) frag = sanitizeHtml(item.contentHtml, { allowImages: false });

  const blocks = [];
  if (frag) {
    for (const node of Array.from(frag.childNodes)) {
      if (node.nodeType === 1) {
        if (node.classList) node.classList.add('nooz-art-b');
        blocks.push(node);
      } else if (node.nodeType === 3 && node.textContent.trim()) {
        const p = document.createElement('p');
        p.className = 'nooz-art-b';
        p.textContent = node.textContent.trim();
        blocks.push(p);
      }
    }
  }
  if (blocks.length === 0 && item.summary) {
    for (const para of splitParagraphsLocal(item.summary)) {
      const p = document.createElement('p');
      p.className = 'nooz-art-b';
      p.textContent = para;
      blocks.push(p);
    }
  }
  if (blocks.length === 0) {
    const p = document.createElement('p');
    p.className = 'nooz-art-b nooz-art-b--thin';
    p.textContent = 'The full text is not in this feed. Open the story to read it at the source.';
    blocks.push(p);
  }
  return blocks;
}

function splitParagraphsLocal(text) {
  const parts = (text || '').split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean);
  return parts.length ? parts : [(text || '').trim()].filter(Boolean);
}

// ---- Page shells + the measuring pass ---------------------------------------

function buildPageShell(state, isFront, pageNum, dims) {
  const sheet = document.createElement('section');
  sheet.className = 'nooz-sheet' + (isFront ? ' nooz-sheet--front' : '');
  sheet.style.width = dims.pageW + 'px';
  sheet.style.height = dims.pageH + 'px';

  const header = isFront ? buildFrontNameplate(state) : buildRunningHead(state, pageNum);
  header.classList.add('nooz-sheet-head');
  sheet.appendChild(header);

  const box = document.createElement('div');
  box.className = 'nooz-colbox';
  box.style.columnCount = String(dims.cols);
  sheet.appendChild(box);
  return { sheet, box };
}

// The front-page nameplate: an edition line, the wordmark, a dateline, and a
// skyline of teasers -- a proper front page.
function buildFrontNameplate(state) {
  const wrap = document.createElement('header');
  wrap.className = 'nooz-nameplate';

  const top = document.createElement('div');
  top.className = 'nooz-np-topline';
  const ed = document.createElement('span');
  ed.className = 'nooz-np-edition';
  ed.textContent = 'The Loom Edition';
  const tag = document.createElement('span');
  tag.className = 'nooz-np-tagline';
  tag.textContent = 'Woven from your own sources';
  top.appendChild(ed);
  top.appendChild(tag);
  wrap.appendChild(top);

  const word = document.createElement('h1');
  word.className = 'nooz-nameplate-word';
  word.textContent = 'Nooz';
  wrap.appendChild(word);

  const dateline = document.createElement('div');
  dateline.className = 'nooz-np-dateline';
  const enabled = state.sources.filter((s) => s.enabled).length;
  const d1 = document.createElement('span');
  d1.textContent = fullToday();
  const d2 = document.createElement('span');
  d2.className = 'nooz-np-dateline-mid';
  d2.textContent = 'Your Source, Your News';
  const d3 = document.createElement('span');
  d3.textContent = `${enabled} ${enabled === 1 ? 'source' : 'sources'}`;
  dateline.appendChild(d1);
  dateline.appendChild(d2);
  dateline.appendChild(d3);
  wrap.appendChild(dateline);

  const picks = (state.items || []).slice(0, 4);
  if (picks.length) {
    const teasers = document.createElement('div');
    teasers.className = 'nooz-np-teasers';
    for (const it of picks) {
      const t = document.createElement('div');
      t.className = 'nooz-np-teaser';
      const k = document.createElement('span');
      k.className = 'nooz-np-teaser-kicker';
      k.textContent = TOPIC_LABEL[classifyItem(it)] || 'News';
      const ti = document.createElement('span');
      ti.className = 'nooz-np-teaser-title';
      ti.textContent = clampText(it.title || '', 46);
      t.appendChild(k);
      t.appendChild(ti);
      teasers.appendChild(t);
    }
    wrap.appendChild(teasers);
  }

  return wrap;
}

// Inner pages: a plain running head instead of the big nameplate.
function buildRunningHead(state, pageNum) {
  const head = document.createElement('div');
  head.className = 'nooz-runhead';
  const left = document.createElement('span');
  left.className = 'nooz-runhead-name';
  left.textContent = 'Nooz';
  const mid = document.createElement('span');
  mid.className = 'nooz-runhead-date';
  mid.textContent = fullToday();
  const right = document.createElement('span');
  right.className = 'nooz-runhead-folio';
  right.textContent = `Page ${pageNum}`;
  head.appendChild(left);
  head.appendChild(mid);
  head.appendChild(right);
  return head;
}

function paginateUnits(units, state, dims) {
  const host = document.createElement('div');
  host.style.cssText = `position:absolute; left:-99999px; top:0; visibility:hidden; width:${dims.pageW}px;`;
  document.body.appendChild(host);

  const pages = [];
  let cur = null;
  let curBox = null;
  const start = (isFront, pageNum) => {
    const { sheet, box } = buildPageShell(state, isFront, pageNum, dims);
    host.replaceChildren(sheet);
    const colH = Math.max(120, dims.pageH - box.offsetTop - 22);
    box.style.height = colH + 'px';
    cur = { unitIdxs: [], isFront, pageNum, colH };
    curBox = box;
    pages.push(cur);
  };
  const overflow = () => curBox.scrollWidth > curBox.clientWidth + 2;

  let pageNum = 1;
  start(true, pageNum);
  for (let i = 0; i < units.length; i++) {
    curBox.appendChild(units[i].el);
    if (!overflow()) {
      cur.unitIdxs.push(i);
      continue;
    }
    if (cur.unitIdxs.length === 0) {
      // A single unit too tall for an empty page (a giant image, say): keep it
      // and move on rather than loop forever.
      cur.unitIdxs.push(i);
      start(false, ++pageNum);
      continue;
    }
    curBox.removeChild(units[i].el);

    // Don't orphan a headline at a page break: if the only thing of this
    // article on the page is its head, carry the head over with the body.
    const carried = [];
    if (units[i].kind === 'body') {
      const artIdx = units[i].articleIdx;
      let onlyHead = true;
      const tail = [];
      for (let j = cur.unitIdxs.length - 1; j >= 0 && units[cur.unitIdxs[j]].articleIdx === artIdx; j--) {
        tail.push(cur.unitIdxs[j]);
        if (units[cur.unitIdxs[j]].kind !== 'head') { onlyHead = false; break; }
      }
      if (onlyHead && tail.length) {
        for (const idx of tail) {
          cur.unitIdxs.pop();
          if (units[idx].el.parentNode === curBox) curBox.removeChild(units[idx].el);
          carried.unshift(idx);
        }
      }
    }

    start(false, ++pageNum);
    for (const idx of carried) { curBox.appendChild(units[idx].el); cur.unitIdxs.push(idx); }
    curBox.appendChild(units[i].el);
    cur.unitIdxs.push(i);
    if (overflow()) start(false, ++pageNum); // still overflows a fresh page: accept, move on
  }
  while (pages.length && pages[pages.length - 1].unitIdxs.length === 0) pages.pop();

  // Detach every unit so the visible book can re-home them.
  for (const u of units) if (u.el.parentNode) u.el.parentNode.removeChild(u.el);
  host.remove();
  return pages.length ? pages : [{ unitIdxs: [], isFront: true, pageNum: 1, colH: dims.pageH }];
}

function buildVisiblePage(state, actions, page, dims) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-page';
  const { sheet, box } = buildPageShell(state, page.isFront, page.pageNum, dims);
  box.style.height = page.colH + 'px';
  for (const idx of page.unitIdxs) box.appendChild(plan.units[idx].el);
  wrap.appendChild(sheet);
  return wrap;
}

function newspaperLoading() {
  const p = document.createElement('p');
  p.className = 'nooz-np-loading';
  p.textContent = 'Setting the paper…';
  return p;
}

function buildPager(spread, maxSpread, shown, pageCount, doTurn) {
  const pager = document.createElement('div');
  pager.className = 'nooz-pager';

  const prev = document.createElement('button');
  prev.type = 'button';
  prev.className = 'nooz-button nooz-pager-btn';
  prev.textContent = '‹ Turn back';
  prev.disabled = spread <= 0;
  prev.addEventListener('click', () => doTurn(-1));
  pager.appendChild(prev);

  const label = document.createElement('span');
  label.className = 'nooz-pager-label';
  label.textContent = pagerLabel(spread, shown, pageCount);
  pager.appendChild(label);

  const next = document.createElement('button');
  next.type = 'button';
  next.className = 'nooz-button nooz-pager-btn';
  next.textContent = 'Turn page ›';
  next.disabled = spread >= maxSpread;
  next.addEventListener('click', () => doTurn(1));
  pager.appendChild(next);

  return pager;
}

function pagerLabel(spread, shown, pageCount) {
  if (spread === 0) return `The front page  ·  ${pageCount} ${pageCount === 1 ? 'page' : 'pages'}`;
  if (shown.length > 1) return `Pages ${shown[0] + 1}–${shown[1] + 1} of ${pageCount}`;
  return `Page ${shown[0] + 1} of ${pageCount}`;
}

// ---------------------------------------------------------------------------
// Stories
// ---------------------------------------------------------------------------

function buildLeadStory(item, state, actions, showImages, imageStyle, continued) {
  const story = document.createElement('article');
  story.className = 'nooz-lead';
  story.setAttribute('role', 'button');
  story.setAttribute('tabindex', '0');
  if (state.readIds.has(item.id)) story.classList.add('is-read');

  if (showImages && item.image) {
    const figure = frameImage(item.image, {
      prominent: true,
      className: 'nooz-lead-figure',
      currentStyle: imageStyle,
      onStyle: (s) => actions.updateSetting('imageStyle', s),
    });
    if (figure) story.appendChild(figure);
  }

  const body = document.createElement('div');
  body.className = 'nooz-lead-body';
  body.appendChild(kicker(item));

  const headline = document.createElement('h2');
  headline.className = 'nooz-lead-headline';
  headline.textContent = item.title || '(untitled)';
  body.appendChild(headline);

  if (item.summary) {
    const dek = document.createElement('p');
    dek.className = 'nooz-lead-dek';
    dek.textContent = clampText(item.summary, continued ? 220 : 320);
    body.appendChild(dek);
  }

  body.appendChild(byline(item, state));

  const cont = document.createElement('span');
  cont.className = 'nooz-continue';
  cont.textContent = 'Continue reading →';
  body.appendChild(cont);

  story.appendChild(body);
  wire(story, () => actions.openItem(item.id));
  return story;
}

function buildColumnStory(item, state, actions, showImages) {
  const story = document.createElement('article');
  story.className = 'nooz-col-story';
  story.setAttribute('role', 'button');
  story.setAttribute('tabindex', '0');
  if (state.readIds.has(item.id)) story.classList.add('is-read');

  story.appendChild(kicker(item));

  const headline = document.createElement('h3');
  headline.className = 'nooz-col-headline';
  headline.textContent = item.title || '(untitled)';
  story.appendChild(headline);

  if (showImages && item.image) {
    const figure = frameImage(item.image, { className: 'nooz-col-photo' });
    if (figure) story.appendChild(figure);
  }

  if (item.summary) {
    const dek = document.createElement('p');
    dek.className = 'nooz-col-dek';
    dek.textContent = clampText(item.summary, 160);
    story.appendChild(dek);
  }

  story.appendChild(byline(item, state));
  wire(story, () => actions.openItem(item.id));
  return story;
}

function kicker(item) {
  const k = document.createElement('p');
  k.className = 'nooz-kicker';
  k.textContent = TOPIC_LABEL[classifyItem(item)] || 'General';
  return k;
}

function byline(item, state) {
  const line = document.createElement('p');
  line.className = 'nooz-byline';
  const source = (state.sources || []).find((s) => s.id === item.sourceId);
  const src = document.createElement('span');
  src.textContent = source ? source.title : 'Unknown source';
  line.appendChild(src);
  const when = document.createElement('span');
  when.className = 'nooz-byline-dot';
  when.textContent = formatShortDate(item.publishedAt);
  line.appendChild(when);
  return line;
}

function wire(el, activate) {
  el.addEventListener('click', activate);
  el.addEventListener('keydown', (event) => {
    if (event.target !== el) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      activate();
    }
  });
}

function clampText(text, max) {
  const t = (text || '').trim();
  if (t.length <= max) return t;
  return t.slice(0, max).replace(/\s+\S*$/, '') + '…';
}

// ---------------------------------------------------------------------------
// Notices + empty states
// ---------------------------------------------------------------------------

function buildNoSourcesEmptyState(actions) {
  const wrap = emptyState('Nothing on your paper yet', 'Add a feed, or pick from a short list of pre-checked starter sources -- open Sources from the footer.');
  wrap.appendChild(primaryButton('Open sources', () => actions.goTo('sources')));
  return wrap;
}

function buildNothingFlowedEmptyState(actions) {
  const wrap = emptyState('Nothing has flowed yet', 'Tap refresh to check your sources.');
  wrap.appendChild(plainButton('Refresh', () => actions.refreshAll()));
  return wrap;
}

function buildNoResultsEmptyState(state) {
  return emptyState('No results', `Nothing on your paper matches "${state.searchQuery}".`);
}

function emptyState(titleText, bodyText) {
  const wrap = document.createElement('div');
  wrap.className = 'nooz-empty-state';
  const title = document.createElement('p');
  title.className = 'nooz-empty-state-title';
  title.textContent = titleText;
  wrap.appendChild(title);
  const text = document.createElement('p');
  text.className = 'nooz-empty-state-text';
  text.textContent = bodyText;
  wrap.appendChild(text);
  return wrap;
}

function primaryButton(label, onClick) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'nooz-button nooz-button--primary';
  btn.textContent = label;
  btn.addEventListener('click', onClick);
  return btn;
}

function plainButton(label, onClick) {
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'nooz-button';
  btn.textContent = label;
  btn.addEventListener('click', onClick);
  return btn;
}

function fullToday() {
  return new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });
}

function formatShortDate(publishedAt) {
  const diffMin = Math.floor((Date.now() - publishedAt) / 60000);
  if (diffMin <= 0) return 'just now';
  if (diffMin < 60) return `${diffMin}m`;
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return `${diffHr}h`;
  const diffDay = Math.floor(diffHr / 24);
  if (diffDay < 7) return `${diffDay}d`;
  const date = new Date(publishedAt);
  const now = new Date();
  const options = { month: 'short', day: 'numeric' };
  if (date.getFullYear() !== now.getFullYear()) options.year = 'numeric';
  return date.toLocaleDateString(undefined, options);
}
