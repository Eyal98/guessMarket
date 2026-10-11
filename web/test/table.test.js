import { test } from 'node:test';
import assert from 'node:assert/strict';
import { keepSelection } from '../public/js/table.js';

const byNumber = event => event.number;

test('the selected row is found again in fresh data by its key, not by identity', () => {
    const fresh = [{ number: 1, status: 'Active' }, { number: 2, status: 'Closed' }];
    assert.deepEqual(keepSelection(fresh, 2, byNumber), { number: 2, status: 'Closed' });
});

test('a selected row that is gone leaves nothing selected', () => {
    assert.equal(keepSelection([{ number: 1 }], 5, byNumber), null);
    assert.equal(keepSelection([{ number: 1 }], null, byNumber), null);
    assert.equal(keepSelection([{ number: 1 }], 1, null), null);
});
