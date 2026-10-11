import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createApi, failureOf, Failure, UNREACHABLE } from '../public/js/api.js';

/** A fetch that records every call and answers from a list. */
function fakeFetch(...answers) {
    const calls = [];
    async function fetchImpl(url, init) {
        calls.push({ url, init });
        const next = answers.shift();
        if (next instanceof Error) {
            throw next;
        }
        return { ok: next.status >= 200 && next.status < 300, status: next.status, text: async () => next.body ?? '' };
    }
    return { calls, fetchImpl };
}

test('logging in sends the name, Hebrew and symbols included, as the JavaFX client does', async () => {
    const { calls, fetchImpl } = fakeFetch({ status: 200, body: '{"name":"אבי & co"}' });
    const me = await createApi('/guess-market', fetchImpl).login('אבי & co');
    assert.equal(calls[0].url, '/guess-market/login?username=%D7%90%D7%91%D7%99+%26+co');
    assert.equal(calls[0].init.method, 'POST');
    assert.equal(me.name, 'אבי & co');
});

test('every parameter of an order goes in the query, in the JavaFX client order', async () => {
    const { calls, fetchImpl } = fakeFetch({ status: 200, body: '[]' });
    await createApi('/guess-market', fetchImpl).order(3, 2, 'sell', 10, 0.55);
    assert.equal(calls[0].url, '/guess-market/trade/order?event=3&option=2&side=sell&quantity=10&price=0.55');
    assert.equal(calls[0].init.method, 'POST');
});

test('reads go out as GET with their numbers', async () => {
    const { calls, fetchImpl } = fakeFetch({ status: 200, body: '{"after":4,"lines":[]}' }, { status: 200, body: '{}' });
    const api = createApi('/guess-market', fetchImpl);
    await api.ledger(4);
    await api.orderBook(7);
    assert.deepEqual(calls.map(call => [call.init.method, call.url]),
        [['GET', '/guess-market/account/ledger?after=4'], ['GET', '/guess-market/events/book?event=7']]);
});

test('a chat message travels as a form, so its text is never part of an address', async () => {
    const { calls, fetchImpl } = fakeFetch({ status: 200, body: '{}' });
    await createApi('/guess-market', fetchImpl).say('שלום <b>');
    assert.equal(calls[0].url, '/guess-market/chat');
    assert.ok(calls[0].init.body instanceof URLSearchParams);
    assert.equal(calls[0].init.body.get('message'), 'שלום <b>');
});

test('a refusal carries the server own explanation', async () => {
    const { fetchImpl } = fakeFetch({ status: 400, body: '{"message":"Load more funds first.","problems":["a","b"]}' });
    const failure = await createApi('/gm', fetchImpl).open(1).catch(caught => caught);
    assert.ok(failure instanceof Failure);
    assert.equal(failure.message, 'Load more funds first.');
    assert.deepEqual(failure.problems, ['a', 'b']);
    assert.equal(failure.sessionLost, false);
    assert.equal(failure.unreachable, false);
});

test('401 means the session is gone, 502 and a dead connection mean the server cannot be reached', async () => {
    const { fetchImpl } = fakeFetch(
        { status: 401, body: '{"message":"Please log in first.","problems":[]}' },
        { status: 502, body: '{"message":"Cannot reach the Guess Market server at x.","problems":[]}' },
        new TypeError('fetch failed'));
    const api = createApi('/gm', fetchImpl);
    const lost = await api.events().catch(caught => caught);
    const down = await api.events().catch(caught => caught);
    const gone = await api.events().catch(caught => caught);
    assert.equal(lost.sessionLost, true);
    assert.equal(down.unreachable, true);
    assert.equal(down.message, 'Cannot reach the Guess Market server at x.');
    assert.equal(gone.unreachable, true);
    assert.equal(gone.status, UNREACHABLE);
    assert.match(gone.message, /start\.bat/);
});

// The feed sends one request of each kind at a time, so one that never ends would freeze that kind
// for good. The JavaFX client's OkHttp gives up after ten seconds; so does the page.
test('a request that gets no answer gives up, as unreachable', { timeout: 3000 }, async () => {
    const silent = (url, init) => new Promise((resolve, reject) => {
        init.signal?.addEventListener('abort', () => reject(init.signal.reason));
    });
    const failure = await createApi('/gm', silent, { timeoutMs: 50 }).events().catch(caught => caught);
    assert.ok(failure instanceof Failure);
    assert.equal(failure.unreachable, true);
    assert.match(failure.message, /did not answer/);
});

test('an answer that is not ours is described by its status', () => {
    assert.equal(failureOf(500, '<html>oops</html>').message, 'The server answered with status 500.');
    assert.equal(failureOf(404, '').message, 'The server answered with status 404.');
});

test('the logout address is known for the beacon', () => {
    assert.equal(createApi('/guess-market', fakeFetch().fetchImpl).url('logout'), '/guess-market/logout');
});
