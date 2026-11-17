import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

// The reference guards are private functions inside the serverless handlers, so
// pull them out of the real source files rather than copy them: if web/api/*.js
// changes, this test sees the change.
const vectors = JSON.parse(readFileSync(new URL('../shared/host-guard-vectors.json', import.meta.url), 'utf8'));

function guardFrom(rel) {
  const src = readFileSync(new URL(rel, import.meta.url), 'utf8');
  const m = src.match(/function isBlockedTarget\(hostname\) \{[\s\S]*?\n\}\n?/);
  assert.ok(m, `isBlockedTarget found in ${rel}`);
  return vm.runInNewContext(`(${m[0].replace(/^function isBlockedTarget/, 'function')})`);
}

for (const rel of ['../../web/api/feed.js', '../../web/api/article.js']) {
  const guard = guardFrom(rel);
  test(`host-guard vectors agree with ${rel.split('/').pop()}`, () => {
    for (const c of vectors.cases) assert.equal(guard(c.host), c.blocked, `${c.host} blocked=${c.blocked}`);
  });
}
