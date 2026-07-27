// sources.js -- the "Sources" view: add, inspect, enable/disable, and remove
// the feeds this reader has chosen.
//
// Three pieces, top to bottom:
//   1. Add a source by URL (a plain input + button; validated just enough to
//      catch an obviously empty or malformed value -- no lecture, one short
//      line of inline text if it doesn't look like a URL).
//   2. "Your feeds" -- only the sources you added by URL, each with its enabled
//      toggle, a remove button, and an honest fetch-health indicator. Per the
//      "never silent" principle: a source that has been failing keeps showing
//      its status and reason, in place, for as long as it keeps failing.
//      Sources that come from the starter catalogue are NOT relisted here.
//   3. A short catalogue of pre-checked starter sources, grouped by region,
//      each with a plain on/off switch you flip in place. Switching one on adds
//      it; switching it off removes it -- it never jumps into a separate list.
//
// All feed-derived and reader-typed text (titles, URLs, error strings) is
// only ever assigned via .textContent, never innerHTML, so nothing a source
// or a reader types can inject markup into the page.

/**
 * @param {HTMLElement} container
 * @param {object} state
 * @param {object} actions
 */
export function render(container, state, actions) {
  container.replaceChildren();

  const root = document.createElement('div');
  root.className = 'nooz-layout nooz-stack nooz-stack--lg';

  const heading = document.createElement('h1');
  heading.textContent = 'Sources';
  root.appendChild(heading);

  root.appendChild(buildAddSourceSection(actions));
  const yourFeeds = buildYourFeedsSection(state, actions);
  if (yourFeeds) root.appendChild(yourFeeds);
  root.appendChild(buildStarterSourcesSection(state, actions));

  container.appendChild(root);
}

// ---------------------------------------------------------------------------
// 1. Add a source by URL
// ---------------------------------------------------------------------------

// Persists only for this browser tab's lifetime, purely so a reader's
// in-progress typing survives an unrelated re-render (e.g. a background
// refresh finishing for some other source). Not part of app state -- never
// read or written by anything outside this module.
let addUrlDraft = '';

function buildAddSourceSection(actions) {
  const section = document.createElement('div');
  section.className = 'nooz-stack nooz-stack--xs';

  const label = document.createElement('p');
  label.className = 'nooz-section-title';
  label.textContent = 'Add a source';
  section.appendChild(label);

  const form = document.createElement('form');
  form.className = 'nooz-input-group';
  form.setAttribute('novalidate', ''); // this module does its own plain validation

  const input = document.createElement('input');
  input.type = 'url';
  input.className = 'nooz-input';
  input.placeholder = 'https://example.com/feed.xml';
  input.setAttribute('aria-label', 'Feed URL');
  input.autocomplete = 'off';
  input.value = addUrlDraft;
  form.appendChild(input);

  const submitBtn = document.createElement('button');
  submitBtn.type = 'submit';
  submitBtn.className = 'nooz-button nooz-button--primary';
  submitBtn.textContent = 'Add';
  form.appendChild(submitBtn);

  section.appendChild(form);

  const error = document.createElement('p');
  error.className = 'nooz-byline';
  error.style.color = 'var(--paper-signal-danger)';
  error.setAttribute('role', 'alert');
  error.hidden = true;
  section.appendChild(error);

  const showError = (message) => {
    error.textContent = message;
    error.hidden = false;
  };
  const clearError = () => {
    if (error.hidden) return;
    error.hidden = true;
    error.textContent = '';
  };

  input.addEventListener('input', () => {
    addUrlDraft = input.value;
    clearError();
  });

  form.addEventListener('submit', (event) => {
    event.preventDefault();
    const value = input.value.trim();
    if (!value) {
      showError('Enter a URL.');
      return;
    }
    if (!isPlausibleUrl(value)) {
      showError('Enter a valid URL.');
      return;
    }
    clearError();
    actions.addSourceByUrl(value);
    addUrlDraft = '';
    input.value = '';
  });

  return section;
}

function isPlausibleUrl(value) {
  try {
    const parsed = new URL(value);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:';
  } catch (_err) {
    return false;
  }
}

// ---------------------------------------------------------------------------
// 2. Your sources
// ---------------------------------------------------------------------------

// "Your feeds" -- only the sources you added by URL yourself. Sources that come
// from the starter catalogue are controlled by their on/off switch down in that
// catalogue, in place, so they are never duplicated up here or "moved" into a
// separate list. Returns null (nothing rendered) when you have no custom feeds.
function buildYourFeedsSection(state, actions) {
  const starterUrls = new Set((state.starters || []).map((s) => s.url));
  const custom = state.sources.filter((s) => !starterUrls.has(s.url));
  if (custom.length === 0) return null;

  const section = document.createElement('div');
  section.className = 'nooz-stack nooz-stack--xs';

  const label = document.createElement('p');
  label.className = 'nooz-section-title';
  label.textContent = 'Your feeds';
  section.appendChild(label);

  const list = document.createElement('div');
  for (const source of custom) {
    list.appendChild(buildSourceRow(source, state, actions));
  }
  section.appendChild(list);

  return section;
}

function buildSourceRow(source, state, actions) {
  const displayTitle = source.title || source.url;

  const row = document.createElement('div');
  row.className = 'nooz-source-row';

  const info = document.createElement('div');
  info.className = 'nooz-source-row-info';

  const titleEl = document.createElement('span');
  titleEl.className = 'nooz-source-row-title';
  titleEl.textContent = displayTitle;
  info.appendChild(titleEl);

  const metaEl = document.createElement('span');
  metaEl.className = 'nooz-source-row-meta';
  metaEl.textContent = source.region ? `${source.region} · ${source.url}` : source.url;
  info.appendChild(metaEl);

  row.appendChild(info);

  const actionsWrap = document.createElement('div');
  actionsWrap.className = 'nooz-source-row-actions';

  actionsWrap.appendChild(buildStatusIndicator(source, state));

  const toggle = document.createElement('input');
  toggle.type = 'checkbox';
  toggle.className = 'nooz-switch';
  toggle.checked = !!source.enabled;
  toggle.setAttribute('role', 'switch');
  toggle.setAttribute('aria-checked', source.enabled ? 'true' : 'false');
  toggle.setAttribute('aria-label', `${source.enabled ? 'Disable' : 'Enable'} ${displayTitle}`);
  toggle.addEventListener('change', () => actions.toggleSourceEnabled(source.id));
  actionsWrap.appendChild(toggle);

  const removeBtn = document.createElement('button');
  removeBtn.type = 'button';
  removeBtn.className = 'nooz-button';
  removeBtn.textContent = 'Remove';
  removeBtn.setAttribute('aria-label', `Remove ${displayTitle}`);
  removeBtn.addEventListener('click', () => actions.removeSource(source.id));
  actionsWrap.appendChild(removeBtn);

  row.appendChild(actionsWrap);
  return row;
}

// Fetch-health indicator: a small dot plus a short text label, so status
// never rides on colour alone -- and, when a source is failing, the reason
// stays visible (truncated with an ellipsis if long) with the full reason
// still reachable via a title tooltip. Nothing about a persistently-failing
// source is ever hidden.
function buildStatusIndicator(source, state) {
  const status = (state.fetchStatus || {})[source.id];
  const errorMessage = (state.fetchErrors || {})[source.id];

  const wrap = document.createElement('div');
  wrap.className = 'nooz-row nooz-row--xs';
  wrap.style.flexWrap = 'nowrap';
  wrap.style.gap = 'var(--space-xxs)';

  const dot = document.createElement('span');
  dot.className = 'nooz-status-dot';

  const label = document.createElement('span');
  label.className = 'nooz-byline nooz-truncate';
  label.style.maxWidth = '120px';

  if (status === 'ok') {
    dot.classList.add('nooz-status-dot--ok');
    label.textContent = 'OK';
  } else if (status === 'loading') {
    dot.classList.add('nooz-status-dot--loading');
    label.textContent = 'Checking…';
  } else if (status === 'error') {
    dot.classList.add('nooz-status-dot--error');
    const reason = errorMessage || 'Unknown error';
    label.textContent = reason;
    label.style.color = 'var(--paper-signal-danger)';
    label.title = reason;
    wrap.title = reason;
  } else {
    label.textContent = 'Not checked yet';
  }

  wrap.appendChild(dot);
  wrap.appendChild(label);
  wrap.setAttribute('role', 'status');
  wrap.setAttribute('aria-label', `Fetch status: ${label.textContent}`);
  return wrap;
}

// ---------------------------------------------------------------------------
// 3. Starter sources
// ---------------------------------------------------------------------------

function buildStarterSourcesSection(state, actions) {
  const section = document.createElement('div');
  section.className = 'nooz-stack nooz-stack--md';

  const label = document.createElement('p');
  label.className = 'nooz-section-title';
  label.textContent = 'Starter sources';
  section.appendChild(label);

  const starters = state.starters || [];

  if (starters.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'nooz-empty-state-text';
    empty.style.textAlign = 'left';
    empty.style.maxWidth = 'none';
    empty.textContent = 'No starter sources available.';
    section.appendChild(empty);
    return section;
  }

  const description = document.createElement('p');
  description.className = 'nooz-empty-state-text';
  description.style.textAlign = 'left';
  description.style.maxWidth = 'none';
  description.textContent = 'A short list of pre-checked, currently-live feeds -- switch on any you like.';
  section.appendChild(description);

  const byUrl = new Map(state.sources.map((source) => [source.url, source]));

  const groups = new Map();
  for (const starter of starters) {
    const region = starter.region || 'Other';
    if (!groups.has(region)) groups.set(region, []);
    groups.get(region).push(starter);
  }

  for (const [region, regionStarters] of groups) {
    const group = document.createElement('div');
    group.className = 'nooz-stack nooz-stack--xs';

    const regionHeading = document.createElement('h3');
    regionHeading.textContent = region;
    group.appendChild(regionHeading);

    const rows = document.createElement('div');
    for (const starter of regionStarters) {
      rows.appendChild(buildStarterRow(starter, byUrl.get(starter.url), state, actions));
    }
    group.appendChild(rows);

    section.appendChild(group);
  }

  return section;
}

// `subscribed` is the reader's own source object for this starter's URL, or
// undefined if it isn't on their list yet. The switch reflects that -- on means
// subscribed -- and flipping it adds or removes the source. Either way the row
// stays exactly where it is in the catalogue; nothing jumps to another section.
function buildStarterRow(starter, subscribed, state, actions) {
  const row = document.createElement('div');
  row.className = 'nooz-source-row';

  const info = document.createElement('div');
  info.className = 'nooz-source-row-info';

  const titleEl = document.createElement('span');
  titleEl.className = 'nooz-source-row-title';
  titleEl.textContent = starter.title;
  info.appendChild(titleEl);

  const metaEl = document.createElement('span');
  metaEl.className = 'nooz-source-row-meta';
  metaEl.textContent = starter.url;
  info.appendChild(metaEl);

  row.appendChild(info);

  const actionsWrap = document.createElement('div');
  actionsWrap.className = 'nooz-source-row-actions';

  // Never-silent: a subscribed starter that's mid-check or failing shows its
  // status right here, in place, exactly as a feed in "Your feeds" would.
  if (subscribed) {
    const status = (state.fetchStatus || {})[subscribed.id];
    if (status === 'loading' || status === 'error') {
      actionsWrap.appendChild(buildStatusIndicator(subscribed, state));
    }
  }

  // A plain Add/Remove button -- not a switch. One control, one action; the
  // row never moves regardless of which state it's in.
  const on = !!subscribed;
  const btn = document.createElement('button');
  btn.type = 'button';
  btn.className = 'nooz-button' + (on ? '' : ' nooz-button--primary');
  btn.textContent = on ? 'Remove' : 'Add';
  btn.setAttribute('aria-label', `${on ? 'Remove' : 'Add'} ${starter.title}`);
  btn.addEventListener('click', () => {
    if (subscribed) actions.removeSource(subscribed.id);
    else actions.addStarter(starter);
  });
  actionsWrap.appendChild(btn);

  row.appendChild(actionsWrap);
  return row;
}
