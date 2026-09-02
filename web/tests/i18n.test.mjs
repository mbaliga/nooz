import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import path from 'node:path';

import { installDom } from './dom.mjs';
import { resolveTag, direction, format } from '../js/i18n.js';

/**
 * The web reader's share of "a language is a data file".
 *
 * The parts worth pinning are the ones with judgement in them: which catalogue
 * a browser's language list resolves to, and what happens to a translation's
 * placeholders when a language needs them in a different order.
 *
 * The catalogues themselves are generated from i18n/strings/ by
 * tools/i18n/generate.py, so the last test here is really about the generator:
 * a translation that silently loses a %1$s would put an empty gap in a sentence
 * on a screen nobody testing in English will ever see.
 */

installDom();

const REPO = path.resolve(import.meta.dirname, '..', '..');
// index.json lives alongside the catalogues but is a manifest, not one of them.
const catalogues = Object.fromEntries(
  fs.readdirSync(path.join(REPO, 'web', 'i18n'))
    .filter((f) => f.endsWith('.json') && f !== 'index.json')
    .map((f) => [
      f.replace(/\.json$/, ''),
      JSON.parse(fs.readFileSync(path.join(REPO, 'web', 'i18n', f), 'utf8')),
    ]),
);
const available = Object.keys(catalogues);

test('the index offers exactly the catalogues that exist', () => {
  const index = JSON.parse(fs.readFileSync(path.join(REPO, 'web', 'i18n', 'index.json'), 'utf8'));
  const offered = index.locales.map((l) => l.tag).sort();
  assert.deepEqual(offered, available.slice().sort(), 'the picker and the catalogues disagree');
  assert.equal(index.total, Object.keys(catalogues.en).length, 'the denominator matches the base');
  for (const locale of index.locales) {
    assert.ok(locale.endonym, `${locale.tag} has no name of its own`);
    assert.equal(
      locale.coverage,
      Math.round((Object.keys(catalogues[locale.tag]).length / index.total) * 100),
      `${locale.tag}'s stated coverage does not match its catalogue`,
    );
  }
});

test('the shipped catalogues are the ones the app claims', () => {
  // If this drops, someone deleted a locale without meaning to.
  assert.ok(available.length >= 25, `expected the full locale set, got ${available.length}`);
  for (const expected of ['en', 'hi', 'ta', 'ur', 'zh-Hans', 'sw']) {
    assert.ok(available.includes(expected), `${expected} is missing`);
  }
});

test('a stored choice wins, and an unknown one falls back rather than breaking', () => {
  assert.equal(resolveTag('ta', ['en'], available), 'ta');
  assert.equal(resolveTag('xx', ['ta'], available), 'en', 'an unknown stored tag falls back to English');
});

test("the browser's preference order is honoured", () => {
  assert.equal(resolveTag('', ['xx', 'ml', 'en'], available), 'ml', 'the first available wins');
  assert.equal(resolveTag('', [], available), 'en');
  assert.equal(resolveTag('', undefined, available), 'en');
});

test('regional and cased tags find their catalogue', () => {
  // Browsers send what the OS gives them, which is rarely a bare language.
  assert.equal(resolveTag('', ['pt-BR'], available), 'pt');
  assert.equal(resolveTag('', ['zh-Hans-CN'], available), 'zh-Hans');
  assert.equal(resolveTag('', ['zh-hans'], available), 'zh-Hans', 'matching is case-insensitive');
  assert.equal(resolveTag('', ['ar-EG'], available), 'ar');
});

test('scripts that run right to left say so', () => {
  for (const tag of ['ar', 'fa', 'ur', 'ks']) {
    assert.equal(direction(tag), 'rtl', `${tag} is right-to-left`);
  }
  for (const tag of ['en', 'hi', 'ta', 'zh-Hans']) {
    assert.equal(direction(tag), 'ltr', `${tag} is left-to-right`);
  }
});

test('placeholders are positional, so a translation can reorder them', () => {
  assert.equal(format('%1$s · %2$d%% translated', ['தமிழ்', 41]), 'தமிழ் · 41% translated');
  // The whole point of positional: some languages need the number first.
  assert.equal(format('%%%2$d · %1$s', ['Türkçe', 41]), '%41 · Türkçe');
  assert.equal(format('%1$s', [undefined]), '', 'a missing argument leaves a gap, not "undefined"');
});

test('no translation loses a placeholder its English carries', () => {
  const base = catalogues.en;
  const problems = [];
  for (const [tag, strings] of Object.entries(catalogues)) {
    if (tag === 'en') continue;
    for (const [key, value] of Object.entries(strings)) {
      const expected = (base[key].match(/%\d+\$[sd]/g) || []).sort();
      const actual = (value.match(/%\d+\$[sd]/g) || []).sort();
      if (expected.join() === actual.join()) continue;

      // A `_one` key is the singular of a count, and several languages express
      // "1 source" as just "source" -- Arabic's مصدر واحد carries the "one" in
      // the word, not a digit. Dropping the number there is a translation
      // decision, not a slip, and Android's formatter ignores an unused
      // argument. Everything else, and inventing a placeholder anywhere, is a
      // mistake: a lost %1$s leaves a silent gap in a sentence on a screen
      // nobody testing in English will ever look at.
      const dropped = expected.filter((p) => !actual.includes(p));
      const invented = actual.filter((p) => !expected.includes(p));
      if (invented.length === 0 && dropped.length > 0 && key.endsWith('_one')) continue;

      problems.push(`${tag}.${key}: expected ${expected.join(' ') || '(none)'}, found ${actual.join(' ') || '(none)'}`);
    }
  }
  assert.deepEqual(problems, [], 'a translation dropped or invented a placeholder');
});

test('no translation carries a key the base does not', () => {
  const base = catalogues.en;
  const problems = [];
  for (const [tag, strings] of Object.entries(catalogues)) {
    for (const key of Object.keys(strings)) {
      if (!(key in base)) problems.push(`${tag}.${key}`);
    }
  }
  assert.deepEqual(problems, [], 'a rename was not propagated');
});

test('every catalogue is a subset of the base, and none is empty', () => {
  for (const [tag, strings] of Object.entries(catalogues)) {
    const count = Object.keys(strings).length;
    assert.ok(count > 0, `${tag} has no strings at all — it should not be shipped`);
    assert.ok(
      count <= Object.keys(catalogues.en).length,
      `${tag} has more strings than the base catalogue`,
    );
  }
});
