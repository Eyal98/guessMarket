import { test } from 'node:test';
import assert from 'node:assert/strict';
import { maySilentlyReenter, reentryRefusal } from '../public/js/session.js';
import { Failure } from '../public/js/api.js';

// A reload sends the page's own logout beacon, which may reach the server just after the reloaded page
// logged back in. That race is the one reason to log back in without asking, so it is allowed only
// before the page has ever had a working session, and only once.
test('only a fresh page logs back in by itself, and only once', () => {
    assert.equal(maySilentlyReenter({ remembered: 'Dana', alreadyTried: false, settled: false }), true);
    assert.equal(maySilentlyReenter({ remembered: 'Dana', alreadyTried: true, settled: false }), false);
    assert.equal(maySilentlyReenter({ remembered: 'Dana', alreadyTried: false, settled: true }), false);
    assert.equal(maySilentlyReenter({ remembered: null, alreadyTried: false, settled: false }), false);
});

test('a failed silent login tells the real reason', () => {
    const ended = 'The server no longer knows this session; it may have been restarted. Please log in again.';
    const taken = new Failure(409, 'Somebody is already logged in as "Dana". Please choose another name.');
    assert.equal(reentryRefusal(taken, ended), taken.message);
    assert.equal(reentryRefusal(new Failure(502, 'Cannot reach the Guess Market server at x.'), ended), 'Cannot reach the Guess Market server at x.');
    assert.equal(reentryRefusal(new Failure(401, 'You are not logged in.'), ended), ended);
});
