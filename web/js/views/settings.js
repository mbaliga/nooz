// settings.js (view) -- the reader's four preferences: reading typeface, paper
// colour, whether loaded language is highlighted, and whether feed images are
// shown. Each control writes straight through actions.updateSetting, which
// persists to localStorage and re-applies the look immediately.

import { FONT_OPTIONS, PAPER_OPTIONS } from '../settings.js';

export function render(container, state, actions) {
  container.replaceChildren();

  const root = document.createElement('div');
  root.className = 'nooz-layout nooz-stack nooz-stack--lg';

  const heading = document.createElement('h1');
  heading.textContent = 'Settings';
  root.appendChild(heading);

  const s = state.settings || {};

  root.appendChild(buildReadingModeSection(s, actions));
  root.appendChild(buildArticleDisplaySection(s, actions));
  root.appendChild(buildFoundQuoteSection(s, actions));
  root.appendChild(buildImageStyleSection(s, actions));
  root.appendChild(buildFontSection(s, actions));
  root.appendChild(buildPaperSection(s, actions));
  root.appendChild(buildToggleSection(s, actions));
  root.appendChild(buildFeedbackSection());

  container.appendChild(root);
}

function buildReadingModeSection(s, actions) {
  const section = document.createElement('div');
  section.className = 'nooz-stack nooz-stack--xs';
  section.appendChild(sectionTitle('Layout'));

  const row = document.createElement('div');
  row.className = 'nooz-choice-row';
  const modes = [
    { key: 'continuous', label: 'Continuous', desc: 'One scrolling paper' },
    { key: 'newspaper', label: 'Newspaper', desc: 'Turn the pages' },
  ];
  const current = s.readingMode || 'continuous';
  for (const m of modes) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'nooz-choice nooz-choice--wide';
    if (current === m.key) btn.classList.add('is-active');
    btn.setAttribute('aria-pressed', current === m.key ? 'true' : 'false');
    const label = document.createElement('span');
    label.className = 'nooz-choice-label nooz-choice-label--strong';
    label.textContent = m.label;
    btn.appendChild(label);
    const desc = document.createElement('span');
    desc.className = 'nooz-choice-label';
    desc.textContent = m.desc;
    btn.appendChild(desc);
    btn.addEventListener('click', () => actions.updateSetting('readingMode', m.key));
    row.appendChild(btn);
  }
  section.appendChild(row);

  section.appendChild(
    toggleRow(
      'Immersive',
      'Newspaper mode, with nothing below the page -- no page count, no turn buttons. Click or hold the margin either side of the page to turn it.',
      s.immersiveNewspaper === true,
      (v) => actions.updateSetting('immersiveNewspaper', v)
    )
  );

  return section;
}

function buildArticleDisplaySection(s, actions) {
  const section = document.createElement('div');
  section.className = 'nooz-stack nooz-stack--xs';
  section.appendChild(sectionTitle('Articles'));

  const row = document.createElement('div');
  row.className = 'nooz-choice-row';
  const options = [
    { key: 'full', label: 'Full articles', desc: 'Read in place, like a newspaper' },
    { key: 'excerpt', label: 'Excerpts', desc: 'A short dek; open to read more' },
  ];
  const current = s.articleDisplay || 'full';
  for (const opt of options) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'nooz-choice nooz-choice--wide';
    if (current === opt.key) btn.classList.add('is-active');
    btn.setAttribute('aria-pressed', current === opt.key ? 'true' : 'false');
    const label = document.createElement('span');
    label.className = 'nooz-choice-label nooz-choice-label--strong';
    label.textContent = opt.label;
    btn.appendChild(label);
    const desc = document.createElement('span');
    desc.className = 'nooz-choice-label';
    desc.textContent = opt.desc;
    btn.appendChild(desc);
    btn.addEventListener('click', () => actions.updateSetting('articleDisplay', opt.key));
    row.appendChild(btn);
  }
  section.appendChild(row);
  return section;
}

function buildFoundQuoteSection(s, actions) {
  const section = document.createElement('div');
  section.className = 'nooz-stack nooz-stack--xs';
  section.appendChild(sectionTitle('While you read'));

  const note = document.createElement('p');
  note.className = 'nooz-choice-label';
  note.textContent = 'Every so often, a real line pulled from something you’ve already read.';
  section.appendChild(note);

  const row = document.createElement('div');
  row.className = 'nooz-choice-row';
  const options = [
    { key: 'quote', label: 'Found quote', desc: 'A small pull-quote break' },
    { key: 'dateline', label: 'Dateline aside', desc: 'A single wire-style line' },
  ];
  const current = s.foundQuoteStyle || 'quote';
  for (const opt of options) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'nooz-choice nooz-choice--wide';
    if (current === opt.key) btn.classList.add('is-active');
    btn.setAttribute('aria-pressed', current === opt.key ? 'true' : 'false');
    const label = document.createElement('span');
    label.className = 'nooz-choice-label nooz-choice-label--strong';
    label.textContent = opt.label;
    btn.appendChild(label);
    const desc = document.createElement('span');
    desc.className = 'nooz-choice-label';
    desc.textContent = opt.desc;
    btn.appendChild(desc);
    btn.addEventListener('click', () => actions.updateSetting('foundQuoteStyle', opt.key));
    row.appendChild(btn);
  }
  section.appendChild(row);
  return section;
}

function buildImageStyleSection(s, actions) {
  const section = document.createElement('div');
  section.className = 'nooz-stack nooz-stack--xs';
  section.appendChild(sectionTitle('Images'));

  const row = document.createElement('div');
  row.className = 'nooz-choice-row';
  const styles = [
    { key: 'halftone', label: 'Halftone' },
    { key: 'bw', label: 'Black & white' },
    { key: 'colour', label: 'Colour' },
  ];
  const current = s.imageStyle || 'halftone';
  for (const st of styles) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'nooz-choice';
    if (current === st.key) btn.classList.add('is-active');
    btn.setAttribute('aria-pressed', current === st.key ? 'true' : 'false');
    const label = document.createElement('span');
    label.className = 'nooz-choice-label';
    label.textContent = st.label;
    btn.appendChild(label);
    btn.addEventListener('click', () => actions.updateSetting('imageStyle', st.key));
    row.appendChild(btn);
  }
  section.appendChild(row);
  return section;
}

function sectionTitle(text) {
  const p = document.createElement('p');
  p.className = 'nooz-section-title';
  p.textContent = text;
  return p;
}

function buildFontSection(s, actions) {
  const section = document.createElement('div');
  section.className = 'nooz-stack nooz-stack--xs';
  section.appendChild(sectionTitle('Reading typeface'));

  const row = document.createElement('div');
  row.className = 'nooz-choice-row';
  for (const opt of FONT_OPTIONS) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'nooz-choice nooz-choice--font';
    if (s.font === opt.key) btn.classList.add('is-active');
    btn.dataset.font = opt.key;
    btn.setAttribute('aria-pressed', s.font === opt.key ? 'true' : 'false');

    const sample = document.createElement('span');
    sample.className = 'nooz-choice-sample';
    sample.textContent = 'Aa';
    btn.appendChild(sample);

    const label = document.createElement('span');
    label.className = 'nooz-choice-label';
    label.textContent = opt.label;
    btn.appendChild(label);

    btn.addEventListener('click', () => actions.updateSetting('font', opt.key));
    row.appendChild(btn);
  }
  section.appendChild(row);
  return section;
}

function buildPaperSection(s, actions) {
  const section = document.createElement('div');
  section.className = 'nooz-stack nooz-stack--xs';
  section.appendChild(sectionTitle('Paper'));

  const row = document.createElement('div');
  row.className = 'nooz-choice-row';
  for (const opt of PAPER_OPTIONS) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'nooz-choice nooz-choice--paper';
    if (s.paper === opt.key) btn.classList.add('is-active');
    btn.setAttribute('aria-pressed', s.paper === opt.key ? 'true' : 'false');

    const swatch = document.createElement('span');
    swatch.className = 'nooz-choice-swatch';
    swatch.style.background = opt.swatch;
    btn.appendChild(swatch);

    const label = document.createElement('span');
    label.className = 'nooz-choice-label';
    label.textContent = opt.label;
    btn.appendChild(label);

    btn.addEventListener('click', () => actions.updateSetting('paper', opt.key));
    row.appendChild(btn);
  }
  section.appendChild(row);
  return section;
}

function buildToggleSection(s, actions) {
  const section = document.createElement('div');
  section.className = 'nooz-stack nooz-stack--sm';
  section.appendChild(sectionTitle('Reading'));

  section.appendChild(
    toggleRow(
      'Highlight loaded language',
      'Mark loaded verbs, intensifiers, emotive adjectives, and editorializing hedges in article text -- tap a mark to see why.',
      s.highlightLoaded !== false,
      (v) => actions.updateSetting('highlightLoaded', v)
    )
  );

  section.appendChild(
    toggleRow(
      'Show images',
      'Display the lead image and any pictures a feed provides.',
      s.showImages !== false,
      (v) => actions.updateSetting('showImages', v)
    )
  );

  return section;
}

// Plain mailto, matching the Android app's own feedback pattern (same
// address). No form, no ticket system -- just a real inbox.
const FEEDBACK_EMAIL = 'nooz@asystemofcells.com';

function buildFeedbackSection() {
  const section = document.createElement('div');
  section.className = 'nooz-stack nooz-stack--xs';
  section.appendChild(sectionTitle('Feedback'));

  const link = document.createElement('a');
  link.className = 'nooz-button';
  link.href = `mailto:${FEEDBACK_EMAIL}?subject=${encodeURIComponent('Nooz web reader issue')}`;
  link.textContent = 'Report an issue';
  section.appendChild(link);

  return section;
}

function toggleRow(title, desc, checked, onChange) {
  const row = document.createElement('label');
  row.className = 'nooz-toggle-row';

  const text = document.createElement('span');
  text.className = 'nooz-toggle-text';
  const t = document.createElement('span');
  t.className = 'nooz-toggle-title';
  t.textContent = title;
  text.appendChild(t);
  const d = document.createElement('span');
  d.className = 'nooz-toggle-desc';
  d.textContent = desc;
  text.appendChild(d);
  row.appendChild(text);

  const toggle = document.createElement('input');
  toggle.type = 'checkbox';
  toggle.className = 'nooz-switch';
  toggle.checked = checked;
  toggle.setAttribute('role', 'switch');
  toggle.setAttribute('aria-checked', checked ? 'true' : 'false');
  toggle.addEventListener('change', () => onChange(toggle.checked));
  row.appendChild(toggle);

  return row;
}
