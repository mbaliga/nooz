// lens.js -- loaded-language highlighting, ported from the Android app's
// BiasLexicon (core/model/.../BiasLexicon.kt), in the Recasens et al. (2013)
// lineage: loaded verbs, intensifiers, emotive adjectives, editorializing
// hedges. The app underlines these in the reader; here we mark them the same
// way, and each mark carries a title saying exactly which category fired, so
// the flag is always inspectable rather than a black box.
//
// annotate() only ever wraps text that is already in the DOM as plain text
// nodes -- it never parses strings into markup -- so it can be run over
// already-sanitized article bodies without reintroducing any injection risk.

const TERMS = {
  'loaded verb': ['slammed', 'blasted', 'bashed', 'gushed', 'boasted', 'bragged', 'touted',
    'admitted', 'conceded', 'claimed', 'refused', 'lashed out', 'hit out',
    'ripped', 'railed', 'crowed', 'vowed', 'insisted', 'unleashed', 'erupted',
    'denied', 'dismissed', 'mocked', 'scoffed', 'fumed', 'raged', 'snapped',
    'warned', 'threatened', 'demanded', 'hailed', 'praised', 'condemned',
    'denounced', 'decried', 'lambasted', 'savaged', 'grilled', 'pounced',
    'trumpeted', 'peddled', 'spewed', 'parroted', 'chided', 'berated',
    'scolded', 'hyped', 'downplayed', 'brushed off', 'shrugged off',
    'doubled down', 'backpedaled', 'walked back', 'caved', 'scrambled',
    'stormed', 'seized on', 'clung to', 'sowed', 'stoked', 'fueled', 'fuelled'],
  intensifier: ['very', 'extremely', 'incredibly', 'utterly', 'totally', 'hugely',
    'massively', 'wildly', 'absolutely', 'completely', 'remarkably',
    'highly', 'deeply', 'vastly', 'profoundly', 'enormously', 'immensely',
    'tremendously', 'exceedingly', 'overwhelmingly', 'startlingly',
    'shockingly', 'unbelievably', 'outrageously', 'downright', 'flat-out',
    'sheer', 'wholly', 'entirely', 'thoroughly', 'positively'],
  'emotive adjective': ['shocking', 'outrageous', 'devastating', 'stunning', 'horrific', 'brutal',
    'disastrous', 'catastrophic', 'explosive', 'damning', 'scathing',
    'bombshell', 'chaotic', 'dramatic', 'staggering', 'alarming',
    'unprecedented', 'historic', 'dire', 'grim', 'bleak', 'dreadful',
    'horrifying', 'terrifying', 'appalling', 'disgraceful', 'shameful',
    'scandalous', 'searing', 'blistering', 'withering', 'fiery', 'heated',
    'bitter', 'furious', 'controversial', 'embattled', 'beleaguered',
    'troubled', 'botched', 'bungled', 'reckless', 'radical', 'extreme',
    'sweeping', 'seismic', 'landmark', 'watershed', 'monumental', 'colossal',
    'crippling', 'spiraling', 'spiralling', 'rampant', 'raging', 'toxic',
    'sinister', 'grave', 'stark', 'damaging', 'relentless', 'ruthless'],
  'editorializing hedge': ['so-called', 'allegedly', 'reportedly', 'apparently', 'notoriously',
    'infamously', 'supposedly', 'purportedly', 'arguably', 'clearly',
    'obviously', 'of course', 'seemingly', 'presumably', 'ostensibly',
    'undoubtedly', 'unsurprisingly', 'predictably', 'tellingly', 'curiously',
    'needless to say', 'make no mistake', 'to be sure', 'some say',
    'critics say', 'critics argue', 'many believe', 'sources say',
    'insiders say', 'it is understood', 'it is believed'],
};

function escapeRegExp(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// One combined, longest-match-first alternation so multi-word phrases win over
// their component words ("lashed out" beats "out"). Each alternative maps back
// to its category via TERM_CATEGORY.
const TERM_CATEGORY = new Map();
const ALTERNATION = (() => {
  const all = [];
  for (const [category, list] of Object.entries(TERMS)) {
    for (const term of list) {
      all.push(term);
      TERM_CATEGORY.set(term.toLowerCase(), category);
    }
  }
  all.sort((a, b) => b.length - a.length);
  return new RegExp('\\b(' + all.map(escapeRegExp).join('|') + ')\\b', 'gi');
})();

const SKIP_TAGS = new Set(['A', 'MARK', 'SCRIPT', 'STYLE', 'CODE', 'PRE', 'BUTTON']);

/**
 * Walk every text node under `root` and wrap loaded-language matches in
 * <mark class="nooz-loaded" data-cat="..."> spans. Idempotent-ish: skips text
 * that's already inside a <mark> or a link, so re-running won't double-wrap.
 */
export function annotate(root) {
  if (!root) return;
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      if (!node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
      let el = node.parentElement;
      while (el && el !== root) {
        if (SKIP_TAGS.has(el.tagName)) return NodeFilter.FILTER_REJECT;
        el = el.parentElement;
      }
      return NodeFilter.FILTER_ACCEPT;
    },
  });

  const targets = [];
  let n;
  while ((n = walker.nextNode())) targets.push(n);

  for (const textNode of targets) {
    const text = textNode.nodeValue;
    ALTERNATION.lastIndex = 0;
    if (!ALTERNATION.test(text)) continue;
    ALTERNATION.lastIndex = 0;

    const frag = document.createDocumentFragment();
    let last = 0;
    let m;
    while ((m = ALTERNATION.exec(text)) !== null) {
      if (m.index > last) frag.appendChild(document.createTextNode(text.slice(last, m.index)));
      const category = TERM_CATEGORY.get(m[0].toLowerCase()) || 'loaded';
      const mark = document.createElement('mark');
      mark.className = 'nooz-loaded';
      mark.dataset.cat = category;
      mark.title = `Loaded language: ${category}`;
      mark.textContent = m[0];
      frag.appendChild(mark);
      last = m.index + m[0].length;
      if (m[0].length === 0) ALTERNATION.lastIndex++; // guard against zero-width loops
    }
    if (last < text.length) frag.appendChild(document.createTextNode(text.slice(last)));
    textNode.parentNode.replaceChild(frag, textNode);
  }
}
