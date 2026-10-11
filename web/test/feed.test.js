import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createFeed } from '../public/js/feed.js';
import { Failure } from '../public/js/api.js';

const settle = () => new Promise(resolve => setTimeout(resolve, 0));
const quietTimers = { setInterval: () => 1, clearInterval: () => {} };

/** An API whose every answer is fixed unless a test replaces it. */
function fakeApi(overrides = {}) {
    return {
        events: async () => [], users: async () => [], account: async () => ({ name: 'Avi', blocked: false }),
        ledger: async after => ({ after, lines: [] }), chat: async after => ({ after, lines: [] }), ...overrides,
    };
}

test('a value is published only when it changed', async () => {
    let answer = [{ number: 1 }];
    const feed = createFeed(fakeApi({ events: async () => answer }), { timers: quietTimers });
    const told = [];
    feed.on('events', now => told.push(now));
    feed.pullEverything(); await settle();
    feed.pullEverything(); await settle();
    answer = [{ number: 1 }, { number: 2 }];
    feed.pullEverything(); await settle();
    assert.equal(told.length, 2);
    assert.deepEqual(feed.events, answer);
});

test('one request of a kind at a time; rounds that come meanwhile become one more', async () => {
    let calls = 0;
    let release;
    const feed = createFeed(fakeApi({ events: () => { calls += 1; return new Promise(resolve => { release = resolve; }); } }), { timers: quietTimers });
    feed.pullEverything(); feed.pullEverything(); feed.pullEverything();
    assert.equal(calls, 1);
    release([]); await settle();
    assert.equal(calls, 2);
    release([]); await settle();
    assert.equal(calls, 2);
});

test('ledger lines are added in order, and a stale answer is dropped', async () => {
    const answers = [
        { after: 0, lines: [{ number: 1 }, { number: 2 }] },
        { after: 0, lines: [{ number: 1 }, { number: 2 }] },
        { after: 2, lines: [{ number: 3 }] },
    ];
    const asked = [];
    const feed = createFeed(fakeApi({ ledger: async after => { asked.push(after); return answers.shift(); } }), { timers: quietTimers });
    const added = [];
    feed.on('ledger', (all, fresh) => added.push(fresh.map(line => line.number)));
    for (let round = 0; round < 3; round += 1) {
        feed.pullEverything(); await settle();
    }
    assert.deepEqual(asked, [0, 2, 2]);
    assert.deepEqual(feed.ledger.map(line => line.number), [1, 2, 3]);
    assert.deepEqual(added, [[1, 2], [3]]);
});

test('a lost session stops the feed and is reported once', async () => {
    const lost = async () => { throw new Failure(401, 'Please log in first.'); };
    let reports = 0;
    let cleared = 0;
    const feed = createFeed(fakeApi({ events: lost, users: lost, account: lost }), {
        onSessionLost: () => { reports += 1; },
        timers: { setInterval: () => 7, clearInterval: id => { cleared += id === 7 ? 1 : 0; } },
    });
    feed.start(); await settle();
    feed.pullEverything(); await settle();
    assert.equal(reports, 1);
    assert.equal(cleared, 1);
});

test('the server going away and coming back is told once each way', async () => {
    let down = true;
    const events = async () => { if (down) throw new Failure(502, 'Cannot reach the Guess Market server.'); return []; };
    const feed = createFeed(fakeApi({ events }), { timers: quietTimers });
    const told = [];
    feed.on('reachable', (ok, failure) => told.push([ok, failure?.message]));
    feed.pullEverything(); await settle();
    feed.pullEverything(); await settle();
    down = false;
    feed.pullEverything(); await settle();
    assert.deepEqual(told, [[false, 'Cannot reach the Guess Market server.'], [true, undefined]]);
});

test('a pull added from outside runs with the rest', async () => {
    const feed = createFeed(fakeApi(), { timers: quietTimers });
    let ran = 0;
    feed.every(async () => { ran += 1; });
    feed.pullEverything(); await settle();
    assert.equal(ran, 1);
});
