# Topic keywords, in the languages the catalogue publishes in

`core/model/.../TopicLexicon.kt` carried English keywords and nothing else,
while the source catalogue ships 33 India regional feeds across eleven scripts.
Every one of those articles therefore classified as `general`: the Loom
collapsed to a single band and the Contrast dumbbells emptied, for exactly the
readers the India expansion was for. Nothing errored. It simply showed one
undifferentiated bar and looked like a quiet news day, every day.

One file per language, keyed by topic. `tools/i18n/generate.py` turns them into
`TopicLexiconL10n.kt` and `web/js/topics-l10n.json`, so Android and the web
reader classify the same story the same way — a classifier that disagrees
between two clients showing the same numbers is worse than one that is merely
incomplete.

## Writing terms

- **Nouns and noun phrases**, in the form a headline actually uses. Headlines
  are not sentences; a verb stem rarely appears intact.
- **Distinctive over frequent.** A word that appears in every second headline
  regardless of subject costs more than it earns.
- **Match the script the feeds are in.** For Urdu that is Perso-Arabic, not
  Devanagari transliteration; for Punjabi, Gurmukhi.
- Matching is case-insensitive and respects word boundaries in every script
  (see `TopicLexicon.matcherFor`), so inflected forms are not matched
  automatically. Where a language inflects heavily, prefer the stem-most form
  that still stands alone as a word.

## Honest limits

This is a first pass, not a finished lexicon, and it has not been reviewed by
native speakers of these languages. It is committed because an incomplete
lexicon that fires is a large improvement over a complete one that cannot, and
because `TopicEvidence` makes every match inspectable: a reader can tap a
classification and see the exact term that produced it, which is how a bad term
gets found and removed.
