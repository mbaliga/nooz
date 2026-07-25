// selection.js -- when the reader selects a run of text, offer to look it up.
// A small popover appears just above the selection with two actions: search
// the selected text on DuckDuckGo or on Wikipedia. Both open in a new tab.
//
// Deliberately tiny and unobtrusive: it only appears for a non-trivial
// selection made with the mouse/touch inside article text, dismisses on the
// next click or scroll, and never phones anything home -- the lookup only
// happens when the reader actively taps one of the two buttons.

let popover = null;
let installed = false;

const MIN_CHARS = 2;
const MAX_CHARS = 240;

export function installSelectionSearch() {
  if (installed) return;
  installed = true;
  document.addEventListener('mouseup', onSelectionChanged);
  document.addEventListener('keyup', onSelectionChanged);
  document.addEventListener('scroll', hide, true);
  document.addEventListener('mousedown', (e) => {
    if (popover && !popover.contains(e.target)) hide();
  });
}

function onSelectionChanged() {
  // let the selection settle
  setTimeout(evaluate, 0);
}

function evaluate() {
  const sel = window.getSelection();
  if (!sel || sel.isCollapsed) {
    hide();
    return;
  }
  const text = sel.toString().trim();
  if (text.length < MIN_CHARS || text.length > MAX_CHARS) {
    hide();
    return;
  }
  // Only offer it for selections inside article-ish text, not UI chrome.
  const anchor = sel.anchorNode;
  const host = anchor && (anchor.nodeType === 1 ? anchor : anchor.parentElement);
  if (!host || !host.closest('.nooz-reader-body, .nooz-lead, .nooz-col-story, .nooz-reader')) {
    hide();
    return;
  }
  let rect;
  try {
    rect = sel.getRangeAt(0).getBoundingClientRect();
  } catch (_err) {
    hide();
    return;
  }
  if (!rect || (rect.width === 0 && rect.height === 0)) {
    hide();
    return;
  }
  show(text, rect);
}

function show(text, rect) {
  if (!popover) popover = build();
  const ddg = popover.querySelector('[data-src="ddg"]');
  const wiki = popover.querySelector('[data-src="wiki"]');
  ddg.href = `https://duckduckgo.com/?q=${encodeURIComponent(text)}`;
  wiki.href = `https://en.wikipedia.org/w/index.php?search=${encodeURIComponent(text)}`;

  document.body.appendChild(popover);
  popover.style.visibility = 'hidden';
  popover.classList.add('is-visible');
  const pw = popover.offsetWidth;
  const ph = popover.offsetHeight;
  let left = rect.left + rect.width / 2 - pw / 2 + window.scrollX;
  left = Math.max(8, Math.min(left, window.innerWidth - pw - 8));
  let top = rect.top + window.scrollY - ph - 8;
  if (top < window.scrollY + 4) top = rect.bottom + window.scrollY + 8; // flip below if no room
  popover.style.left = `${Math.round(left)}px`;
  popover.style.top = `${Math.round(top)}px`;
  popover.style.visibility = 'visible';
}

function hide() {
  if (popover && popover.parentNode) popover.parentNode.removeChild(popover);
  if (popover) popover.classList.remove('is-visible');
}

function build() {
  const el = document.createElement('div');
  el.className = 'nooz-selpop';
  el.setAttribute('role', 'menu');

  el.appendChild(link('ddg', 'DuckDuckGo'));
  const sep = document.createElement('span');
  sep.className = 'nooz-selpop-sep';
  el.appendChild(sep);
  el.appendChild(link('wiki', 'Wikipedia'));
  return el;
}

function link(src, label) {
  const a = document.createElement('a');
  a.className = 'nooz-selpop-btn';
  a.dataset.src = src;
  a.target = '_blank';
  a.rel = 'noopener noreferrer';
  a.textContent = label;
  a.setAttribute('role', 'menuitem');
  return a;
}
