import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRelay } from '../relay.js';

const publicDir = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'public');
let tomcat;
let relay;
let base;
let seen;

const listen = server => new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
const close = server => new Promise(resolve => {
    server.close(resolve);
    server.closeAllConnections();
});

/** A request sent exactly as written, without the URL normalising fetch would do. */
function raw(method, requestPath) {
    return new Promise((resolve, reject) => {
        const request = http.request({ host: '127.0.0.1', port: relay.address().port, method, path: requestPath }, reply => {
            let body = '';
            reply.on('data', chunk => { body += chunk; });
            reply.on('end', () => resolve({ status: reply.statusCode, body }));
        });
        request.on('error', reject);
        request.end();
    });
}

before(async () => {
    // A stand-in for Tomcat that remembers what reached it.
    tomcat = http.createServer((request, response) => {
        let body = '';
        request.on('data', chunk => { body += chunk; });
        request.on('end', () => {
            if (request.url.startsWith('/guess-market/broken')) {
                // An answer that breaks off half way: it promises 100 bytes and the connection dies.
                response.writeHead(200, { 'Content-Type': 'application/json', 'Content-Length': '100' });
                response.write('{"partial":');
                setTimeout(() => request.socket.destroy(), 20);
                return;
            }
            seen = { method: request.method, url: request.url, body, cookie: request.headers.cookie };
            response.writeHead(201, { 'Content-Type': 'application/json', 'Set-Cookie': 'JSESSIONID=abc; Path=/guess-market; HttpOnly' });
            response.end('{"ok":true}');
        });
    });
    await listen(tomcat);
    relay = createRelay({ server: `http://127.0.0.1:${tomcat.address().port}/guess-market`, publicDir });
    await listen(relay);
    base = `http://127.0.0.1:${relay.address().port}`;
});

after(async () => {
    await close(relay);
    await close(tomcat);
});

test('the API passes through untouched: method, path, query, body, cookie, status and Set-Cookie', async () => {
    const response = await fetch(`${base}/guess-market/chat?x=1`, {
        method: 'POST', body: 'message=hi', headers: { Cookie: 'JSESSIONID=abc', 'Content-Type': 'application/x-www-form-urlencoded' },
    });
    assert.deepEqual(seen, { method: 'POST', url: '/guess-market/chat?x=1', body: 'message=hi', cookie: 'JSESSIONID=abc' });
    assert.equal(response.status, 201);
    assert.match(response.headers.get('set-cookie'), /Path=\/guess-market/);
    assert.deepEqual(await response.json(), { ok: true });
});

test('the page and its modules are served with their types', async () => {
    const page = await fetch(`${base}/`);
    assert.equal(page.status, 200);
    assert.match(page.headers.get('content-type'), /^text\/html/);
    assert.match(await page.text(), /<title>Guess Market<\/title>/);
    assert.equal(page.headers.get('cache-control'), 'no-store');
    const module = await fetch(`${base}/js/format.js`);
    assert.match(module.headers.get('content-type'), /^text\/javascript/);
});

test('the page is told where the API is', async () => {
    const config = await (await fetch(`${base}/config.json`)).json();
    assert.deepEqual(config, { apiBase: '/guess-market', server: `http://127.0.0.1:${tomcat.address().port}/guess-market` });
});

test('nothing outside the public folder is ever served', async () => {
    for (const sneaky of ['/../relay.js', '/%2e%2e/relay.js', '/..%2frelay.js', '/..%5crelay.js',
        '/js/../../serve.js', '/%00', '/%E0%A4%A', '/js/']) {
        assert.equal((await raw('GET', sneaky)).status, 404, sneaky);
    }
});

// Tomcat folds "..", its encoded forms and ";" path parameters into other applications (its manager,
// for one); only Guess Market's own API is the relay's to pass on.
test('a path that climbs out of the API is never passed to Tomcat', async () => {
    for (const sneaky of ['/guess-market/../manager/html', '/guess-market/%2e%2e/manager/html',
        '/guess-market/..;/manager/html', '/guess-market/.%2e/manager/html', '/guess-market/events;x=1',
        '/guess-market/./events', '/guess-market/..%5cmanager']) {
        seen = null;
        assert.equal((await raw('GET', sneaky)).status, 404, sneaky);
        assert.equal(seen, null, sneaky);
    }
    seen = null;
    await raw('GET', '/guess-market/events?after=..');
    assert.equal(seen?.url, '/guess-market/events?after=..');
});

test('a path that only starts like the API is not passed through', async () => {
    seen = null;
    assert.equal((await raw('GET', '/guess-marketing')).status, 404);
    assert.equal(seen, null);
});

test('an answer Tomcat breaks off is broken off for the browser too, not left hanging', { timeout: 3000 }, async () => {
    const ended = await new Promise(resolve => {
        const request = http.get({ host: '127.0.0.1', port: relay.address().port, path: '/guess-market/broken' }, reply => {
            reply.on('data', () => {});
            reply.on('end', () => resolve('ended'));
            reply.on('error', () => resolve('cut'));
            reply.on('aborted', () => resolve('cut'));
        });
        request.on('error', () => resolve('cut'));
    });
    assert.equal(ended, 'cut');
});

test('only reading is allowed outside the API', async () => {
    assert.equal((await raw('POST', '/')).status, 405);
});

test('a Tomcat that does not answer gives 502 and a sentence', async () => {
    const nobody = http.createServer();
    await listen(nobody);
    const deadPort = nobody.address().port;
    await close(nobody);
    const orphan = createRelay({ server: `http://127.0.0.1:${deadPort}/guess-market`, publicDir });
    await listen(orphan);
    try {
        const response = await fetch(`http://127.0.0.1:${orphan.address().port}/guess-market/events`);
        assert.equal(response.status, 502);
        const error = await response.json();
        assert.match(error.message, /^Cannot reach the Guess Market server at http:\/\/127\.0\.0\.1:\d+\/guess-market\./);
        assert.deepEqual(error.problems, []);
    } finally {
        await close(orphan);
    }
});
