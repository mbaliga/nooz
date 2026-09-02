// images.js -- how every picture in the reader is rendered. Images default to
// a halftone (newsprint) treatment; the most prominent image on a page carries
// a small icon-only chip to switch the whole reader between Halftone / B&W /
// Colour. The actual look is CSS driven by :root[data-imgstyle] (see
// style.css) so a switch re-skins every image at once without re-rendering;
// this module just builds the frame and, for the lead image, the chip.

const SVG_NS = 'http://www.w3.org/2000/svg';

// Classes that already carry their own fixed aspect-ratio in style.css (a
// deliberate crop, not a "whatever this image's real shape is" placeholder).
// frameImage sets an inline aspect-ratio once a src's real dimensions are
// known so layout doesn't collapse mid-decode -- an inline style would
// outrank and override one of these, so they're excluded from that.
const FIXED_ASPECT_CLASSES = ['nooz-art-photo--col'];

// Every <img> this module has ever built, keyed by src, so a rebuilt frame
// for a src already on the page can reuse the exact node instead of a fresh
// one. Moving an already-decoded <img> into a new parent is a plain DOM
// move; creating a brand new <img> element for a src the browser already has
// bytes for still re-decodes on WebKit even though Chrome keeps the decoded
// bitmap hot across it -- this is what turns "the article view rebuilt itself
// in the background" into a visible flash on an iPad and nothing at all on a
// desktop browser. Capped and FIFO-evicted so a long reading session doesn't
// hold onto every image it's ever shown forever.
const IMAGE_CACHE_CAP = 40;
const imageCache = new Map(); // src -> { img, w, h }

function cacheSet(src, entry) {
  imageCache.set(src, entry);
  while (imageCache.size > IMAGE_CACHE_CAP) {
    imageCache.delete(imageCache.keys().next().value);
  }
}

/**
 * Wrap an image URL in a .nooz-img frame (relative-positioned so the halftone
 * dot overlay and, optionally, the style chip can sit over it). Returns null
 * if there's no usable src, so callers can skip cleanly.
 *
 * @param {string} src
 * @param {{prominent?: boolean, currentStyle?: string, onStyle?: (s:string)=>void, className?: string}} [opts]
 */
export function frameImage(src, opts = {}) {
  if (!src) return null;
  const frame = document.createElement('figure');
  frame.className = 'nooz-img' + (opts.className ? ' ' + opts.className : '');

  let entry = imageCache.get(src);
  let img;
  let isFreshNode = false;

  if (!entry) {
    img = document.createElement('img');
    img.alt = '';
    img.src = src;
    entry = { img, w: 0, h: 0 };
    cacheSet(src, entry);
    isFreshNode = true;
  } else if (entry.img.isConnected) {
    // Same src wanted twice on screen at once (a stale frame mid-transition,
    // say) -- clone rather than steal the node still in use elsewhere.
    // cloneNode(false) doesn't carry listeners, so this copy needs its own.
    img = entry.img.cloneNode(false);
    entry.img = img;
    isFreshNode = true;
  } else {
    // Already decoded and not on screen anywhere right now: adopt the exact
    // node. This is the reuse that actually kills the flicker.
    img = entry.img;
  }

  if (isFreshNode) {
    img.addEventListener('load', () => {
      entry.w = img.naturalWidth;
      entry.h = img.naturalHeight;
      const f = img.closest('.nooz-img');
      if (f && entry.w && entry.h && !hasFixedAspect(f)) {
        f.style.aspectRatio = entry.w + ' / ' + entry.h;
      }
    });
    // A broken image collapses the whole frame rather than leaving a torn
    // box, and drops out of the cache so a later retry starts clean.
    img.addEventListener('error', () => {
      imageCache.delete(src);
      const f = img.closest('.nooz-img');
      if (f) f.remove();
    });
  }

  img.loading = opts.prominent ? 'eager' : 'lazy';
  if (opts.prominent) img.decoding = 'async';

  frame.appendChild(img);
  // Dimensions already known from a previous load: reserve the space now so
  // this frame never collapses to zero height while the (reused or fresh)
  // node decodes.
  if (entry.w && entry.h && !hasFixedAspect(frame)) {
    frame.style.aspectRatio = entry.w + ' / ' + entry.h;
  }

  if (opts.prominent) {
    frame.appendChild(buildStyleChip(opts.currentStyle || 'halftone', opts.onStyle));
  }
  return frame;
}

function hasFixedAspect(frame) {
  return FIXED_ASPECT_CLASSES.some((c) => frame.classList.contains(c));
}

/** Rewrap any <img> already inside a sanitized body so it gets the same skin. */
export function frameBodyImages(root) {
  if (!root) return;
  for (const img of Array.from(root.querySelectorAll('img'))) {
    if (img.closest('.nooz-img')) continue;
    const frame = document.createElement('figure');
    frame.className = 'nooz-img nooz-img--inline';
    img.replaceWith(frame);
    frame.appendChild(img);
    img.addEventListener('error', () => frame.remove());
  }
}

// ---- the Halftone / B&W / Colour chip ----------------------------------

const STYLES = [
  { key: 'halftone', label: 'Halftone' },
  { key: 'bw', label: 'Black & white' },
  { key: 'colour', label: 'Colour' },
];

function buildStyleChip(current, onStyle) {
  const chip = document.createElement('div');
  chip.className = 'nooz-img-chip';
  chip.setAttribute('role', 'group');
  chip.setAttribute('aria-label', 'Image style');
  // The chip sits over the photo; keep clicks off the underlying story link.
  chip.addEventListener('click', (e) => e.stopPropagation());

  for (const s of STYLES) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'nooz-img-chip-btn';
    if (s.key === current) btn.classList.add('is-active');
    btn.setAttribute('aria-label', s.label);
    btn.setAttribute('aria-pressed', s.key === current ? 'true' : 'false');
    btn.title = s.label;
    btn.appendChild(icon(s.key));
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (onStyle) onStyle(s.key);
    });
    chip.appendChild(btn);
  }
  return chip;
}

function icon(key) {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 16 16');
  svg.setAttribute('class', 'nooz-icon');
  svg.setAttribute('aria-hidden', 'true');

  if (key === 'halftone') {
    // a little dot grid
    for (const [cx, cy, r] of [[4, 4, 1.6], [8, 4, 1.1], [12, 4, 1.6], [4, 8, 1.1], [8, 8, 2], [12, 8, 1.1], [4, 12, 1.6], [8, 12, 1.1], [12, 12, 1.6]]) {
      const c = document.createElementNS(SVG_NS, 'circle');
      c.setAttribute('cx', cx); c.setAttribute('cy', cy); c.setAttribute('r', r);
      c.setAttribute('fill', 'currentColor');
      svg.appendChild(c);
    }
  } else if (key === 'bw') {
    // a half-filled circle
    const ring = document.createElementNS(SVG_NS, 'circle');
    ring.setAttribute('cx', '8'); ring.setAttribute('cy', '8'); ring.setAttribute('r', '5.5');
    ring.setAttribute('fill', 'none'); ring.setAttribute('stroke', 'currentColor'); ring.setAttribute('stroke-width', '1.4');
    svg.appendChild(ring);
    const half = document.createElementNS(SVG_NS, 'path');
    half.setAttribute('d', 'M8 2.5a5.5 5.5 0 0 1 0 11z');
    half.setAttribute('fill', 'currentColor');
    svg.appendChild(half);
  } else {
    // colour: filled disc
    const c = document.createElementNS(SVG_NS, 'circle');
    c.setAttribute('cx', '8'); c.setAttribute('cy', '8'); c.setAttribute('r', '5.5');
    c.setAttribute('fill', 'currentColor');
    svg.appendChild(c);
  }
  return svg;
}
