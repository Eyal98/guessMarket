/**
 * Starts the web client: node serve.js [server-address] [port] [--open]
 *
 *   server-address  where Tomcat serves Guess Market; http://localhost:8080/guess-market by default,
 *                   which the exercise allows to be assumed
 *   port            where the page is served; 3000 by default
 *   --open          opens the browser at the page once it is ready (start.bat passes it)
 */
import { exec } from 'node:child_process';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRelay } from './relay.js';

const DEFAULT_SERVER = 'http://localhost:8080/guess-market';
const DEFAULT_PORT = '3000';
const LOOPBACK = '127.0.0.1';

const given = process.argv.slice(2);
const open = given.includes('--open');
const [serverText = DEFAULT_SERVER, portText = DEFAULT_PORT] = given.filter(argument => argument !== '--open');

const server = checkedServer(serverText);
const port = checkedPort(portText);
const here = path.dirname(fileURLToPath(import.meta.url));
const relay = createRelay({ server, publicDir: path.join(here, 'public') });

relay.on('error', error => {
    if (error.code === 'EADDRINUSE') {
        stop(`Port ${port} is already in use, perhaps by another copy of the web client. Close it, or give`
            + ` another port: start.bat ${server} 3001`);
    }
    stop(`The web client could not start: ${error.message}`);
});

// This computer only: the page is opened here, and listening on every interface would put the market
// on the local network and make Windows ask whether node.exe may pass the firewall.
relay.listen(port, LOOPBACK, () => {
    const page = `http://localhost:${port}/`;
    console.log('Guess Market web client');
    console.log(`  Open:    ${page}`);
    console.log(`  Server:  ${server}`);
    console.log('  Close this window to stop it.');
    checkTheServer();
    if (open) {
        openBrowser(page);
    }
});

function checkedServer(text) {
    let url;
    try {
        url = new URL(text);
    } catch {
        stop(`"${text}" is not a server address. Give it like this: ${DEFAULT_SERVER}`);
    }
    const context = url.pathname.replace(/\/+$/, '');
    if (url.protocol !== 'http:' || context === '') {
        stop(`"${text}" is not a server address. Give it like this: ${DEFAULT_SERVER}`);
    }
    return url.origin + context;
}

function checkedPort(text) {
    const number = Number(text);
    if (!/^\d+$/.test(text) || number < 1 || number > 65535) {
        stop(`"${text}" is not a port number. Give a number such as 3000.`);
    }
    return number;
}

/** Says at once whether Tomcat is there, so a checker is not left wondering why nothing loads. */
function checkTheServer() {
    http.get(`${server}/`, { autoSelectFamily: true }, reply => {
        reply.resume();
        if (reply.statusCode === 404) {
            console.log(`  Note: Tomcat answers, but Guess Market is not deployed at ${server}.`);
        }
    }).on('error', () => {
        console.log(`  Note: nothing answers at ${server} yet. Start Tomcat with guess-market.war in its`
            + ' webapps folder; the page connects as soon as it is up.');
    });
}

function openBrowser(page) {
    if (process.platform === 'win32') {
        exec(`start "" "${page}"`);
    } else if (process.platform === 'darwin') {
        exec(`open "${page}"`);
    } else {
        exec(`xdg-open "${page}"`);
    }
}

function stop(message) {
    console.error(message);
    process.exit(1);
}
