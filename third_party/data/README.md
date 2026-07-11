# Bundled data

## `core/data/.../assets/common_words.txt`
The 30,000 most frequent English words, one per line, rank-ordered — the
common-vs-obscure gate for the dictionary lens (a word absent from this list,
alphabetic and long enough, is treated as "obscure" and underlined for a
definition lookup).

Derived from Peter Norvig's `count_1w.txt` (the words column only, top 30k by
corpus frequency), distributed under the MIT license. Norvig's n-gram data is
itself derived from the Google Web Trillion Word Corpus. A bare list of common
words is used purely as a frequency gate; no definitions or counts are bundled.
Source: https://norvig.com/ngrams/

## Dictionaries (definitions)
Definitions are **not bundled** — they are downloaded on the user's explicit
request (owner's "one-click download" dictionaries). The catalogue and their
licenses live in `DictionaryCatalog`; the default option is the public-domain
**Webster's 1913** flat-JSON build
(https://github.com/matthewreagan/WebstersEnglishDictionary, MIT repo over a
public-domain source). Every download URL is live-verified per the build rule.
