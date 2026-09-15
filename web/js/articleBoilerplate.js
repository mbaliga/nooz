// articleBoilerplate.js -- the syndication/ad plumbing a feed appends after
// the real writing stops, not writing itself: WordPress's auto-appended
// "The post X appeared first on Y." footer, a "Continue reading →" link's
// own label, a standalone "Advertisement" marker, an excerpt's truncation
// bracket, a bare tracking link with no label at all.
//
// Reported (screenshot: "links coming through or some sort of ads... the
// article itself isn't fully clean"): sanitizeHtml.js strips *dangerous*
// markup (script/iframe/on* handlers) but has no idea any of the above is
// filler rather than the writer's own last sentence, so all of it survived
// -- often as a real, clickable <a> once rendered.
//
// Every pattern is anchored to the END of the text it's checking (`$`), so a
// real sentence is never truncated mid-way -- only a whole, recognizable
// fragment with nothing real after it is ever removed.
//
// Mirrors core/model/.../Html.kt's TRAILING_BOILERPLATE list on the Android
// side (a separate implementation, since the two clients don't share a
// runtime) -- keep the two in sync.

const TRAILING_BOILERPLATE = [
  // WordPress/Jetpack's byte-for-byte auto-appended syndication footer.
  /\bthe post .+? appeared first on .+?\.$/i,
  // A "keep reading" link's own label, alone or with a trailing arrow/ellipsis --
  // never this much text unless it's the whole label, so "read more about the
  // case below" (real prose) can't match: there's no room for it before the $.
  /\b(continue reading|read more|read (the )?full (article|story)|keep reading|full story)\s*[:\-–—»›→]*\s*(\.{2,3}|…)?$/i,
  // An ad-network label with nothing else around it.
  /\b(advertisement|sponsored(\s+content)?)$/i,
  // WordPress excerpt truncation markers ("...the rest of the story [&#8230;]").
  /\[(…|\.\.\.)\]$/,
  // A bare tracking/redirect link with no surrounding label at all.
  /\bhttps?:\/\/\S+$/,
];

/**
 * Strips recognized trailing boilerplate fragments one at a time until none
 * remain -- a feed can chain more than one ("...real ending. [&#8230;]
 * Continue reading →"). If the whole string turns out to be nothing but such
 * fragments, the result is empty, which callers already treat as "no
 * summary" rather than displaying pure junk.
 */
export function stripTrailingBoilerplate(text) {
  let out = (text || '').trim();
  let changed = true;
  while (changed && out) {
    changed = false;
    for (const pattern of TRAILING_BOILERPLATE) {
      const m = out.match(pattern);
      if (!m) continue;
      const matchStart = m.index;
      if (matchStart + m[0].length !== out.length) continue; // must reach the true end
      // NOT '.': a period right before the cut is the real sentence's own
      // terminal punctuation, never a separator glued onto the boilerplate --
      // only whitespace and connector marks (dash/colon/comma) are.
      out = out.slice(0, matchStart).replace(/[\s\-–—:,]+$/, '');
      changed = true;
      break;
    }
  }
  return out;
}

/** True if [text], trimmed, is nothing but a recognized boilerplate fragment. */
export function isWholeTextBoilerplate(text) {
  const trimmed = (text || '').trim();
  return trimmed.length > 0 && stripTrailingBoilerplate(trimmed) === '';
}
