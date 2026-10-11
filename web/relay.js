/**
 * The small server the web client runs on. It does two things and knows nothing of the market:
 *
 *  - serves the page — the files under public/ and nothing else;
 *  - passes every request under the server's context path (/guess-market) to Tomcat as it is, and
 *    Tomcat's answer back as it is, Set-Cookie included.
 *
 * Why it exists: a browser sends a site's cookie only to the address its page came from, and a page
 * from anywhere but Tomcat could call Tomcat only if the server added CORS headers — a change to the
 * server, which exercise 4 does not allow. Through the relay the page and the API share one address,
 * Tomcat's own session cookie works untouched, and the server stays exactly as exercise 3 left it.
 */
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { pipeline } from 'node:stream';

const TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.svg': 'image/svg+xml',
    '.png': 'image/png',
    '.ico': 'image/x-icon',
};

/** Headers that describe one connection, not the message, and so are never passed on. */
const HOP_BY_HOP = new Set(['connection', 'keep-alive', 'proxy-authenticate', 'proxy-authorization',
    'proxy-connection', 'te', 'trailer', 'transfer-encoding', 'upgrade']);

export function createRelay({ server, publicDir }) {
    const target = new URL(server);
    const apiBase = target.pathname.replace(/\/+$/, '');
    const root = path.resolve(publicDir);

    return http.createServer((request, response) => {
        const pathname = request.url.split('?')[0];
        if (pathname === apiBase || pathname.startsWith(`${apiBase}/`)) {
            if (staysInside(pathname)) {
                pass(request, response);
            } else {
                notFound(response);
            }
        } else if (request.method !== 'GET' && request.method !== 'HEAD') {
            json(response, 405, { message: "Only the server's API accepts anything but reading.", problems: [] });
        } else if (pathname === '/config.json') {
            json(response, 200, { apiBase, server: target.origin + apiBase });
        } else {
            serveFile(pathname, request, response);
        }
    });

    function pass(request, response) {
        const upstream = http.request({
            hostname: target.hostname.replace(/^\[|\]$/g, ''),
            port: target.port || 80,
            method: request.method,
            path: request.url,
            headers: { ...withoutHopByHop(request.headers), host: target.host },
            autoSelectFamily: true,
        }, reply => {
            response.writeHead(reply.statusCode, withoutHopByHop(reply.headers));
            // An answer Tomcat breaks off is broken off for the browser too; a plain pipe would leave
            // the browser waiting for the rest for ever.
            pipeline(reply, response, broken => {
                if (broken) {
                    response.destroy();
                }
            });
        });
        upstream.on('error', () => {
            if (response.headersSent) {
                response.destroy();
                return;
            }
            json(response, 502, {
                message: `Cannot reach the Guess Market server at ${server}. Please check that Tomcat is running`
                    + ' with guess-market.war deployed.',
                problems: [],
            });
        });
        request.pipe(upstream);
    }

    function serveFile(pathname, request, response) {
        let wanted;
        try {
            wanted = decodeURIComponent(pathname);
        } catch {
            notFound(response);
            return;
        }
        if (wanted === '/') {
            wanted = '/index.html';
        }
        const file = path.resolve(root, `.${wanted}`);
        if (!file.startsWith(root + path.sep) || wanted.includes('\0')) {
            notFound(response);
            return;
        }
        fs.stat(file, (error, stats) => {
            if (error || !stats.isFile()) {
                notFound(response);
                return;
            }
            response.writeHead(200, {
                'Content-Type': TYPES[path.extname(file).toLowerCase()] ?? 'application/octet-stream',
                'Content-Length': stats.size,
                'Cache-Control': 'no-store',
                'X-Content-Type-Options': 'nosniff',
            });
            if (request.method === 'HEAD') {
                response.end();
                return;
            }
            fs.createReadStream(file).on('error', () => response.destroy()).pipe(response);
        });
    }
}

/**
 * Whether a path under the context path stays there. Tomcat folds "." and ".." segments — written
 * plainly or percent-encoded — and drops ";" path parameters, so "/guess-market/..;/manager" would
 * reach another application; the API never needs any of them.
 */
function staysInside(pathname) {
    let decoded;
    try {
        decoded = decodeURIComponent(pathname);
    } catch {
        return false;
    }
    if (/[;\\\0]/.test(decoded)) {
        return false;
    }
    return !decoded.split('/').some(segment => segment === '.' || segment === '..');
}

function withoutHopByHop(headers) {
    return Object.fromEntries(Object.entries(headers).filter(([name]) => !HOP_BY_HOP.has(name.toLowerCase())));
}

function notFound(response) {
    json(response, 404, { message: 'There is nothing here.', problems: [] });
}

function json(response, status, body) {
    const text = JSON.stringify(body);
    response.writeHead(status, {
        'Content-Type': 'application/json; charset=utf-8',
        'Content-Length': Buffer.byteLength(text),
        'Cache-Control': 'no-store',
    });
    response.end(text);
}
