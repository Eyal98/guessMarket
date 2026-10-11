import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import http from 'node:http';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const serveJs = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'serve.js');

const freePort = () => new Promise(resolve => {
    const probe = http.createServer().listen(0, '127.0.0.1', () => {
        const { port } = probe.address();
        probe.close(() => resolve(port));
    });
});

const reaches = (host, port) => new Promise(resolve => {
    const socket = net.connect({ host, port });
    socket.once('connect', () => { socket.destroy(); resolve(true); });
    socket.once('error', () => resolve(false));
});

/** This computer's own address on its network, if it has one. */
const lanAddress = () => Object.values(os.networkInterfaces()).flat()
    .find(nic => nic && nic.family === 'IPv4' && !nic.internal)?.address;

// Listening on every interface would put the market on the local network and make Windows ask the
// checker to allow node.exe through the firewall; the page is only ever opened on this computer.
test('the web client answers on this computer only', { timeout: 10000 }, async t => {
    const port = await freePort();
    const relay = spawn(process.execPath, [serveJs, 'http://127.0.0.1:1/guess-market', String(port)]);
    try {
        await new Promise((resolve, reject) => {
            relay.stdout.on('data', chunk => { if (String(chunk).includes('Open:')) resolve(); });
            relay.once('exit', code => reject(new Error(`serve.js stopped with ${code}`)));
        });
        assert.equal(await reaches('127.0.0.1', port), true);
        const lan = lanAddress();
        if (!lan) {
            t.skip('this computer has no network address to try');
            return;
        }
        assert.equal(await reaches(lan, port), false, `the web client answered on ${lan}`);
    } finally {
        relay.kill();
    }
});
