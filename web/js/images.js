// images.js -- how every picture in the reader is rendered. Images default to
// a halftone (newsprint) treatment; the most prominent image on a page carries
// a small icon-only chip to switch the whole reader between Halftone / B&W /
// Colour. The actual look is CSS driven by :root[data-imgstyle] (see
// style.css) so a switch re-skins every image at once without re-rendering;
// this module just builds the frame and, for the lead image, the chip.

const SVG_NS = 'http://www.w3.org/2000/svg';

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

  const img = document.createElement('img');
  img.loading = 'lazy';
  img.alt = '';
  img.src = src;
  // A broken image collapses the whole frame rather than leaving a torn box.
  img.addEventListener('error', () => frame.remove());
  frame.appendChild(img);

  if (opts.prominent) {
    frame.appendChild(buildStyleChip(opts.currentStyle || 'halftone', opts.onStyle));
  }
  return frame;
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
