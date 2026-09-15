import assert from 'node:assert/strict';
import test from 'node:test';

import { stripTrailingBoilerplate, isWholeTextBoilerplate } from '../js/articleBoilerplate.js';

/**
 * Every case here is either "this exact junk gets removed" or "this real
 * sentence survives untouched" — both matter equally. The patterns are
 * anchored to the end of the string on purpose (see the file's own header),
 * so the negative cases below are the ones actually proving that: a real
 * sentence that merely *contains* one of the trigger words must come through
 * exactly as written, because nothing about it reaches the `$`.
 */

test('strips a WordPress syndication footer', () => {
  const s = 'The city council voted 5-2 to approve the budget. The post City council approves budget appeared first on The Daily Record.';
  assert.equal(stripTrailingBoilerplate(s), 'The city council voted 5-2 to approve the budget.');
});

test('strips a bare "Continue reading" label with a trailing arrow', () => {
  assert.equal(stripTrailingBoilerplate('The storm is expected to clear by Tuesday. Continue reading →'), 'The storm is expected to clear by Tuesday.');
  assert.equal(stripTrailingBoilerplate('The storm is expected to clear by Tuesday. Read More »'), 'The storm is expected to clear by Tuesday.');
  assert.equal(stripTrailingBoilerplate('The storm is expected to clear by Tuesday. Read the full story...'), 'The storm is expected to clear by Tuesday.');
});

test('strips a standalone ad-network label', () => {
  assert.equal(stripTrailingBoilerplate('Some genuine excerpt text here that is long enough. Advertisement'), 'Some genuine excerpt text here that is long enough.');
  assert.equal(stripTrailingBoilerplate('Some genuine excerpt text here that is long enough. Sponsored Content'), 'Some genuine excerpt text here that is long enough.');
});

test('strips a trailing WordPress excerpt truncation marker', () => {
  assert.equal(stripTrailingBoilerplate('The mayor spoke for twenty minutes about the new budget […]'), 'The mayor spoke for twenty minutes about the new budget');
});

test('strips a bare trailing tracking link', () => {
  assert.equal(stripTrailingBoilerplate('Full details are available now. https://example.com/track?id=abc123'), 'Full details are available now.');
});

test('strips more than one chained fragment', () => {
  const s = 'Local officials confirmed the closure will last through the weekend. […] Continue reading →';
  assert.equal(stripTrailingBoilerplate(s), 'Local officials confirmed the closure will last through the weekend.');
});

test('leaves a real sentence containing the same words alone, because they never reach the end', () => {
  assert.equal(
    stripTrailingBoilerplate('The reporter continued reading the transcript for another hour before filing the story.'),
    'The reporter continued reading the transcript for another hour before filing the story.',
  );
  assert.equal(
    stripTrailingBoilerplate('Read more about the case at the courthouse tomorrow, officials said.'),
    'Read more about the case at the courthouse tomorrow, officials said.',
  );
  assert.equal(
    stripTrailingBoilerplate('Sponsored data plans are becoming more common in emerging markets, the report found.'),
    'Sponsored data plans are becoming more common in emerging markets, the report found.',
  );
});

test('leaves a plain, no-boilerplate sentence completely untouched', () => {
  const s = 'City council approved the new budget on a 5-2 vote after a lengthy debate.';
  assert.equal(stripTrailingBoilerplate(s), s);
});

test('an entirely-boilerplate string reduces to empty', () => {
  assert.equal(stripTrailingBoilerplate('The post Foo Bar appeared first on Example News.'), '');
  assert.equal(stripTrailingBoilerplate('  Advertisement  '), '');
});

test('isWholeTextBoilerplate is true only when nothing real is left', () => {
  assert.equal(isWholeTextBoilerplate('Continue reading →'), true);
  assert.equal(isWholeTextBoilerplate('Advertisement'), true);
  assert.equal(isWholeTextBoilerplate('The city council voted 5-2 to approve the budget.'), false);
  assert.equal(isWholeTextBoilerplate(''), false);
  assert.equal(isWholeTextBoilerplate('   '), false);
});

// Honest limit, not a bug: a genuine citation that happens to end a paragraph
// with a bare URL and no label reads identically to a tracking link with no
// label, and this — deliberately — cannot tell them apart. In a plain-text
// or link-rendered context a bare trailing URL is overwhelmingly feed
// plumbing rather than a citation a reader would tap from a phone anyway, so
// the trade favors removing it.
test('honest limit: a genuine bare trailing URL is indistinguishable from a tracking link and is removed too', () => {
  assert.equal(
    stripTrailingBoilerplate('The full text of the ruling is available at https://legit-court.gov/opinions/2026-441.pdf'),
    'The full text of the ruling is available at',
  );
});
