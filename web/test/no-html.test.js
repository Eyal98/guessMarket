import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scripts = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'public', 'js');

// Event names, ledger lines and chat messages are written by people. Shown as HTML, an event called
// <img src=x onerror=...> would run in every viewer's browser; shown as text it is only a strange name.
test('no script ever turns text into HTML', () => {
    const files = fs.readdirSync(scripts).filter(name => name.endsWith('.js'));
    assert.ok(files.length >= 5);
    for (const name of files) {
        const source = fs.readFileSync(path.join(scripts, name), 'utf8');
        assert.doesNotMatch(source, /\b(innerHTML|outerHTML|insertAdjacentHTML|document\.write)\b/, name);
    }
});
