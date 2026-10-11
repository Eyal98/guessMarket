import { test } from 'node:test';
import assert from 'node:assert/strict';
import { oneAtATime } from '../public/js/dom.js';

// Holding Enter in the amount field repeats the key; each repeat must not load the money again.
test('an action asked for again while it is still under way is not done twice', async () => {
    let runs = 0;
    let finish;
    const deposit = oneAtATime(() => { runs += 1; return new Promise(resolve => { finish = resolve; }); });
    deposit();
    deposit();
    deposit();
    assert.equal(runs, 1);
    finish();
    await new Promise(resolve => setTimeout(resolve, 0));
    deposit();
    assert.equal(runs, 2);
});

test('an action that fails can be asked for again', async () => {
    let runs = 0;
    const deposit = oneAtATime(async () => { runs += 1; throw new Error('refused'); });
    await deposit().catch(() => {});
    await deposit().catch(() => {});
    assert.equal(runs, 2);
});
