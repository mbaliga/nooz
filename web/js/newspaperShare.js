// newspaperShare.js -- renders a "newspaper clipping" share image, matching
// the Android app's own NewspaperShare mockup: the Nooz masthead, a double
// rule, the real headline (word-wrapped, bold serif), a hairline, the source
// and author stacked on their own lines, and a dated "clipped with Nooz"
// footer. No article photo -- like the Android version, this is a pure
// typographic clipping, not a photo card.
//
// Colours are read live from the page's own --paper-* custom properties, so
// the clipping matches whichever paper theme (cream/white/sepia/night) the
// reader currently has set, rather than a fixed look independent of it.
//
// Pure rendering only -- this module knows nothing about how the result gets
// shared (Web Share API, download, clipboard); that's app.js's shareItem.

const WIDTH = 1080;
const MARGIN = 84;

const MAST_SIZE = 128;
const HEADLINE_SIZE = 78;
const HEADLINE_LEADING = 92;
const BYLINE_SIZE = 30;
const BYLINE_LEADING = 44;
const FOOTER_SIZE = 28;

function themeColors() {
  const cs = getComputedStyle(document.documentElement);
  const read = (name, fallback) => (cs.getPropertyValue(name) || '').trim() || fallback;
  return {
    paper: read('--paper-field', '#F7F6F3'),
    ink: read('--paper-ink', '#141414'),
    muted: read('--paper-ink-dim', '#8A8A86'),
  };
}

function wrapText(ctx, text, maxWidth) {
  const words = (text || '').split(/\s+/).filter(Boolean);
  if (words.length === 0) return [''];
  const lines = [];
  let line = '';
  for (const word of words) {
    const test = line ? `${line} ${word}` : word;
    if (line && ctx.measureText(test).width > maxWidth) {
      lines.push(line);
      line = word;
    } else {
      line = test;
    }
  }
  if (line) lines.push(line);
  return lines;
}

function ellipsize(ctx, text, maxWidth) {
  if (ctx.measureText(text).width <= maxWidth) return text;
  let t = text;
  while (t.length > 1 && ctx.measureText(t + '…').width > maxWidth) t = t.slice(0, -1);
  return t + '…';
}

/**
 * @param {{title: string, sourceTitle?: string|null, author?: string|null}} item
 * @returns {Promise<Blob|null>} a PNG blob, or null if canvas rendering isn't available
 */
export async function renderNewspaperClipping({ title, sourceTitle, author }) {
  // Wrong metrics (and a wrong-looking clipping) if the real faces haven't
  // painted in yet -- wait for them, same as the newspaper's own pagination does.
  if (document.fonts && document.fonts.ready) {
    try { await document.fonts.ready; } catch (_err) { /* draw with whatever's loaded */ }
  }

  const colors = themeColors();
  const contentWidth = WIDTH - MARGIN * 2;

  const probe = document.createElement('canvas').getContext('2d');
  if (!probe) return null;
  probe.font = `bold ${HEADLINE_SIZE}px "PT Serif", Georgia, serif`;
  const headlineLines = wrapText(probe, title || '(untitled)', contentWidth);

  const bylineLines = [sourceTitle, author].filter(Boolean);
  if (bylineLines.length === 0) bylineLines.push('Nooz reader');

  const height =
    MARGIN +
    MAST_SIZE * 1.25 + 56 + // masthead + gap to the double rule
    3 + 11 + 1.5 + 42 + // double rule (bold, gap, hairline) + gap to headline
    headlineLines.length * HEADLINE_LEADING + 18 + // headline block + gap to the rule beneath it
    1.5 + 36 + // hairline + gap to byline
    bylineLines.length * BYLINE_LEADING + 40 + // byline block + gap to footer
    FOOTER_SIZE * 1.3 +
    MARGIN;

  const canvas = document.createElement('canvas');
  canvas.width = WIDTH;
  canvas.height = Math.ceil(height);
  const ctx = canvas.getContext('2d');
  if (!ctx) return null;

  ctx.fillStyle = colors.paper;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.textBaseline = 'top'; // y always means "top of this text's line box" -- no baseline arithmetic below

  let y = MARGIN;

  ctx.fillStyle = colors.ink;
  ctx.textAlign = 'center';
  ctx.font = `400 ${MAST_SIZE}px "PT Serif", Georgia, serif`;
  ctx.fillText('Nooz', WIDTH / 2, y);
  y += MAST_SIZE * 1.25 + 56;

  ctx.fillRect(MARGIN, y, contentWidth, 3);
  y += 3 + 11;
  ctx.fillRect(MARGIN, y, contentWidth, 1.5);
  y += 1.5 + 42;

  ctx.textAlign = 'left';
  ctx.font = `bold ${HEADLINE_SIZE}px "PT Serif", Georgia, serif`;
  for (const line of headlineLines) {
    ctx.fillText(line, MARGIN, y);
    y += HEADLINE_LEADING;
  }
  y += 18;

  ctx.fillRect(MARGIN, y, contentWidth, 1.5);
  y += 1.5 + 36;

  ctx.fillStyle = colors.muted;
  ctx.font = `500 ${BYLINE_SIZE}px "Hyle Grotesk", sans-serif`;
  ctx.letterSpacing = '2px'; // no-op where unsupported; harmless
  for (const line of bylineLines) {
    ctx.fillText(ellipsize(ctx, line.toUpperCase(), contentWidth), MARGIN, y);
    y += BYLINE_LEADING;
  }
  ctx.letterSpacing = '0px';
  y += 40;

  ctx.textAlign = 'center';
  ctx.font = `500 ${FOOTER_SIZE}px "Hyle Grotesk", sans-serif`;
  ctx.letterSpacing = '2px';
  const dateStr = new Date().toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' });
  ctx.fillText(`${dateStr}  ·  clipped with Nooz`, WIDTH / 2, y);
  ctx.letterSpacing = '0px';

  return await new Promise((resolve) => canvas.toBlob(resolve, 'image/png'));
}
