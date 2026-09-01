import assert from 'node:assert/strict';
import test from 'node:test';
import fs from 'node:fs';
import path from 'node:path';

/**
 * The web reader's own i18n ratchet, the counterpart to Gradle's `verifyI18n`.
 *
 * The layer being in place is not the same as the reader being translated, and
 * the difference is easy to miss from the inside: `web/i18n/*.json` reports 100%
 * for twenty-nine locales, and every one of those numbers is about the
 * *catalogue*, not about how much of this app reads from it. When this guard was
 * first written exactly one view called `t()`, so a reader who chose Tamil got a
 * Tamil settings heading and an English everything-else — while the picker
 * showed no shortfall at all.
 *
 * Same shape as the Android ratchet, for the same reason: a per-file budget that
 * can only shrink, a new file enforced from its first commit, and a failure if a
 * file improves without its number coming down. `_ALLOWLIST` below is a to-do
 * list, not a permission slip.
 */

const REPO = path.resolve(import.meta.dirname, '..');

/**
 * Words that must stay in English wherever they appear. "Nooz" is the app's own
 * name (STATE.md §RESERVED, decided by the owner) and a translated masthead is a
 * bug; the two feature names are built on it. Routing these through a key would
 * mean thirty identical copies of the same word and a chance for one of them to
 * drift.
 */
const BRAND = new Set(['Nooz', 'Nooz Flash', 'Nooz Cast', 'GDELT', 'Wikipedia']);

/**
 * String literals in positions that put words on the screen.
 *
 * `textContent` and the two attributes that carry an accessible name are the
 * whole surface that matters: everything user-visible in this codebase goes
 * through one of them, by the same "never innerHTML on untrusted content" rule
 * the views already follow.
 */
const UI_STRING = new RegExp(
  [
    String.raw`textContent\s*=\s*(['"])((?:(?!\1)[^\\]|\\.){3,}?)\1`,
    String.raw`setAttribute\(\s*['"](?:aria-label|title|placeholder)['"]\s*,\s*(['"])((?:(?!\1)[^\\]|\\.){3,}?)\1`,
    String.raw`\.placeholder\s*=\s*(['"])((?:(?!\1)[^\\]|\\.){3,}?)\1`,
  ].join('|'),
  'g',
);

/**
 * Files still carrying English, and how much. Delete a line when it reaches
 * zero. The numbers are a floor, not a total: copy that reaches the screen some
 * other way is not counted, exactly as on the Android side.
 */
const ALLOWLIST = {
  'js/onboarding.js': 1,
  'js/views/settings.js': 2,
};

function scan() {
  const found = {};
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        // api/ is serverless Node, i18n/ is generated data.
        if (entry.name === 'node_modules' || entry.name === 'api' || entry.name === 'i18n') continue;
        walk(full);
      } else if (entry.name.endsWith('.js') && !entry.name.endsWith('-l10n.js')) {
        const rel = path.relative(REPO, full).split(path.sep).join('/');
        const src = fs.readFileSync(full, 'utf8');
        const hits = [...src.matchAll(UI_STRING)]
          .map((m) => m[2] ?? m[4] ?? m[6])
          .filter((v) => v && /[A-Za-z]{3}/.test(v) && !v.startsWith('$') && !BRAND.has(v.trim()));
        if (hits.length) found[rel] = hits;
      }
    }
  };
  walk(path.join(REPO, 'js'));
  return found;
}

test('no new English is hardcoded into the web reader', () => {
  const found = scan();
  const problems = [];

  for (const [file, hits] of Object.entries(found)) {
    const budget = ALLOWLIST[file] ?? 0;
    if (hits.length > budget) {
      problems.push(
        `${file}: ${hits.length} hardcoded string(s), allowed ${budget}\n` +
          hits.slice(budget).map((h) => `      "${h.slice(0, 60)}"`).join('\n'),
      );
    }
  }

  // The ratchet's other half: slack a file no longer needs would quietly let a
  // regression back in later.
  for (const [file, budget] of Object.entries(ALLOWLIST)) {
    const actual = found[file]?.length ?? 0;
    if (actual < budget) {
      problems.push(`${file}: now ${actual}, allowlist still says ${budget} — lower it (or delete the line)`);
    }
  }

  assert.deepEqual(
    problems,
    [],
    'Interface copy must go through t() in js/i18n.js so it can be translated.\n  ' +
      problems.join('\n  '),
  );
});

test('the reader reports how much of itself is actually translated', () => {
  // The number that matters is not the catalogue's completeness but this one:
  // strings the views take from it, against strings they still hardcode.
  const found = scan();
  const hardcoded = Object.values(found).reduce((n, hits) => n + hits.length, 0);
  const wired = fs.readdirSync(path.join(REPO, 'js', 'views'))
    .concat(['../app.js', '../onboarding.js'])
    .reduce((n, f) => {
      const full = path.join(REPO, 'js', 'views', f);
      if (!fs.existsSync(full) || !f.endsWith('.js')) return n;
      return n + (fs.readFileSync(full, 'utf8').match(/\bt\(/g) || []).length;
    }, 0);

  // Not an assertion about a target, just a fact printed where someone will see
  // it: a silent 6% is how this went unnoticed in the first place.
  console.log(`      web i18n: ${wired} translated call site(s), ${hardcoded} still hardcoded`);
  assert.ok(wired + hardcoded > 0, 'the scanner found nothing at all — it is broken');
});

test('every t() call names a key that exists', () => {
  // A `t('typo_key', 'Some words')` shows its English fallback and nothing
  // anywhere reports it: the catalogue still says 100%, the coverage ratchet
  // above counts the call as translated, and the string is English forever.
  // This guard found `loom_strip_label` on its first run — a key the wiring
  // referenced and nobody had added.
  const base = JSON.parse(
    fs.readFileSync(path.join(REPO, '..', 'i18n', 'strings', 'en.json'), 'utf8'),
  );
  const missing = [];
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        if (entry.name === 'node_modules' || entry.name === 'api') continue;
        walk(full);
      } else if (entry.name.endsWith('.js')) {
        const src = fs.readFileSync(full, 'utf8');
        for (const m of src.matchAll(/\bt\(\s*'([a-z0-9_]+)'/g)) {
          if (!(m[1] in base)) missing.push(`${path.basename(full)}: ${m[1]}`);
        }
      }
    }
  };
  walk(path.join(REPO, 'js'));
  assert.deepEqual([...new Set(missing)], [], 'a t() call names a key that is not in en.json');
});
