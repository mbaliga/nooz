// topics.js -- transparent topic classification, ported from the Android
// app's TopicLexicon (core/model/.../TopicLexicon.kt) so the web reader
// groups stories the same way the app does. Keyword/phrase rules per topic,
// matched case-insensitively on word boundaries, plus a feed-category map.
//
// Deliberately a legible lexicon, not an opaque model: a story's section is
// something a reader could reconstruct by eye, which is the whole point of an
// app about what flowed vs. what you read.

export const TOPICS = [
  { key: 'politics', label: 'Politics' },
  { key: 'conflict', label: 'Conflict' },
  { key: 'business', label: 'Business' },
  { key: 'tech', label: 'Technology' },
  { key: 'science', label: 'Science' },
  { key: 'climate', label: 'Climate' },
  { key: 'health', label: 'Health' },
  { key: 'culture', label: 'Culture' },
  { key: 'sport', label: 'Sport' },
  { key: 'general', label: 'General' },
];

export const TOPIC_LABEL = Object.fromEntries(TOPICS.map((t) => [t.key, t.label]));

const TERMS = {
  politics: ['election', 'parliament', 'congress', 'senate', 'president', 'prime minister',
    'government', 'policy', 'minister', 'lawmaker', 'legislation', 'vote', 'voter',
    'campaign', 'democrat', 'republican', 'coalition', 'referendum', 'cabinet',
    'diplomacy', 'sanction', 'supreme court', 'governor', 'impeachment', 'ballot'],
  conflict: ['war', 'airstrike', 'missile', 'troops', 'ceasefire', 'militant', 'insurgent',
    'gunfire', 'shelling', 'offensive', 'casualties', 'hostage', 'terror', 'militia',
    'occupation', 'invasion', 'border clash', 'rebel', 'drone strike', 'genocide',
    'armed forces', 'front line'],
  business: ['market', 'stocks', 'shares', 'economy', 'inflation', 'interest rate', 'gdp',
    'revenue', 'profit', 'earnings', 'merger', 'acquisition', 'ipo', 'startup',
    'trade', 'tariff', 'central bank', 'recession', 'unemployment', 'currency',
    'investor', 'nasdaq', 'quarterly'],
  tech: ['software', 'app', 'smartphone', 'chip', 'semiconductor', 'artificial intelligence',
    'machine learning', 'algorithm', 'cybersecurity', 'data breach', 'cloud',
    'gadget', 'processor', 'open source', 'encryption', 'social media', 'silicon',
    'robot', 'quantum computing'],
  science: ['research', 'study', 'scientist', 'physics', 'astronomy', 'space', 'nasa',
    'galaxy', 'particle', 'genome', 'biology', 'chemistry', 'experiment', 'telescope',
    'satellite', 'spacecraft', 'fossil', 'evolution', 'laboratory'],
  climate: ['climate', 'climate change', 'global warming', 'emissions', 'carbon', 'renewable',
    'solar', 'wind power', 'drought', 'flood', 'wildfire', 'heatwave', 'biodiversity',
    'deforestation', 'greenhouse', 'monsoon', 'pollution', 'net zero', 'glacier', 'cyclone'],
  health: ['health', 'hospital', 'disease', 'virus', 'vaccine', 'outbreak', 'pandemic',
    'cancer', 'mental health', 'medicine', 'clinical', 'epidemic', 'infection',
    'diabetes', 'surgery', 'drug', 'patients', 'public health'],
  culture: ['film', 'movie', 'music', 'album', 'festival', 'art', 'artist', 'book', 'author',
    'theatre', 'celebrity', 'actor', 'director', 'museum', 'fashion', 'cinema',
    'streaming', 'box office', 'concert', 'award'],
  sport: ['match', 'tournament', 'cricket', 'football', 'soccer', 'goal', 'world cup',
    'olympics', 'medal', 'championship', 'league', 'player', 'coach', 'wicket',
    'innings', 'striker', 'tennis', 'grand slam', 'fifa'],
};

const CATEGORY_MAP = {
  politics: 'politics', world: 'politics', nation: 'politics', national: 'politics',
  election: 'politics', government: 'politics',
  war: 'conflict', conflict: 'conflict', military: 'conflict', defence: 'conflict', defense: 'conflict',
  business: 'business', economy: 'business', markets: 'business', finance: 'business', money: 'business',
  technology: 'tech', tech: 'tech', gadgets: 'tech',
  science: 'science', space: 'science',
  climate: 'climate', environment: 'climate', weather: 'climate',
  health: 'health', wellness: 'health', medicine: 'health',
  culture: 'culture', entertainment: 'culture', arts: 'culture', lifestyle: 'culture',
  film: 'culture', music: 'culture',
  sport: 'sport', sports: 'sport',
};

// Precompiled word-boundary matchers, per topic.
const MATCHERS = Object.fromEntries(
  Object.entries(TERMS).map(([topic, list]) => [
    topic,
    list.map((term) => new RegExp('\\b' + escapeRegExp(term) + '\\b', 'i')),
  ])
);

function escapeRegExp(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Classify one item into a topic key. Feed-declared category wins when it maps
 * cleanly (the source's own labelling is the strongest signal); otherwise the
 * topic with the most keyword hits across title + summary; 'general' if none.
 */
export function classifyItem(item) {
  const cat = (item.category || '').trim().toLowerCase();
  if (cat) {
    if (CATEGORY_MAP[cat]) return CATEGORY_MAP[cat];
    for (const [key, topic] of Object.entries(CATEGORY_MAP)) {
      if (cat.includes(key)) return topic;
    }
  }

  const text = `${item.title || ''} ${item.summary || ''}`;
  let best = 'general';
  let bestScore = 0;
  for (const [topic, matchers] of Object.entries(MATCHERS)) {
    let score = 0;
    for (const re of matchers) if (re.test(text)) score += 1;
    if (score > bestScore) {
      bestScore = score;
      best = topic;
    }
  }
  return best;
}
