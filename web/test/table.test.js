import { test } from 'node:test';
import assert from 'node:assert/strict';
import { keepSelection, fillsInPlace } from '../public/js/table.js';

const byNumber = event => event.number;

test('the selected row is found again in fresh data by its key, not by identity', () => {
    const fresh = [{ number: 1, status: 'Active' }, { number: 2, status: 'Closed' }];
    assert.deepEqual(keepSelection(fresh, 2, byNumber), { number: 2, status: 'Closed' });
});

test('the same rows with new values are written in place; any other change redraws', () => {
    const before = [{ number: 1, account: 5 }, { number: 2, account: 7 }];
    assert.equal(fillsInPlace(before, [{ number: 1, account: 9 }, { number: 2, account: 7 }], byNumber), true);
    assert.equal(fillsInPlace(before, [{ number: 2, account: 7 }, { number: 1, account: 5 }], byNumber), false);
    assert.equal(fillsInPlace(before, [{ number: 1, account: 5 }], byNumber), false);
    assert.equal(fillsInPlace([], [], byNumber), false);
    assert.equal(fillsInPlace([{ a: 1 }], [{ a: 2 }], null), true);
});

test('a selected row that is gone leaves nothing selected', () => {
    assert.equal(keepSelection([{ number: 1 }], 5, byNumber), null);
    assert.equal(keepSelection([{ number: 1 }], null, byNumber), null);
    assert.equal(keepSelection([{ number: 1 }], 1, null), null);
});
