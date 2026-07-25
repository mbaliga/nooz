// settings.js -- reader preferences, kept in localStorage (not IndexedDB:
// these are tiny, synchronous, and per-browser, exactly what localStorage is
// for). Four knobs the reader asked for: the reading typeface, the colour of
// the paper, whether loaded language is highlighted, and whether feed images
// are shown. Everything is applied as data-* attributes on <html>, so the CSS
// does the actual work and a reload paints the chosen look before any JS runs
// its first render.

const STORAGE_KEY = 'nooz-settings-v1';

export const FONT_OPTIONS = [
  { key: 'serif', label: 'PT Serif', note: 'The house serif' },
  { key: 'transitional', label: 'Georgia', note: 'Classic newspaper serif' },
  { key: 'modern', label: 'Times', note: 'Tight, traditional' },
  { key: 'grotesque', label: 'Hyle Grotesk', note: 'Clean sans' },
];

export const PAPER_OPTIONS = [
  { key: 'cream', label: 'Cream', swatch: '#F7F6F3' },
  { key: 'white', label: 'White', swatch: '#FFFFFF' },
  { key: 'sepia', label: 'Sepia', swatch: '#F4ECD8' },
  { key: 'slate', label: 'Night', swatch: '#1C1C1A' },
];

export const IMAGE_STYLES = ['halftone', 'bw', 'colour'];
export const READING_MODES = ['continuous', 'newspaper'];

const DEFAULTS = {
  font: 'serif',
  paper: 'cream',
  highlightLoaded: true,
  showImages: true,
  imageStyle: 'halftone', // halftone (default, newsprint) | bw | colour
  readingMode: 'continuous', // continuous scroll | newspaper (paged, page-turn)
};

let current = { ...DEFAULTS };
const listeners = new Set();

export function loadSettings() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      current = {
        font: FONT_OPTIONS.some((o) => o.key === parsed.font) ? parsed.font : DEFAULTS.font,
        paper: PAPER_OPTIONS.some((o) => o.key === parsed.paper) ? parsed.paper : DEFAULTS.paper,
        highlightLoaded: typeof parsed.highlightLoaded === 'boolean' ? parsed.highlightLoaded : DEFAULTS.highlightLoaded,
        showImages: typeof parsed.showImages === 'boolean' ? parsed.showImages : DEFAULTS.showImages,
        imageStyle: IMAGE_STYLES.includes(parsed.imageStyle) ? parsed.imageStyle : DEFAULTS.imageStyle,
        readingMode: READING_MODES.includes(parsed.readingMode) ? parsed.readingMode : DEFAULTS.readingMode,
      };
    }
  } catch (_err) {
    current = { ...DEFAULTS };
  }
  applyToDocument();
  return current;
}

export function getSettings() {
  return { ...current };
}

export function setSetting(key, value) {
  if (!(key in DEFAULTS)) return;
  current = { ...current, [key]: value };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(current));
  } catch (_err) {
    // storage full or blocked (private mode) -- the setting still applies for
    // this session, it just won't persist; not worth interrupting the reader.
  }
  applyToDocument();
  for (const fn of listeners) fn(getSettings());
}

export function onSettingsChange(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

function applyToDocument() {
  const root = document.documentElement;
  root.dataset.font = current.font;
  root.dataset.paper = current.paper;
  root.dataset.loaded = current.highlightLoaded ? 'on' : 'off';
  root.dataset.images = current.showImages ? 'on' : 'off';
  root.dataset.imgstyle = current.imageStyle;
  root.dataset.reading = current.readingMode;
}
