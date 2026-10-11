# Web Client (Exercise 4) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A browser client for the unchanged exercise 3 server — login, events, account and chat — started by one bat and packaged as its own zip.

**Architecture:** Plain ES-module JavaScript in `web/public/`, served together with the server's API by a zero-dependency Node relay (`web/relay.js`, run by `web/serve.js`) so that, to the browser, page and API share one origin and Tomcat's session cookie works untouched. Pure decisions (formatting, rules, the pull feed, the HTTP wrapper) live apart from the DOM and are tested with Node's own `node:test`; the screens mirror the JavaFX controllers one to one.

**Tech Stack:** HTML, CSS, JavaScript (ES2022 modules, no framework, no build), Node.js ≥ 18 built-ins only (`http`, `fs`, `path`, `url`, `child_process`, `node:test`), the exercise 3 Tomcat WAR.

**Spec:** `docs/superpowers/specs/2026-10-11-web-client-design.md`

## Global Constraints

- No change to the server, engine, DTOs, JavaFX client, `build.bat`, `package.bat`, `README.md` or the `ex3-submission` tag. Everything new is under `web/`, plus `package-web.bat`, a step in `test.bat`, optional arguments in `docs/make-readme-docx.py`, one line in `.gitignore`.
- No npm dependencies: `web/package.json` exists only to declare `"type": "module"`; nothing is ever installed.
- The page talks only to its own origin: `/config.json` and `<apiBase>/…` (default `/guess-market`).
- Defaults: server `http://localhost:8080/guess-market`, page `http://localhost:3000/`.
- Pull period 500 ms; at most one request per kind in flight; publish only when the JSON changed.
- Every piece of server text reaches the DOM through `textContent` / text nodes — never `innerHTML`, `outerHTML`, `insertAdjacentHTML` or `document.write`.
- Captions and messages are the JavaFX client's own wording (quoted in each task).
- Money is written as `Format.java` writes it: two decimals, half-up, |x| < 0.005 shown as `0.00`; shares with `,` thousands separators.
- Commit messages carry **no** Co-Authored-By trailer. Nothing is pushed or tagged without Eyal's approval. Eyal's identity number never enters a tracked file.
- On this machine port 8080 is qBittorrent: never stop it. Local Tomcat: `tools/tomcat` (11.0.26) on 8081. Java is not on PATH: `C:\Program Files\Java\jdk-25.0.4`.

## Review Focus

1. Server text with markup or quotes (an event named `<img src=x onerror=alert(1)>`, a chat line `<b>hi</b> & "you"`) must appear literally, never as HTML — pinned by `web/test/no-html.test.js` (Task 6).
2. Hebrew and symbols in a user name or chat message must reach Tomcat intact (`אבי & co`) — pinned in `web/test/api.test.js` (Task 3).
3. Tomcat stopped or restarted while a user is logged in: a banner, no crash, recovery when it returns — pinned by the relay's 502 test (Task 5), `Failure.unreachable` (Task 3) and the feed's reachable signal (Task 4).
4. A refresh arriving while the user is typing a quantity or price must not wipe it — pinned by the `tradeSituation` stability test (Task 2) and the fixed placement of the trade panel (Task 7).
5. Malformed numbers (`""`, `abc`, `0`, `1.5` shares, `0.5.5`) must give the JavaFX-style sentence and send nothing — pinned by the `parse*` tests (Task 2).

---

## File Structure

```
web/
  package.json            {"type":"module"}; scripts only, no dependencies
  relay.js                createRelay(): static files + pass-through to Tomcat
  serve.js                the runnable relay: arguments, port, opening the browser
  start.bat               what the checker runs
  README.md               the exercise 4 readme (Hebrew); docs/readme-web.docx is made from it
  public/
    index.html            the one page
    css/guess-market.css  the JavaFX colours as CSS
    js/format.js          money, shares, percent, time
    js/market.js          pure rules: filters, roles, situation key, messages, input parsing
    js/api.js             Failure, createApi(): one function per endpoint
    js/feed.js            createFeed(): the 500 ms pulls
    js/dom.js             el(), section(), wrapping(), labelled(), row(), label(), tabbed()
    js/table.js           keepSelection(), createTable(), staticTable(), col(), num()
    js/shell.js           top bar, banner, status line, warning dialog (the Messenger)
    js/session.js         remember / forget the name; logout beacon
    js/login.js           the login screen
    js/trade.js           the trade panel (used by events and account)
    js/event-detail.js    the selected event, LMSR or order book; tradeTable()
    js/events.js          the events tab
    js/account.js         the account tab
    js/chat.js            the chat tab
    js/app.js             start-up, login ↔ market, session loss
  test/
    format.test.js  market.test.js  api.test.js  feed.test.js
    table.test.js   no-html.test.js relay.test.js
package-web.bat           builds dist\guess-market-web.zip
```

---

### Task 1: Project skeleton and number formatting

**Files:**
- Create: `web/package.json`, `web/public/js/format.js`
- Test: `web/test/format.test.js`

**Interfaces:**
- Produces: `NOTHING: string`, `money(amount: number): string`, `signedMoney(amount: number): string`, `moneyOrNothing(amount: number|null|undefined): string`, `shares(count: number): string`, `percent(value: number): string`, `time(epochMillis: number): string` (local `HH:mm:ss`).

- [ ] **Step 1: Create `web/package.json`**

```json
{
  "name": "guess-market-web",
  "private": true,
  "type": "module",
  "description": "The web client of Guess Market: static pages and a relay to the Tomcat server. It has no dependencies; nothing is ever installed.",
  "scripts": {
    "start": "node serve.js",
    "test": "node --test"
  }
}
```

- [ ] **Step 2: Write the failing test `web/test/format.test.js`**

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { NOTHING, money, signedMoney, moneyOrNothing, shares, percent, time } from '../public/js/format.js';

// Every expected string below was printed by String.format(Locale.US, "%.2f", x) in Java 25, after
// Format.java's own rule that anything smaller than half a penny is shown as zero.
test('money is written exactly as the JavaFX client writes it', () => {
    const javaSays = [
        [0, '0.00'], [5.8, '5.80'], [-5.8, '-5.80'], [1.005, '1.01'], [0.125, '0.13'], [2.675, '2.68'],
        [-1.005, '-1.01'], [1234567.891, '1234567.89'], [0.004, '0.00'], [-0.004, '0.00'], [0.005, '0.01'],
        [62.006, '62.01'], [0.615, '0.62'], [10.555, '10.56'], [1.0049999, '1.00'],
    ];
    for (const [amount, expected] of javaSays) {
        assert.equal(money(amount), expected, `money(${amount})`);
    }
});

test('a signed amount always shows its sign, except nought', () => {
    assert.equal(signedMoney(5.8), '+5.80');
    assert.equal(signedMoney(-5.8), '-5.80');
    assert.equal(signedMoney(0), '0.00');
    assert.equal(signedMoney(-0.001), '0.00');
});

test('a price the market cannot give is a dash', () => {
    assert.equal(moneyOrNothing(null), NOTHING);
    assert.equal(moneyOrNothing(undefined), NOTHING);
    assert.equal(moneyOrNothing(0.5), '0.50');
    assert.equal(NOTHING, '—');
});

test('shares carry thousands separators, as %,d does', () => {
    assert.equal(shares(0), '0');
    assert.equal(shares(1234567), '1,234,567');
    assert.equal(shares(-1500), '-1,500');
});

test('percent and time', () => {
    assert.equal(percent(3), '3%');
    assert.equal(time(new Date(2026, 9, 11, 14, 3, 9).getTime()), '14:03:09');
    assert.equal(time(new Date(2026, 9, 11, 0, 0, 0).getTime()), '00:00:00');
});
```

- [ ] **Step 3: Run it to see it fail**

Run: `cd web && node --test test/format.test.js`
Expected: FAIL — `Cannot find module '…/public/js/format.js'`.

- [ ] **Step 4: Write `web/public/js/format.js`**

```js
/**
 * How numbers are written on screen: the same strings as the JavaFX client's Format.java, so the two
 * clients never disagree about a price.
 *
 * A price the market cannot supply is shown as a dash rather than a nought. A book with nobody
 * selling has no ask and therefore no spread, and writing 0.00 there would claim something untrue.
 */

/** What is shown where the market has no answer. */
export const NOTHING = '—';

/** Anything smaller than half a penny is shown as zero, so no total ever reads "-0.00". */
const ROUNDING_NOISE = 0.005;

/**
 * An amount of money, to two decimal places.
 *
 * Java's %.2f rounds the shortest decimal form of a number half up, so 1.005 is "1.01"; JavaScript's
 * toFixed rounds the binary value, which is a hair under 1.005, and says "1.00". Shifting the decimal
 * point in the text of the number, rather than multiplying, keeps the rounding on the digits a person
 * sees, which is what Java does.
 */
export function money(amount) {
    if (Math.abs(amount) < ROUNDING_NOISE) {
        return '0.00';
    }
    const sign = amount < 0 ? -1 : 1;
    const cents = Math.round(Number(`${Math.abs(amount)}e2`));
    return Number.isFinite(cents) ? (sign * cents / 100).toFixed(2) : amount.toFixed(2);
}

/** An amount that came in or went out, with its sign always shown: +5.80 received, -5.80 paid. */
export function signedMoney(amount) {
    const shown = money(amount);
    return shown.startsWith('-') || shown === money(0) ? shown : `+${shown}`;
}

/** An amount the market may not be able to give, shown as a dash when it cannot. */
export function moneyOrNothing(amount) {
    return amount === null || amount === undefined ? NOTHING : money(amount);
}

/** A whole number of shares. */
export function shares(count) {
    return Number(count).toLocaleString('en-US');
}

/** A percentage as it is written in the events file. */
export function percent(value) {
    return `${value}%`;
}

/** The time of day a chat line was sent, on this computer's clock. */
export function time(epochMillis) {
    const at = new Date(epochMillis);
    return [at.getHours(), at.getMinutes(), at.getSeconds()].map(part => String(part).padStart(2, '0')).join(':');
}
```

- [ ] **Step 5: Run it to see it pass**

Run: `cd web && node --test test/format.test.js`
Expected: PASS, 5 tests.

- [ ] **Step 6: Commit**

```bash
git add web/package.json web/public/js/format.js web/test/format.test.js
git commit -m "Start the web client with the JavaFX client's way of writing numbers"
```

---

### Task 2: The market's rules, apart from any screen

**Files:**
- Create: `web/public/js/market.js`
- Test: `web/test/market.test.js`

**Interfaces:**
- Consumes: `format.js` (Task 1).
- Produces (all pure):
  - `ORDER_BOOK = 'Order book'`, `ALL = 'All'`, `FILTERS: {caption, field, choices}[]`
  - `passesFilters(event, chosen: {methodKind, status, commissionType}): boolean`
  - `eventsSummary(shownCount, total): string`, `commissionOf(event): string`
  - `rolesOf(me, events): {event, runsIt, holding}[]`, `roleName(role): string`, `holdingSummary(role): string`
  - `othersThan(users, me): UserDto[]`, `justBlocked(was, now): boolean`
  - `tradeSituation(event|null, me|null): string|null`
  - `describeOpening(event)`, `describeClosing(event)`, `describePurchase(result)`, `describeSale(result)`, `describeOrder(trades)`, `describeDeposit(amount, me)`: `string`
  - `parseQuantity(text)`, `parsePrice(text)`, `parseAmount(text)`: `{value: number} | {problem: string}`
  - `chatLineText(line): string`

- [ ] **Step 1: Write the failing test `web/test/market.test.js`**

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
    ALL, passesFilters, eventsSummary, commissionOf, rolesOf, roleName, holdingSummary, othersThan,
    justBlocked, tradeSituation, describeOpening, describeClosing, describePurchase, describeSale,
    describeOrder, describeDeposit, parseQuantity, parsePrice, parseAmount, chatLineText,
} from '../public/js/market.js';

const event = (fields = {}) => ({
    number: 1, name: 'Rain', description: 'Will it rain?', commissionPercent: 3, commissionType: 'on-purchase',
    commissionTiming: 'charged on every purchase', optionNames: ['Yes', 'No'], status: 'Active',
    tradingMethod: 'LMSR (b = 100)', methodKind: 'LMSR', marketMakerName: 'Dana', accountBalance: 69.31,
    openingCost: 69.31, winningOptionName: null, ...fields,
});
const me = (fields = {}) => ({ name: 'Avi', balance: 100, blocked: false, marketMakerOf: [], participations: [], ...fields });
const everything = { methodKind: ALL, status: ALL, commissionType: ALL };

test('filters pass an event only when every chosen group agrees', () => {
    assert.equal(passesFilters(event(), everything), true);
    assert.equal(passesFilters(event(), { ...everything, methodKind: 'Order book' }), false);
    assert.equal(passesFilters(event(), { ...everything, status: 'Active', commissionType: 'on-purchase' }), true);
    assert.equal(passesFilters(event({ status: 'Closed' }), { ...everything, status: 'Active' }), false);
});

test('the summary line says how many events there are and how many are shown', () => {
    assert.equal(eventsSummary(0, 0), 'No events on the server yet.');
    assert.equal(eventsSummary(1, 1), '1 event');
    assert.equal(eventsSummary(4, 4), '4 events');
    assert.equal(eventsSummary(2, 4), '2 of 4 events shown');
});

test('commission is written as percent and type', () => {
    assert.equal(commissionOf(event()), '3% on-purchase');
});

test('every event gets a role, whether the user runs it, trades in it, or neither', () => {
    const holding = { event: event({ number: 2 }), options: [{ number: 1, optionName: 'Yes', shares: 10, paidFor: 6.2, currentValue: 7.3 }, { number: 2, optionName: 'No', shares: 1500, paidFor: 1, currentValue: null }], commissionPaid: 0.2, netResult: 0, trades: [] };
    const roles = rolesOf(me({ marketMakerOf: ['Rain'], participations: [holding] }),
        [event(), event({ number: 2, name: 'Snow' }), event({ number: 3, name: 'Sun' })]);
    assert.deepEqual(roles.map(roleName), ['Market maker', 'Trading', 'Not taken part yet']);
    assert.deepEqual(roles.map(holdingSummary), ['—', 'Yes 10, No 1,500', '—']);
    const both = rolesOf(me({ marketMakerOf: ['Snow'], participations: [holding] }), [event({ number: 2, name: 'Snow' })]);
    assert.equal(roleName(both[0]), 'Market maker, trading');
});

test('the other users are everybody but me', () => {
    const users = [{ name: 'Avi' }, { name: 'Dana' }];
    assert.deepEqual(othersThan(users, me()), [{ name: 'Dana' }]);
    assert.deepEqual(othersThan(users, null), users);
});

test('the moment of going below zero is noticed once', () => {
    assert.equal(justBlocked(me(), me({ blocked: true })), true);
    assert.equal(justBlocked(me({ blocked: true }), me({ blocked: true })), false);
    assert.equal(justBlocked(null, me({ blocked: true })), false);
});

test('the trade panel situation ignores everything that moves twice a second', () => {
    const before = tradeSituation(event(), me());
    assert.equal(tradeSituation(event({ accountBalance: 500, description: 'changed' }), me({ balance: 3 })), before);
    assert.notEqual(tradeSituation(event({ status: 'Closed' }), me()), before);
    assert.notEqual(tradeSituation(event(), me({ blocked: true })), before);
    assert.notEqual(tradeSituation(event({ marketMakerName: 'Avi' }), me()), before);
    assert.notEqual(tradeSituation(event({ number: 2 }), me()), before);
    assert.equal(tradeSituation(null, me()), null);
    assert.equal(tradeSituation(event(), null), null);
});

test('what an action did is told in the JavaFX client words', () => {
    assert.equal(describeOpening(event()), 'You opened "Rain". It is trading now.');
    assert.equal(describeClosing(event({ winningOptionName: 'Yes' })), '"Rain" is closed on "Yes". Its winners have been paid.');
    assert.equal(describePurchase({ optionName: 'Yes', quantity: 10, sharesCost: 5.25, commission: 0.16 }),
        'You bought 10 of "Yes" for 5.25 plus 0.16 commission.');
    assert.equal(describePurchase({ optionName: 'Yes', quantity: 1000, sharesCost: 5, commission: 0 }),
        'You bought 1,000 of "Yes" for 5.00.');
    assert.equal(describeSale({ optionName: 'No', quantity: -4, sharesCost: -1.5 }), 'You sold 4 of "No" for 1.50.');
    assert.equal(describeOrder([]), 'Your order is waiting in the book.');
    assert.equal(describeOrder([{ quantity: 4 }]), 'Your order went through: 1 trade, 4 shares changing hands or newly minted.');
    assert.equal(describeOrder([{ quantity: 4 }, { quantity: 6 }]), 'Your order went through: 2 trades, 10 shares changing hands or newly minted.');
    assert.equal(describeDeposit(500, me({ balance: 600 })), 'You loaded 500.00. Your balance is 600.00.');
});

test('a number of shares must be a whole number from 1 up', () => {
    assert.deepEqual(parseQuantity(' 10 '), { value: 10 });
    for (const typed of ['', 'abc', '0', '1.5', '-3', '1e3', '10 shares']) {
        assert.ok(parseQuantity(typed).problem, `"${typed}" should be refused`);
    }
    assert.equal(parseQuantity('abc').problem, '"abc" is not a number of shares. Please type a whole number from 1 up, such as 10.');
});

test('a price and an amount must be numbers, and the server judges the rest', () => {
    assert.deepEqual(parsePrice('0.55'), { value: 0.55 });
    assert.deepEqual(parsePrice('.5'), { value: 0.5 });
    assert.equal(parsePrice('0.5.5').problem, '"0.5.5" is not a price. Please type a number, such as 0.50.');
    assert.ok(parsePrice('').problem);
    assert.deepEqual(parseAmount('500'), { value: 500 });
    assert.deepEqual(parseAmount('-5'), { value: -5 });
    assert.equal(parseAmount('lots').problem, '"lots" is not an amount. Please type a number, such as 500.');
    assert.ok(parseAmount('  ').problem);
    assert.ok(parseAmount('0x10').problem);
});

test('a chat line reads time, name, text', () => {
    const sentAt = new Date(2026, 9, 11, 9, 5, 0).getTime();
    assert.equal(chatLineText({ number: 1, userName: 'Dana', text: '<b>hi</b>', sentAt }), '09:05:00  Dana:  <b>hi</b>');
});
```

- [ ] **Step 2: Run it to see it fail**

Run: `cd web && node --test test/market.test.js`
Expected: FAIL — `Cannot find module '…/public/js/market.js'`.

- [ ] **Step 3: Write `web/public/js/market.js`**

```js
/**
 * The decisions the screens make, kept apart from the screens so they can be tested on their own.
 * Each one is the JavaScript twin of a piece of the JavaFX client, named where it comes from, and its
 * sentences are that client's sentences word for word.
 */
import { money, shares, percent, time, NOTHING } from './format.js';

export const ORDER_BOOK = 'Order book';
export const ALL = 'All';

/** The three groups of toggles above the events table (EventsController.filterBar). */
export const FILTERS = [
    { caption: 'Type', field: 'methodKind', choices: [ALL, 'LMSR', ORDER_BOOK] },
    { caption: 'Status', field: 'status', choices: [ALL, 'Not started', 'Active', 'Closed'] },
    { caption: 'Commission', field: 'commissionType', choices: [ALL, 'on-purchase', 'on-close'] },
];

export function passesFilters(event, chosen) {
    return FILTERS.every(({ field }) => {
        const wanted = chosen[field] ?? ALL;
        return wanted === ALL || wanted === event[field];
    });
}

export function eventsSummary(shownCount, total) {
    if (total === 0) {
        return 'No events on the server yet.';
    }
    return shownCount === total
        ? `${total} ${total === 1 ? 'event' : 'events'}`
        : `${shownCount} of ${total} events shown`;
}

export function commissionOf(event) {
    return `${percent(event.commissionPercent)} ${event.commissionType}`;
}

/** Where the user stands in every event on the server (AccountController.rolesOf). */
export function rolesOf(me, events) {
    return events.map(event => ({
        event,
        runsIt: me.marketMakerOf.includes(event.name),
        holding: me.participations.find(part => part.event.number === event.number) ?? null,
    }));
}

/** EventRole.role(). */
export function roleName({ runsIt, holding }) {
    if (runsIt) {
        return holding === null ? 'Market maker' : 'Market maker, trading';
    }
    return holding === null ? 'Not taken part yet' : 'Trading';
}

export function holdingSummary({ holding }) {
    if (holding === null || holding.options.length === 0) {
        return NOTHING;
    }
    return holding.options.map(option => `${option.optionName} ${shares(option.shares)}`).join(', ');
}

export function othersThan(users, me) {
    return users.filter(user => me === null || user.name !== me.name);
}

/** True at the one moment an action takes the user below zero (AccountController.tellIfJustBlocked). */
export function justBlocked(was, now) {
    return Boolean(was && now && !was.blocked && now.blocked);
}

/**
 * What the trade panel offers depends only on this; it is rebuilt when, and only when, this changes
 * (TradePanel.rebuildIfNeeded). Prices and balances move twice a second and are left out on purpose:
 * rebuilding with them would wipe a quantity the user was half way through typing.
 */
export function tradeSituation(event, me) {
    if (!event || !me) {
        return null;
    }
    return [event.number, event.status, event.marketMakerName === me.name, me.blocked].join('|');
}

export function describeOpening(opened) {
    return `You opened "${opened.name}". It is trading now.`;
}

export function describeClosing(closed) {
    return `"${closed.name}" is closed on "${closed.winningOptionName}". Its winners have been paid.`;
}

export function describePurchase(bought) {
    const commission = bought.commission > 0 ? ` plus ${money(bought.commission)} commission` : '';
    return `You bought ${shares(bought.quantity)} of "${bought.optionName}" for ${money(bought.sharesCost)}${commission}.`;
}

export function describeSale(sold) {
    return `You sold ${shares(-sold.quantity)} of "${sold.optionName}" for ${money(-sold.sharesCost)}.`;
}

export function describeOrder(trades) {
    if (trades.length === 0) {
        return 'Your order is waiting in the book.';
    }
    const changed = trades.reduce((sum, trade) => sum + trade.quantity, 0);
    return `Your order went through: ${trades.length} ${trades.length === 1 ? 'trade' : 'trades'}, `
        + `${shares(changed)} shares changing hands or newly minted.`;
}

export function describeDeposit(amount, me) {
    return `You loaded ${money(amount)}. Your balance is ${money(me.balance)}.`;
}

/** What Java's Double.parseDouble accepts, less hexadecimal and the words NaN and Infinity. */
const DECIMAL = /^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$/;

export function parseQuantity(text) {
    const typed = String(text ?? '').trim();
    const value = Number(typed);
    return /^\d+$/.test(typed) && value >= 1 && Number.isSafeInteger(value)
        ? { value }
        : { problem: `"${typed}" is not a number of shares. Please type a whole number from 1 up, such as 10.` };
}

export function parsePrice(text) {
    const typed = String(text ?? '').trim();
    return DECIMAL.test(typed)
        ? { value: Number(typed) }
        : { problem: `"${typed}" is not a price. Please type a number, such as 0.50.` };
}

export function parseAmount(text) {
    const typed = String(text ?? '').trim();
    return DECIMAL.test(typed)
        ? { value: Number(typed) }
        : { problem: `"${typed}" is not an amount. Please type a number, such as 500.` };
}

/** ChatController.ChatCell. */
export function chatLineText(line) {
    return `${time(line.sentAt)}  ${line.userName}:  ${line.text}`;
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `cd web && node --test test/market.test.js`
Expected: PASS, 11 tests.

- [ ] **Step 5: Commit**

```bash
git add web/public/js/market.js web/test/market.test.js
git commit -m "Write the market's rules for the web client, apart from any screen"
```

---

### Task 3: The server's API, from the browser

**Files:**
- Create: `web/public/js/api.js`
- Test: `web/test/api.test.js`

**Interfaces:**
- Produces:
  - `UNREACHABLE = 0`
  - `class Failure extends Error { status; message; problems: string[]; get sessionLost(): boolean /* 401 */; get unreachable(): boolean /* 0 or 502 */ }`
  - `failureOf(status: number, bodyText: string): Failure`
  - `createApi(base: string, fetchImpl = fetch)` returning `{ url(path), login(name), logout(), users(), account(), ledger(after), deposit(amount), events(), lmsrEvent(n), orderBook(n), open(n), close(n, winner), buy(n, option, quantity), sell(n, option, quantity), order(n, option, side /* 'buy'|'sell' */, quantity, price), chat(after), say(message) }` — each returns a `Promise` of the parsed JSON, or rejects with a `Failure`.

- [ ] **Step 1: Write the failing test `web/test/api.test.js`**

```js
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

test('an answer that is not ours is described by its status', () => {
    assert.equal(failureOf(500, '<html>oops</html>').message, 'The server answered with status 500.');
    assert.equal(failureOf(404, '').message, 'The server answered with status 404.');
});

test('the logout address is known for the beacon', () => {
    assert.equal(createApi('/guess-market', fakeFetch().fetchImpl).url('logout'), '/guess-market/logout');
});
```

- [ ] **Step 2: Run it to see it fail**

Run: `cd web && node --test test/api.test.js`
Expected: FAIL — `Cannot find module '…/public/js/api.js'`.

- [ ] **Step 3: Write `web/public/js/api.js`**

```js
/**
 * The server's API as the page sees it: one function per endpoint, the only module that knows an
 * address. It mirrors the JavaFX client's MarketServer call for call — same paths, same parameter
 * names, same order — so both clients speak to the server in exactly the same way.
 *
 * Every request goes to the page's own origin. The relay passes it to Tomcat untouched, and the
 * browser sends Tomcat's session cookie along by itself; the user is never a parameter.
 */

/** The status given to a request that never got an answer at all. */
export const UNREACHABLE = 0;

/** Why a request did not succeed, in words fit to show (MarketServer.Failure). */
export class Failure extends Error {
    constructor(status, message, problems = []) {
        super(message);
        this.name = 'Failure';
        this.status = status;
        this.problems = problems;
    }

    /** The server no longer knows this browser, so it has to log in again. */
    get sessionLost() {
        return this.status === 401;
    }

    /** Nothing answered: the relay is gone (0), or the relay could not reach Tomcat (502). */
    get unreachable() {
        return this.status === UNREACHABLE || this.status === 502;
    }
}

/** The server explains every refusal in an ErrorDto; anything else is described by its status. */
export function failureOf(status, bodyText) {
    try {
        const error = JSON.parse(bodyText);
        if (error && typeof error.message === 'string') {
            return new Failure(status, error.message, Array.isArray(error.problems) ? error.problems : []);
        }
    } catch {
        // Not one of ours: an error page from Tomcat itself, for instance.
    }
    return new Failure(status, `The server answered with status ${status}.`);
}

export function createApi(base, fetchImpl = (...args) => globalThis.fetch(...args)) {
    const url = (path, parameters = {}) => {
        const query = new URLSearchParams(parameters).toString();
        return `${base}/${path}${query ? `?${query}` : ''}`;
    };

    async function send(method, path, parameters, body) {
        let response;
        try {
            response = await fetchImpl(url(path, parameters), {
                method, body, credentials: 'same-origin', headers: { Accept: 'application/json' },
            });
        } catch {
            throw new Failure(UNREACHABLE, 'The web client cannot reach its own relay. Please check that the'
                + ' window start.bat opened is still running.');
        }
        const text = await response.text();
        if (!response.ok) {
            throw failureOf(response.status, text);
        }
        try {
            return text ? JSON.parse(text) : null;
        } catch {
            throw new Failure(response.status, "The server's answer could not be read.");
        }
    }

    const get = (path, parameters) => send('GET', path, parameters);
    const post = (path, parameters) => send('POST', path, parameters);

    return {
        url: path => url(path),
        login: name => post('login', { username: name }),
        logout: () => post('logout'),
        users: () => get('users'),
        account: () => get('account'),
        ledger: after => get('account/ledger', { after }),
        deposit: amount => post('account/deposit', { amount }),
        events: () => get('events'),
        lmsrEvent: event => get('events/lmsr', { event }),
        orderBook: event => get('events/book', { event }),
        open: event => post('events/open', { event }),
        close: (event, winner) => post('events/close', { event, winner }),
        buy: (event, option, quantity) => post('trade/buy', { event, option, quantity }),
        sell: (event, option, quantity) => post('trade/sell', { event, option, quantity }),
        order: (event, option, side, quantity, price) => post('trade/order', { event, option, side, quantity, price }),
        chat: after => get('chat', { after }),
        say: message => send('POST', 'chat', {}, new URLSearchParams({ message })),
    };
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `cd web && node --test test/api.test.js`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add web/public/js/api.js web/test/api.test.js
git commit -m "Speak to the server from the browser exactly as the JavaFX client does"
```

---

### Task 4: The feed — pulling the market twice a second

**Files:**
- Create: `web/public/js/feed.js`
- Test: `web/test/feed.test.js`

**Interfaces:**
- Consumes: an `api` with `events() users() account() ledger(after) chat(after)` returning promises (Task 3); `Failure` flags.
- Produces: `PULL_PERIOD = 500`; `createFeed(api, { onSessionLost, timers = globalThis })` returning
  `{ on(topic, listener), every(pull: () => Promise), start(), stop(), pullEverything(), events, users, me, ledger, chat }` where topics are `'events' | 'users' | 'me'` (listener `(now, was)`), `'ledger' | 'chat'` (listener `(all, added)`), `'reachable'` (listener `(ok: boolean, failure?)`).

- [ ] **Step 1: Write the failing test `web/test/feed.test.js`**

```js
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
```

- [ ] **Step 2: Run it to see it fail**

Run: `cd web && node --test test/feed.test.js`
Expected: FAIL — `Cannot find module '…/public/js/feed.js'`.

- [ ] **Step 3: Write `web/public/js/feed.js`**

```js
/**
 * Keeps the page up to date by asking the server, twice a second, how things stand — the web twin of
 * the JavaFX client's MarketFeed, with the same two techniques of the course's chat example:
 *
 *  - whole lists for what changes in place: the events, the users, and the user's own standing;
 *  - deltas for what only grows at its end: the account's lines and the chat, where the page says how
 *    many lines it already holds and receives only the rest.
 *
 * A value is handed to the screens only when it differs from the last one, so nothing is redrawn —
 * and no selection is lost — merely because half a second passed. Each kind of pull runs at most once
 * at a time: a round that comes while the last answer is still on its way is remembered and sent the
 * moment that answer arrives.
 */
export const PULL_PERIOD = 500;

export function createFeed(api, { onSessionLost = () => {}, timers = globalThis } = {}) {
    const state = { events: [], users: [], me: null, ledger: [], chat: [] };
    const shownJson = { events: '[]', users: '[]', me: 'null' };
    const listeners = { events: [], users: [], me: [], ledger: [], chat: [], reachable: [] };
    const pulls = [];
    let timer = null;
    let running = false;
    let reachable = true;

    const tell = (topic, ...values) => listeners[topic].forEach(listener => listener(...values));

    function publish(topic, fresh) {
        const json = JSON.stringify(fresh);
        if (json === shownJson[topic]) {
            return;
        }
        const was = state[topic];
        state[topic] = fresh;
        shownJson[topic] = json;
        tell(topic, fresh, was);
    }

    function append(topic, answer, heldWhenAsked) {
        // A stale answer to an earlier question would repeat lines already shown.
        if (answer.after !== heldWhenAsked || answer.after !== state[topic].length || answer.lines.length === 0) {
            return;
        }
        state[topic] = [...state[topic], ...answer.lines];
        tell(topic, state[topic], answer.lines);
    }

    function setReachable(ok, failure) {
        if (ok !== reachable) {
            reachable = ok;
            tell('reachable', ok, failure);
        }
    }

    function failed(failure) {
        if (failure?.sessionLost) {
            if (running) {
                stop();
                onSessionLost();
            }
        } else if (failure?.unreachable) {
            setReachable(false, failure);
        } else if (!(failure?.status >= 400)) {
            console.error(failure);
        }
    }

    /** One kind of pull, never more than one at a time. */
    function guarded(pull) {
        let underway = false;
        let askedAgain = false;
        return function run() {
            if (underway) {
                askedAgain = true;
                return;
            }
            underway = true;
            Promise.resolve()
                .then(pull)
                .then(() => setReachable(true), failed)
                .finally(() => {
                    underway = false;
                    if (askedAgain) {
                        askedAgain = false;
                        run();
                    }
                });
        };
    }

    function every(pull) {
        pulls.push(guarded(pull));
    }

    function pullEverything() {
        pulls.forEach(run => run());
    }

    function start() {
        running = true;
        pullEverything();
        timer = timers.setInterval(pullEverything, PULL_PERIOD);
    }

    function stop() {
        running = false;
        if (timer !== null) {
            timers.clearInterval(timer);
            timer = null;
        }
    }

    every(() => api.events().then(fresh => publish('events', fresh)));
    every(() => api.users().then(fresh => publish('users', fresh)));
    every(() => api.account().then(fresh => publish('me', fresh)));
    every(() => {
        const held = state.ledger.length;
        return api.ledger(held).then(answer => append('ledger', answer, held));
    });
    every(() => {
        const held = state.chat.length;
        return api.chat(held).then(answer => append('chat', answer, held));
    });

    return {
        on: (topic, listener) => listeners[topic].push(listener),
        every, start, stop, pullEverything,
        get events() { return state.events; },
        get users() { return state.users; },
        get me() { return state.me; },
        get ledger() { return state.ledger; },
        get chat() { return state.chat; },
    };
}
```

- [ ] **Step 4: Run it to see it pass**

Run: `cd web && node --test test/feed.test.js`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add web/public/js/feed.js web/test/feed.test.js
git commit -m "Pull the market twice a second, as the JavaFX client's feed does"
```

---

### Task 5: The relay, `serve.js` and `start.bat`

**Files:**
- Create: `web/relay.js`, `web/serve.js`, `web/start.bat`, `web/public/index.html` (a first, minimal version; Task 6 completes it)
- Test: `web/test/relay.test.js`

**Interfaces:**
- Produces: `createRelay({ server: string /* 'http://host:port/context' */, publicDir: string }): http.Server` (not yet listening). Behaviour: `<context>` and `<context>/*` pass through; `GET /config.json` → `{ apiBase, server }`; other GET/HEAD → files under `publicDir` (`/` → `index.html`), else 404 JSON; other methods → 405 JSON; Tomcat unreachable → 502 `ErrorDto` JSON.
- `node serve.js [server] [port] [--open]`.

- [ ] **Step 1: Write a minimal `web/public/index.html`** (so the static test has a page to find)

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Guess Market</title>
</head>
<body>
</body>
</html>
```

- [ ] **Step 2: Write the failing test `web/test/relay.test.js`**

```js
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
const close = server => new Promise(resolve => server.close(resolve));

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

test('a path that only starts like the API is not passed through', async () => {
    seen = null;
    assert.equal((await raw('GET', '/guess-marketing')).status, 404);
    assert.equal(seen, null);
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
```

- [ ] **Step 3: Run it to see it fail**

Run: `cd web && node --test test/relay.test.js`
Expected: FAIL — `Cannot find module '…/relay.js'`.

- [ ] **Step 4: Write `web/relay.js`**

```js
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
            pass(request, response);
        } else if (request.method !== 'GET' && request.method !== 'HEAD') {
            json(response, 405, { message: 'Only the server\'s API accepts anything but reading.', problems: [] });
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
            reply.pipe(response);
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
```

- [ ] **Step 5: Run it to see it pass**

Run: `cd web && node --test test/relay.test.js`
Expected: PASS, 7 tests.

- [ ] **Step 6: Write `web/serve.js`**

```js
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

relay.listen(port, () => {
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
```

- [ ] **Step 7: Write `web/start.bat`** (CRLF line endings — `.gitattributes` already enforces `*.bat text eol=crlf`)

```bat
@echo off
rem ---------------------------------------------------------------------------
rem  Starts the Guess Market web client and opens it in the browser.
rem
rem    start.bat                            server at http://localhost:8080/guess-market
rem    start.bat <server-address>           another Tomcat
rem    start.bat <server-address> <port>    and the page on another port
rem
rem  The page is served at http://localhost:3000/ by serve.js, which uses
rem  Node.js's own modules only - there is nothing to install. It also passes
rem  the server's API through to Tomcat, which is run exactly as for
rem  exercise 3. Close this window to stop the web client.
rem ---------------------------------------------------------------------------
setlocal

where node >nul 2>&1
if errorlevel 1 (
    echo Node.js was not found. The web client needs Node.js 18 or later on the PATH.
    pause
    exit /b 1
)

set "SERVER=%~1"
if "%SERVER%"=="" set "SERVER=http://localhost:8080/guess-market"
set "PORT=%~2"
if "%PORT%"=="" set "PORT=3000"

node "%~dp0serve.js" "%SERVER%" "%PORT%" --open
if errorlevel 1 pause
exit /b %ERRORLEVEL%
```

- [ ] **Step 8: Try it by hand**

Run (Git Bash): `node web/serve.js http://localhost:8081/guess-market 3000` in the background, then `curl -s http://localhost:3000/config.json` and `curl -s -i http://localhost:3000/guess-market/events | head -5`, then stop it.
Expected: config `{"apiBase":"/guess-market","server":"http://localhost:8081/guess-market"}`; events `401` with `ErrorDto` if Tomcat is up, `502` with "Cannot reach…" if it is not. Then `node web/serve.js nonsense` → `"nonsense" is not a server address…`, exit 1; and a second copy on the same port → "Port 3000 is already in use…".

- [ ] **Step 9: Commit**

```bash
git add web/relay.js web/serve.js web/start.bat web/public/index.html web/test/relay.test.js
git commit -m "Serve the web client and relay its requests to the unchanged server"
```

---

### Task 6: The page's frame — DOM helpers, tables, shell, login, session, start-up

**Files:**
- Create: `web/public/js/dom.js`, `web/public/js/table.js`, `web/public/js/shell.js`, `web/public/js/session.js`, `web/public/js/login.js`, `web/public/js/app.js`, `web/public/css/guess-market.css`
- Modify: `web/public/index.html` (full version)
- Test: `web/test/table.test.js`, `web/test/no-html.test.js`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces:
  - `dom.js`: `el(tag, props, ...children)` (props: `text`, `class`, `value`, `onX` functions, else attributes), `section(title)`, `wrapping(text)`, `labelled(...captionValuePairs)`, `row(...children)`, `label(text)`, `tabbed([{name, element}]) → {element, select(name)}`.
  - `table.js`: `keepSelection(rows, key, keyOf)`, `col(title, value, {wrap})`, `num(title, value)`, `createTable({columns, keyOf, placeholder, onSelect, id}) → {element, show(rows), selected(), scrollToEnd()}`, `staticTable(columns, rows, placeholder) → HTMLElement`.
  - `shell.js`: `createShell() → {main, messenger: {status(msg), refused(failure), warn(headline, msg)}, showUser(me|null), reachable(ok, message), onSessionLost(handler), onLogOut(handler)}`.
  - `session.js`: `remember(name)`, `forget()`, `remembered(): string|null`, `logOutWhenLeaving(url)`.
  - `login.js`: `showLogin(main, {api, serverAddress, message, name, loggedIn(me)})`.
  - `app.js`: entry point; until Tasks 7–9 the market shows three empty tabs.

- [ ] **Step 1: Write the failing tests**

`web/test/table.test.js`:

```js
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
```

`web/test/no-html.test.js`:

```js
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
```

- [ ] **Step 2: Run them to see them fail**

Run: `cd web && node --test test/table.test.js test/no-html.test.js`
Expected: table test FAILS (`Cannot find module …table.js`); the no-html test fails on `files.length >= 5` (only four modules exist yet).

- [ ] **Step 3: Write `web/public/js/dom.js`**

```js
/**
 * The few building blocks every screen is made of. Text always goes in as text — textContent or a
 * text node — never as HTML, so whatever a user named an event is shown as the name it is.
 */
export function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [name, value] of Object.entries(props)) {
        if (value === null || value === undefined || value === false) {
            continue;
        }
        if (name === 'text') {
            node.textContent = value;
        } else if (name === 'class') {
            node.className = value;
        } else if (name === 'value') {
            node.value = value;
        } else if (name.startsWith('on') && typeof value === 'function') {
            node.addEventListener(name.slice(2), value);
        } else {
            node.setAttribute(name, value === true ? '' : String(value));
        }
    }
    node.append(...children.flat().filter(child => child !== null && child !== undefined && child !== false));
    return node;
}

/** Views.section. */
export const section = title => el('h3', { class: 'section-title', text: title });

/** Views.wrapping. */
export const wrapping = text => el('p', { class: 'wrapping', text });

/** Views.labelled: caption, value, caption, value… */
export function labelled(...pairs) {
    const list = el('dl', { class: 'labelled' });
    for (let i = 0; i < pairs.length; i += 2) {
        list.append(el('dt', { text: pairs[i] }), el('dd', { text: String(pairs[i + 1] ?? '') }));
    }
    return list;
}

export const row = (...children) => el('div', { class: 'row' }, ...children);

export const label = text => el('span', { class: 'caption', text });

/** Tabs, the first one chosen. */
export function tabbed(panels) {
    const buttons = new Map();
    const holders = new Map();
    for (const { name, element } of panels) {
        buttons.set(name, el('button', { type: 'button', role: 'tab', class: 'tab', text: name, onclick: () => select(name) }));
        holders.set(name, el('div', { class: 'tab-panel', role: 'tabpanel' }, element));
    }
    function select(chosen) {
        for (const [name, button] of buttons) {
            button.setAttribute('aria-selected', String(name === chosen));
            holders.get(name).hidden = name !== chosen;
        }
    }
    select(panels[0].name);
    return {
        element: el('div', { class: 'tabbed' },
            el('nav', { class: 'tabs', role: 'tablist' }, ...buttons.values()),
            el('div', { class: 'tab-panels' }, ...holders.values())),
        select,
    };
}
```

- [ ] **Step 4: Write `web/public/js/table.js`**

```js
/**
 * Every table on the page. A table is redrawn only when its rows changed, and keeps whichever row was
 * selected — found again by its key, since fresh data brings fresh objects. Redrawing never counts as
 * the user choosing something: only a click does.
 */
import { el } from './dom.js';

export function keepSelection(rows, selectedKey, keyOf) {
    if (selectedKey === null || selectedKey === undefined || !keyOf) {
        return null;
    }
    return rows.find(row => keyOf(row) === selectedKey) ?? null;
}

export const col = (title, value, { wrap = false } = {}) => ({ title, value, wrap });
export const num = (title, value) => ({ title, value, numeric: true });

const classes = (...names) => names.filter(Boolean).join(' ') || null;

export function createTable({ columns, keyOf = null, placeholder = '', onSelect = () => {}, id = null }) {
    const body = el('tbody');
    const head = el('thead', {}, el('tr', {}, ...columns.map(column =>
        el('th', { class: classes(column.numeric && 'num'), text: column.title }))));
    const wrap = el('div', { class: 'table-wrap' }, el('table', { class: 'grid', id }, head, body));
    let rows = [];
    let rowElements = [];
    let shownJson = null;
    let selectedKey = null;

    function show(fresh) {
        const json = JSON.stringify(fresh);
        if (json === shownJson) {
            return;
        }
        shownJson = json;
        rows = fresh;
        const kept = keepSelection(rows, selectedKey, keyOf);
        selectedKey = kept ? keyOf(kept) : null;
        rowElements = rows.map(rowElement);
        body.replaceChildren(...(rows.length > 0 ? rowElements
            : [el('tr', {}, el('td', { class: 'placeholder', colspan: String(columns.length), text: placeholder }))]));
    }

    function rowElement(rowData) {
        const selectable = keyOf !== null;
        const tr = el('tr', { class: classes(selectable && 'selectable', selectable && keyOf(rowData) === selectedKey && 'selected') },
            ...columns.map(column => el('td', {
                class: classes(column.numeric && 'num', column.wrap && 'wrap'),
                text: String(column.value(rowData) ?? ''),
            })));
        if (selectable) {
            tr.addEventListener('click', () => choose(rowData));
        }
        return tr;
    }

    function choose(rowData) {
        selectedKey = keyOf(rowData);
        rows.forEach((each, i) => rowElements[i].classList.toggle('selected', keyOf(each) === selectedKey));
        onSelect(rowData);
    }

    return {
        element: wrap,
        show,
        selected: () => keepSelection(rows, selectedKey, keyOf),
        scrollToEnd: () => { wrap.scrollTop = wrap.scrollHeight; },
    };
}

/** A table that is drawn once, with nothing to select. */
export function staticTable(columns, rows, placeholder) {
    const table = createTable({ columns, placeholder });
    table.show(rows);
    return table.element;
}
```

- [ ] **Step 5: Write `web/public/js/session.js`**

```js
/**
 * Who is logged in, remembered for this tab only, and what happens when the tab goes away.
 *
 * Closing the tab logs out at once — a beacon is the one request a browser still sends as a page
 * closes — so the name is free for the JavaFX client straight away rather than after the server's
 * two-minute timeout. A reload sends the same beacon, so the reloaded page finds the name kept here
 * and logs back in by itself.
 */
const KEY = 'gm.userName';

function storage() {
    try {
        return window.sessionStorage;
    } catch {
        return null;
    }
}

export function remember(name) {
    try { storage()?.setItem(KEY, name); } catch { /* private mode: reloading will ask for the name */ }
}

export function forget() {
    try { storage()?.removeItem(KEY); } catch { /* nothing was kept */ }
}

export function remembered() {
    try { return storage()?.getItem(KEY) ?? null; } catch { return null; }
}

export function logOutWhenLeaving(logoutUrl) {
    window.addEventListener('pagehide', () => {
        if (remembered()) {
            navigator.sendBeacon(logoutUrl);
        }
    });
}
```

- [ ] **Step 6: Write `web/public/js/shell.js`**

```js
/**
 * The frame around every screen — the top bar, the banner, the status line and the warning dialog —
 * and the Messenger every screen reports through (MainController).
 */
import { money } from './format.js';

export function createShell() {
    const $ = id => document.getElementById(id);
    let sessionLost = () => {};

    const messenger = {
        status(message) {
            $('status').textContent = message;
        },
        refused(failure) {
            if (failure.sessionLost) {
                sessionLost();
                return;
            }
            messenger.warn('That could not be done', failure.message);
        },
        warn(headline, message) {
            $('warningHeadline').textContent = headline;
            $('warningMessage').textContent = message;
            const dialog = $('warning');
            if (!dialog.open) {
                dialog.showModal();
            }
        },
    };

    return {
        main: $('main'),
        messenger,
        showUser(me) {
            $('who').hidden = !me;
            if (me) {
                $('userLabel').textContent = `Logged in as ${me.name}`;
                $('balanceLabel').textContent = `Balance ${money(me.balance)}`;
            }
        },
        reachable(ok, message = '') {
            $('banner').hidden = ok;
            $('banner').textContent = ok ? '' : message;
        },
        onSessionLost(handler) {
            sessionLost = handler;
        },
        onLogOut(handler) {
            $('logoutButton').onclick = handler;
        },
    };
}
```

- [ ] **Step 7: Write `web/public/js/login.js`** (wording from `login.fxml` and `LoginController`)

```js
import { el } from './dom.js';

export function showLogin(main, { api, serverAddress, message = '', name = '', loggedIn }) {
    const field = el('input', { type: 'text', id: 'nameField', placeholder: 'Your name', 'aria-label': 'Your name', autocomplete: 'off', value: name });
    const button = el('button', { type: 'submit', id: 'loginButton', class: 'primary-button', text: 'Log in' });
    const problem = el('p', { id: 'problemLabel', class: 'problem', role: 'alert', text: message });
    const form = el('form', { class: 'login-card' },
        el('h2', { class: 'detail-title', text: 'Log in' }),
        el('p', { text: 'Choose a name. If somebody is already using it, you will be asked for another.' }),
        el('div', { class: 'row' }, field, button),
        problem,
        el('p', { class: 'caption', text: `Server: ${serverAddress}` }));

    form.addEventListener('submit', async event => {
        event.preventDefault();
        const wanted = field.value.trim();
        if (!wanted) {
            problem.textContent = 'Please enter a name.';
            return;
        }
        button.disabled = true;
        field.disabled = true;
        problem.textContent = '';
        try {
            loggedIn(await api.login(wanted));
        } catch (failure) {
            button.disabled = false;
            field.disabled = false;
            problem.textContent = failure.message;
            field.select();
            field.focus();
        }
    });

    main.replaceChildren(el('div', { class: 'login-page' }, form));
    field.focus();
}
```

- [ ] **Step 8: Write `web/public/js/app.js`** (Tasks 7–9 replace the three placeholder tabs)

```js
/**
 * Starts the page: finds out where the API is, sees whether this browser is already logged in, and
 * moves between the login screen and the market — including when the server forgets the session.
 */
import { createApi } from './api.js';
import { createFeed } from './feed.js';
import { createShell } from './shell.js';
import { showLogin } from './login.js';
import { el, tabbed } from './dom.js';
import { remember, forget, remembered, logOutWhenLeaving } from './session.js';

const SESSION_ENDED = 'The server no longer knows this session; it may have been restarted. Please log in again.';

const shell = createShell();
let api = null;
let serverAddress = '';
let feed = null;
let reloggedOnce = false;

start();

async function start() {
    let config;
    try {
        config = await (await fetch('config.json', { cache: 'no-store' })).json();
    } catch {
        shell.messenger.status('The web client is not running. Please run start.bat again.');
        return;
    }
    api = createApi(config.apiBase);
    serverAddress = config.server;
    shell.onSessionLost(sessionLost);
    shell.onLogOut(logOut);
    logOutWhenLeaving(api.url('logout'));
    try {
        enter(await api.account());
    } catch (failure) {
        if (failure.sessionLost && remembered()) {
            await reenter();
        } else {
            login(failure.sessionLost ? '' : failure.message, remembered() ?? '');
        }
    }
}

function login(message = '', name = '') {
    leaveMarket();
    showLogin(shell.main, { api, serverAddress, message, name, loggedIn: enter });
}

function enter(me) {
    remember(me.name);
    leaveMarket();
    feed = createFeed(api, { onSessionLost: sessionLost });
    feed.on('me', now => shell.showUser(now));
    feed.on('reachable', (ok, failure) => shell.reachable(ok, failure?.message));
    shell.main.replaceChildren(marketScreens(feed).element);
    shell.showUser(me);
    shell.messenger.status(`Welcome, ${me.name}. Load funds from the Account tab, then choose an event to trade in.`);
    feed.start();
}

function marketScreens() {
    return tabbed([
        { name: 'Events', element: el('div') },
        { name: 'Account', element: el('div') },
        { name: 'Chat', element: el('div') },
    ]);
}

function leaveMarket() {
    if (feed) {
        feed.stop();
        feed = null;
    }
    shell.showUser(null);
    shell.reachable(true);
}

/** Logs back in as the name this tab remembers — once per page, which covers a late logout beacon. */
async function reenter() {
    const name = remembered();
    reloggedOnce = true;
    try {
        enter(await api.login(name));
    } catch (failure) {
        forget();
        login(failure.unreachable ? failure.message : SESSION_ENDED, name);
    }
}

function sessionLost() {
    if (!feed) {
        return;
    }
    leaveMarket();
    if (remembered() && !reloggedOnce) {
        reenter();
        return;
    }
    forget();
    login(SESSION_ENDED);
}

async function logOut() {
    forget();
    leaveMarket();
    try {
        await api.logout();
    } catch {
        // The session ends on the server anyway, two minutes later.
    }
    login('You have logged out.');
}
```

- [ ] **Step 9: Write the full `web/public/index.html`**

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Guess Market</title>
<link rel="stylesheet" href="css/guess-market.css">
<script type="module" src="js/app.js"></script>
</head>
<body>
<header class="header">
  <h1 class="app-title">Guess Market</h1>
  <div id="who" class="who" hidden>
    <span id="userLabel"></span>
    <span id="balanceLabel" class="header-balance"></span>
    <button id="logoutButton" type="button">Log out</button>
  </div>
</header>
<div id="banner" class="banner" role="alert" hidden></div>
<main id="main"></main>
<footer id="status" class="status-bar" aria-live="polite"></footer>
<dialog id="warning" class="warning">
  <form method="dialog">
    <h2 id="warningHeadline"></h2>
    <p id="warningMessage"></p>
    <button class="primary-button" value="ok">OK</button>
  </form>
</dialog>
</body>
</html>
```

- [ ] **Step 10: Write `web/public/css/guess-market.css`** (colours from `client/resources/gm/client/guess-market.css`)

```css
/*
  The one look of the client, carried over from the JavaFX client: navy on a light grey desk.
  The two panes of a tab sit side by side on a wide window and one under the other on a narrow one;
  tables scroll inside their pane, never the page.
*/
:root {
    --navy: #1f3864; --blue: #2e5496; --blue-hover: #3a68b8; --desk: #f5f6f8; --bar: #e4e7ee;
    --ink: #2a2f3a; --muted: #55607a; --line: #d5dae6; --panel: #e9edf5; --problem: #b00020;
    --pale: #d6ddf0; --row-hover: #f1f4fa; --head: #eef1f7;
    font-family: "Segoe UI", system-ui, sans-serif; font-size: 14px; color: var(--ink);
}
* { box-sizing: border-box; }
html, body { height: 100%; margin: 0; }
body { display: flex; flex-direction: column; background: var(--desk); }
[hidden] { display: none !important; }

.header { background: var(--navy); color: #fff; display: flex; align-items: center; gap: 8px 16px; padding: 8px 16px; flex-wrap: wrap; }
.app-title { font-size: 1.6em; margin: 0; flex: 1; text-align: center; }
.who { display: flex; gap: 12px; align-items: center; color: var(--pale); flex-wrap: wrap; }
.header-balance { font-weight: bold; color: #fff; }
.header button { background: transparent; color: #fff; border-color: #6d82b3; }
.header button:hover:not(:disabled) { background: #2a4677; }
.banner { background: #fde8eb; color: var(--problem); padding: 6px 16px; border-bottom: 1px solid #f3c1c9; }
main { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.status-bar { background: var(--bar); padding: 6px 16px; min-height: 2.2em; overflow-wrap: anywhere; }

button { font: inherit; padding: 4px 12px; border: 1px solid var(--line); border-radius: 4px; background: #fff; color: var(--ink); cursor: pointer; }
button:hover:not(:disabled) { background: var(--head); }
button:disabled { opacity: .55; cursor: default; }
.primary-button { font-weight: bold; background: var(--blue); color: #fff; border-color: var(--blue); }
.primary-button:hover:not(:disabled) { background: var(--blue-hover); }
input, select { font: inherit; padding: 4px 6px; border: 1px solid var(--line); border-radius: 4px; background: #fff; color: var(--ink); }
input.quantity { width: 7em; }
input.price { width: 6em; }

.tabbed { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.tabs { display: flex; gap: 2px; padding: 6px 12px 0; border-bottom: 1px solid var(--line); }
.tab { border-radius: 4px 4px 0 0; border-bottom: none; }
.tab[aria-selected="true"] { background: #fff; font-weight: bold; color: var(--navy); }
.tab-panels { flex: 1; min-height: 0; }
.tab-panel { height: 100%; }

.split { display: grid; height: 100%; }
.events-split { grid-template-columns: minmax(0, 48fr) minmax(0, 52fr); }
.account-split { grid-template-columns: minmax(0, 32fr) minmax(0, 68fr); }
.pane { padding: 10px 12px; min-height: 0; overflow: auto; display: flex; flex-direction: column; gap: 8px; }
.split > .pane + .pane { border-left: 1px solid var(--line); }
@media (max-width: 900px) {
    .events-split, .account-split { grid-template-columns: minmax(0, 1fr); height: auto; }
    .split > .pane + .pane { border-left: none; border-top: 1px solid var(--line); }
    .tab-panels { overflow: auto; }
    .pane { overflow: visible; }
}

.table-wrap { overflow: auto; border: 1px solid var(--line); background: #fff; border-radius: 4px; max-height: 22em; flex-shrink: 0; }
.table-wrap.grow { flex: 1 1 12em; max-height: none; }
table.grid { border-collapse: collapse; width: 100%; }
table.grid th { position: sticky; top: 0; background: var(--head); text-align: left; font-weight: 600; padding: 4px 8px; border-bottom: 1px solid var(--line); white-space: nowrap; }
table.grid td { padding: 3px 8px; border-bottom: 1px solid #eef0f5; white-space: nowrap; }
table.grid td.wrap { white-space: normal; overflow-wrap: anywhere; min-width: 12em; }
table.grid .num { text-align: right; }
table.grid tr.selectable { cursor: pointer; }
table.grid tr.selectable:hover td { background: var(--row-hover); }
table.grid tr.selected td { background: var(--blue); color: #fff; }
table.grid td.placeholder { color: var(--muted); text-align: center; padding: 14px; white-space: normal; }

.detail-title { font-size: 1.4em; color: var(--navy); margin: 0; overflow-wrap: anywhere; }
.section-title { font-size: 1em; color: var(--blue); margin: 8px 0 0; }
.option-title { font-size: 1.15em; color: var(--navy); font-weight: bold; margin: 0; overflow-wrap: anywhere; }
.caption, .summary, .filter-caption { color: var(--muted); }
.summary { margin: 0; }
.problem { color: var(--problem); }
.balance { font-size: 1.2em; font-weight: bold; color: var(--navy); }
.labelled { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 2px 14px; margin: 0; }
.labelled dt { color: var(--muted); }
.labelled dd { margin: 0; overflow-wrap: anywhere; }
.wrapping { margin: 0; overflow-wrap: anywhere; }
hr { border: none; border-top: 1px solid var(--line); width: 100%; margin: 4px 0; }
.stack { display: flex; flex-direction: column; gap: 6px; }
.row { display: flex; flex-wrap: wrap; gap: 6px 8px; align-items: center; }
.spacer { flex: 1; }

.filters { display: flex; flex-wrap: wrap; gap: 8px 14px; }
.filter-group { display: flex; gap: 4px; align-items: center; flex-wrap: wrap; }
.toggle[aria-pressed="true"] { background: var(--blue); color: #fff; border-color: var(--blue); }

.trade-panel { padding: 10px; background: var(--panel); border-radius: 4px; display: flex; flex-direction: column; gap: 8px; }
.books { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
@media (max-width: 1100px) { .books { grid-template-columns: minmax(0, 1fr); } }
.option-book { padding: 8px; background: #fff; border: 1px solid var(--line); border-radius: 4px; display: flex; flex-direction: column; gap: 6px; min-width: 0; }

.login-page { flex: 1; display: flex; align-items: center; justify-content: center; padding: 16px; }
.login-card { background: #fff; border: 1px solid var(--line); border-radius: 6px; padding: 20px 24px; width: min(440px, 100%); display: flex; flex-direction: column; gap: 10px; }
.login-card p { margin: 0; }
.login-card .row input { flex: 1; min-width: 0; }

.chat { height: 100%; }
.chat-lines { list-style: none; margin: 0; padding: 6px 8px; flex: 1 1 10em; overflow: auto; background: #fff; border: 1px solid var(--line); border-radius: 4px; }
.chat-lines li { padding: 2px 0; overflow-wrap: anywhere; white-space: pre-wrap; }
.chat-lines li.placeholder { color: var(--muted); }
.chat-writing input { flex: 1; min-width: 0; }

.warning { border: 1px solid var(--line); border-radius: 6px; padding: 16px 20px; max-width: min(560px, calc(100vw - 32px)); }
.warning h2 { margin: 0 0 8px; color: var(--navy); font-size: 1.15em; }
.warning p { white-space: pre-wrap; overflow-wrap: anywhere; }
.warning::backdrop { background: rgba(20, 30, 50, .35); }
```

- [ ] **Step 11: Run all the tests**

Run: `cd web && node --test`
Expected: PASS — every test so far, the two new files included.

- [ ] **Step 12: Check it live**

Start Tomcat (`tools/tomcat`, port 8081 — `CATALINA_HOME` and `JAVA_HOME` set, `catalina.bat run` in the background) with `build/guess-market.war` deployed, then `node web/serve.js http://localhost:8081/guess-market 3000`. In the in-app browser open `http://localhost:3000/`:
- an empty name → "Please enter a name."; log in as `Web1` → three tabs, "Logged in as Web1 · Balance 0.00", the welcome status;
- `curl -s -X POST "http://localhost:8081/guess-market/login?username=Web1"` → `409`; then, in the page, reload → still logged in as Web1 (beacon then silent re-login);
- log out → "You have logged out."; log in as Web1 in the page, then from curl with a fresh cookie jar as `Web1` → 409 (name held); close the tab, curl again → 200 (beacon freed it);
- stop Tomcat → the banner "Cannot reach the Guess Market server at …"; start it → banner gone, page back at login with `SESSION_ENDED` (Tomcat forgot the session) or logged in again silently.

- [ ] **Step 13: Commit**

```bash
git add web/public web/test
git commit -m "Give the web client its frame: login, tabs, status line and session handling"
```

---

### Task 7: The events tab — list, filters, the event in full, and the trade panel

**Files:**
- Create: `web/public/js/trade.js`, `web/public/js/event-detail.js`, `web/public/js/events.js`
- Modify: `web/public/js/app.js` (`marketScreens`)

**Interfaces:**
- Consumes: `api` (Task 3), `feed` (Task 4: `on('me')`, `me`, `events`, `every`, `pullEverything`), `messenger` (Task 6), `market.js` (Task 2), `dom.js`/`table.js` (Task 6), `Failure` (Task 3).
- Produces:
  - `createTradePanel({api, feed, messenger}) → {element, show(event|null)}`
  - `createEventDetail({api, feed, actions: HTMLElement}) → {element, watch(event|null)}`; `tradeTable(trades) → HTMLElement`
  - `createEventsTab({api, feed, messenger}) → {element}`

- [ ] **Step 1: Write `web/public/js/trade.js`** (wording from `TradePanel.java`)

```js
/**
 * Everything the logged-in user can do to one event: open it if they run it and it waits, trade in it
 * while it runs, close it if they run it (TradePanel). Written once and used on both the events tab
 * and the account tab.
 *
 * It is rebuilt only when what can be done changes (market.tradeSituation). The market around it moves
 * twice a second, and rebuilding with it would wipe a quantity the user was half way through typing.
 */
import { el, wrapping, row, label } from './dom.js';
import { money } from './format.js';
import { Failure } from './api.js';
import {
    ORDER_BOOK, tradeSituation, describeOpening, describeClosing, describePurchase, describeSale,
    describeOrder, parseQuantity, parsePrice,
} from './market.js';

export function createTradePanel({ api, feed, messenger }) {
    const root = el('div', { class: 'trade-panel' });
    let event = null;
    let builtFor;

    feed.on('me', rebuildIfNeeded);
    rebuildIfNeeded();
    return {
        element: root,
        show(chosen) {
            event = chosen;
            rebuildIfNeeded();
        },
    };

    function rebuildIfNeeded() {
        const situation = tradeSituation(event, feed.me);
        if (situation !== null && situation === builtFor) {
            return;
        }
        builtFor = situation;
        root.replaceChildren(...controlsFor(feed.me));
    }

    function controlsFor(me) {
        if (!event) {
            return [wrapping('Choose an event to act on it.')];
        }
        if (!me) {
            return [wrapping('Fetching your account...')];
        }
        if (me.blocked) {
            return [wrapping('You have spent past zero, and can take no further part in the market.')];
        }
        const runsIt = event.marketMakerName === me.name;
        if (event.status === 'Not started') {
            return [runsIt ? openControls()
                : wrapping(`This event is waiting for its market maker, ${event.marketMakerName}, to open it.`)];
        }
        if (event.status === 'Closed') {
            return [wrapping(`This event is closed. It was decided on "${event.winningOptionName}".`)];
        }
        const trading = event.methodKind === ORDER_BOOK ? orderControls() : lmsrControls();
        return runsIt ? [trading, closeControls()] : [trading];
    }

    function openControls() {
        const open = button('Open this event', true);
        open.addEventListener('click', () => act(open, () => api.open(event.number), describeOpening));
        return el('div', { class: 'stack' },
            wrapping(`You run this event. Opening it costs ${money(event.openingCost)}, paid from your own account.`),
            el('div', {}, open));
    }

    function lmsrControls() {
        const option = optionChoice('Option');
        const quantity = quantityField();
        const buy = button('Buy', true);
        const sell = button('Sell');
        buy.addEventListener('click', () => withQuantity(quantity, count =>
            act(buy, () => api.buy(event.number, Number(option.value), count), describePurchase)));
        sell.addEventListener('click', () => withQuantity(quantity, count =>
            act(sell, () => api.sell(event.number, Number(option.value), count), describeSale)));
        return el('div', { class: 'stack' },
            wrapping('Shares are bought from, and sold back to, the event itself.'),
            row(label('Option'), option, label('Shares'), quantity, buy, sell));
    }

    function orderControls() {
        const option = optionChoice('Option');
        const side = el('select', { 'aria-label': 'Side' },
            el('option', { value: 'buy', text: 'Buy' }), el('option', { value: 'sell', text: 'Sell' }));
        const quantity = quantityField();
        const price = el('input', { type: 'text', class: 'price', 'aria-label': 'Price', inputmode: 'decimal', value: '0.50' });
        const place = button('Place order', true);
        place.addEventListener('click', () => withQuantity(quantity, count => {
            const asked = parsePrice(price.value);
            if (asked.problem) {
                messenger.refused(new Failure(400, asked.problem));
                return;
            }
            act(place, () => api.order(event.number, Number(option.value), side.value, count, asked.value), describeOrder);
        }));
        return el('div', { class: 'stack' },
            wrapping("Orders meet other people's. Whatever finds no match waits in the book."),
            row(label('Option'), option, label('Side'), side, label('Shares'), quantity, label('Price'), price, place));
    }

    function closeControls() {
        const winner = optionChoice('Winning option');
        const close = button('Close on this outcome');
        close.addEventListener('click', () =>
            act(close, () => api.close(event.number, Number(winner.value)), describeClosing));
        return el('div', { class: 'stack' }, el('hr'),
            wrapping('You run this event, so you decide it. This cannot be undone.'),
            row(label('Winning option'), winner, close));
    }

    /**
     * Sends one action and reports what came of it. The button stays disabled until the answer comes,
     * so an impatient second click cannot buy twice.
     */
    async function act(sender, request, describe) {
        sender.disabled = true;
        try {
            messenger.status(describe(await request()));
            feed.pullEverything();
        } catch (failure) {
            messenger.refused(failure);
        } finally {
            sender.disabled = false;
        }
    }

    function withQuantity(field, then) {
        const count = parseQuantity(field.value);
        if (count.problem) {
            messenger.refused(new Failure(400, count.problem));
            return;
        }
        then(count.value);
    }

    function optionChoice(caption) {
        return el('select', { 'aria-label': caption },
            ...event.optionNames.map((name, index) => el('option', { value: String(index + 1), text: name })));
    }

    function quantityField() {
        return el('input', { type: 'number', class: 'quantity', 'aria-label': 'Shares', min: '1', step: '1', value: '10' });
    }

    function button(text, primary = false) {
        return el('button', { type: 'button', class: primary ? 'primary-button' : null, text });
    }
}
```

- [ ] **Step 2: Write `web/public/js/event-detail.js`** (wording from `EventDetailView.java`)

```js
/**
 * The selected event in full, pulled with everything else twice a second and redrawn only when what
 * arrives differs from what is shown (EventDetailView). The trade panel sits in a place of its own
 * between the heading and the rest and is never moved, so redrawing never takes the focus away from a
 * field the user is typing in.
 */
import { el, section, wrapping, labelled } from './dom.js';
import { col, num, staticTable } from './table.js';
import { money, moneyOrNothing, shares, percent } from './format.js';
import { ORDER_BOOK } from './market.js';

export function createEventDetail({ api, feed, actions }) {
    const headingBox = el('div');
    const body = el('div', { class: 'stack' });
    const content = el('div', { class: 'stack' }, headingBox, actions, body);
    const root = el('section', { class: 'pane', id: 'eventDetail' }, content);
    let watched = null;
    let shownJson = null;

    feed.every(pull);
    showNothing();
    return { element: root, watch };

    function watch(event) {
        const another = !event || !watched || event.number !== watched.number;
        watched = event;
        if (!event) {
            showNothing();
            return;
        }
        if (another) {
            shownJson = null;
            actions.hidden = false;
            headingBox.replaceChildren(heading(event));
            body.replaceChildren(wrapping('Fetching the event...'));
            root.scrollTop = 0;
            pull().catch(() => { /* the next round of the feed reports it */ });
        }
    }

    function showNothing() {
        shownJson = null;
        actions.hidden = true;
        headingBox.replaceChildren();
        body.replaceChildren(wrapping('Choose an event on the left to see it in full.'));
    }

    function pull() {
        const event = watched;
        if (!event) {
            return Promise.resolve();
        }
        const orderBook = event.methodKind === ORDER_BOOK;
        return (orderBook ? api.orderBook(event.number) : api.lmsrEvent(event.number))
            .then(state => showIfNew(event, state, () => (orderBook ? showOrderBook(state) : showLmsr(state))));
    }

    function showIfNew(askedAbout, state, draw) {
        const json = JSON.stringify(state);
        if (!watched || watched.number !== askedAbout.number || json === shownJson) {
            return;
        }
        const place = root.scrollTop;
        shownJson = json;
        draw();
        root.scrollTop = place;
    }

    function showLmsr(state) {
        headingBox.replaceChildren(heading(state.event));
        const parts = [
            section('Current standing'),
            staticTable([num('#', o => o.number), col('Option', o => o.name), num('Value', o => money(o.value)),
                num('Shares held', o => shares(o.sharesBought))], state.options, ''),
            section('Accounts'),
            labelled('Event account', money(state.eventAccountBalance),
                'Commission collected', money(state.commissionCollected),
                'Market maker holds', money(state.marketMakerBalance),
                'A winning share pays', money(state.payoutPerWinningShare)),
            section('Trading history, newest first'), tradeTable(state.history),
        ];
        if (state.closed) {
            parts.push(section('Result'), labelled('Winning option', state.winningOptionName,
                'Winning shares', shares(state.winningShares), 'Paid out', money(state.totalPaidOut)));
        }
        body.replaceChildren(...parts);
    }

    function showOrderBook(state) {
        headingBox.replaceChildren(heading(state.event));
        body.replaceChildren(
            section('Accounts'),
            labelled('Event account', money(state.eventAccountBalance),
                'Commission collected', money(state.commissionCollected),
                'A winning share pays', money(state.baseValue),
                'Highest price allowed', money(state.highestAllowedPrice),
                'Minting', state.mintAllowed ? 'allowed' : 'not allowed'),
            section('Order books'), el('div', { class: 'books' }, ...state.options.map(optionPane)),
            section('Participants'), participantsTable(state));
    }

    function heading(event) {
        return el('div', { class: 'stack' },
            el('h2', { class: 'detail-title', text: event.name }),
            wrapping(event.description),
            labelled('Status', event.status, 'Type', event.tradingMethod,
                'Commission', `${percent(event.commissionPercent)} — ${event.commissionTiming}`,
                'Market maker', String(event.marketMakerName)),
            el('hr'));
    }

    function optionPane(option) {
        return el('div', { class: 'option-book' },
            el('h4', { class: 'option-title', text: option.name }),
            labelled('Last', moneyOrNothing(option.lastPrice), 'Bid', moneyOrNothing(option.bestBid),
                'Ask', moneyOrNothing(option.bestAsk), 'Mid', moneyOrNothing(option.midPrice),
                'Spread', moneyOrNothing(option.spread), 'Shares in issue', shares(option.sharesInIssue)),
            el('span', { text: 'Buyers' }), orderTable(option.bids, 'Nobody is bidding.'),
            el('span', { text: 'Sellers' }), orderTable(option.asks, 'Nobody is selling.'));
    }

    function participantsTable(state) {
        const columns = [col('Trader', who => who.userName)];
        state.options.forEach((option, index) => columns.push(
            num(option.name, who => shares(who.options[index].shares)),
            num(`${option.name} paid`, who => money(who.options[index].paidFor)),
            num(`${option.name} worth`, who => moneyOrNothing(who.options[index].currentValue))));
        return staticTable(columns, state.participants, 'Nobody has traded here yet.');
    }
}

function orderTable(orders, whenEmpty) {
    return staticTable([col('Trader', order => order.userName), num('Shares', order => shares(order.quantity)),
        num('Price', order => money(order.price))], orders, whenEmpty);
}

/** Trades, newest first; the account tab shows a user's own trades the same way. */
export function tradeTable(trades) {
    return staticTable([col('Option', t => t.optionName), num('Shares', t => shares(t.quantity)),
        num('Cost', t => money(t.sharesCost)), num('Commission', t => money(t.commission)),
        num('Total', t => money(t.totalPaid))], trades, 'Nothing has been traded here yet.');
}
```

- [ ] **Step 3: Write `web/public/js/events.js`** (wording from `EventsController.java`)

```js
/**
 * The events tab: every event on the server on the left with the filters above them, and the selected
 * one on the right with the means of acting on it (EventsController).
 */
import { el } from './dom.js';
import { createTable, col, num } from './table.js';
import { money } from './format.js';
import { ALL, FILTERS, passesFilters, eventsSummary, commissionOf } from './market.js';
import { createTradePanel } from './trade.js';
import { createEventDetail } from './event-detail.js';

export function createEventsTab({ api, feed, messenger }) {
    const trade = createTradePanel({ api, feed, messenger });
    const detail = createEventDetail({ api, feed, actions: trade.element });
    const chosen = Object.fromEntries(FILTERS.map(filter => [filter.field, ALL]));
    const summary = el('p', { class: 'summary' });
    const table = createTable({
        id: 'eventsTable',
        columns: [
            num('#', event => event.number), col('Name', event => event.name), col('Status', event => event.status),
            col('Type', event => event.methodKind), col('Commission', commissionOf),
            num('Account', event => money(event.accountBalance)), col('Market maker', event => event.marketMakerName),
        ],
        keyOf: event => event.number,
        placeholder: 'No events yet. Upload an events file from the JavaFX client.',
        onSelect: showSelected,
    });
    table.element.classList.add('grow');

    const element = el('div', { class: 'split events-split' },
        el('section', { class: 'pane' }, filterBar(), summary, table.element), detail.element);
    feed.on('events', refresh);
    refresh();
    return { element };

    function refresh() {
        const passing = feed.events.filter(event => passesFilters(event, chosen));
        table.show(passing);
        summary.textContent = eventsSummary(passing.length, feed.events.length);
        showSelected();
    }

    function showSelected() {
        const selected = table.selected();
        detail.watch(selected);
        trade.show(selected);
    }

    /** One group of toggles per filter, exactly one always chosen, "All" to begin with. */
    function filterBar() {
        return el('div', { class: 'filters' }, ...FILTERS.map(filter => {
            const buttons = filter.choices.map(choice => el('button', {
                type: 'button', class: 'toggle', 'aria-pressed': String(choice === ALL), text: choice,
            }));
            buttons.forEach((button, index) => button.addEventListener('click', () => {
                chosen[filter.field] = filter.choices[index];
                buttons.forEach(other => other.setAttribute('aria-pressed', String(other === button)));
                refresh();
            }));
            return el('div', { class: 'filter-group' }, el('span', { class: 'filter-caption', text: filter.caption }), ...buttons);
        }));
    }
}
```

- [ ] **Step 4: Put the events tab into `app.js`** — replace the imports line `import { el, tabbed } from './dom.js';` with:

```js
import { el, tabbed } from './dom.js';
import { createEventsTab } from './events.js';
```

and the `marketScreens` function with:

```js
function marketScreens(feedOfThisLogin) {
    const parts = { api, feed: feedOfThisLogin, messenger: shell.messenger };
    return tabbed([
        { name: 'Events', element: createEventsTab(parts).element },
        { name: 'Account', element: el('div') },
        { name: 'Chat', element: el('div') },
    ]);
}
```

- [ ] **Step 5: Run the tests**

Run: `cd web && node --test`
Expected: PASS (no-html now scans the three new modules too).

- [ ] **Step 6: Check it live, side by side with a second user**

Relay and Tomcat as in Task 6. A second user with curl (`-c/-b` cookie jar `$S/u2.txt`): log in as `Dana`, upload `test-files/ex3/<an LMSR file>` and `test-files/ex3/three-options.xml` (or an order-book file) with `curl -F file=@…`. In the browser as `Web1`:
- the events appear within a second; filters narrow them and the summary says "N of M events shown"; the selection stays put across refreshes;
- an LMSR event of Dana's shows "waiting for its market maker, Dana"; Dana deposits and opens it (`curl -X POST "…/account/deposit?amount=1000"`, `curl -X POST "…/events/open?event=N"`) → the web panel switches to Option · Shares · Buy · Sell within a second;
- type `37` into Shares on the web, then have Dana buy from curl (`curl -X POST "…/trade/buy?event=N&option=1&quantity=5"`) → Current standing and the history change while `37` stays in the field and keeps the focus;
- an order-book event: Place order with `abc` shares → "\"abc\" is not a number of shares…", nothing sent; with `0.5.5` as price → the price sentence; Dana's resting order (`curl -X POST "…/trade/order?event=M&option=1&side=buy&quantity=3&price=0.40"`) shows under Buyers;
- trading from the web itself (it needs funds, loaded from the account tab) and the market maker's Open/Close controls are checked in Task 8 and in Task 10's end-to-end run, where the web user logs in under the name that uploaded the events.

- [ ] **Step 7: Commit**

```bash
git add web/public/js/trade.js web/public/js/event-detail.js web/public/js/events.js web/public/js/app.js
git commit -m "Show every event on the web, the chosen one in full, and trade in it"
```

---

### Task 8: The account tab

**Files:**
- Create: `web/public/js/account.js`
- Modify: `web/public/js/app.js` (`marketScreens`)

**Interfaces:**
- Consumes: `createTradePanel` (Task 7), `tradeTable` (Task 7), `feed` topics `users`, `me` (with `(now, was)`), `events`, `ledger` (Task 4), `market.js` (Task 2), `table.js` (Task 6).
- Produces: `createAccountTab({api, feed, messenger}) → {element}`.

- [ ] **Step 1: Write `web/public/js/account.js`** (wording from `AccountController.java`, without the upload strip)

```js
/**
 * The account tab: everybody else on the left with my own account under them, and on the right where
 * I stand in every event, my part in the chosen one, and the means of acting on it (AccountController,
 * without uploading a file, as exercise 4 asks).
 */
import { el, section, wrapping, labelled } from './dom.js';
import { createTable, staticTable, col, num } from './table.js';
import { money, signedMoney, moneyOrNothing, shares } from './format.js';
import { Failure } from './api.js';
import {
    ORDER_BOOK, rolesOf, roleName, holdingSummary, othersThan, justBlocked, describeDeposit, parseAmount,
} from './market.js';
import { createTradePanel } from './trade.js';
import { tradeTable } from './event-detail.js';

export function createAccountTab({ api, feed, messenger }) {
    const trade = createTradePanel({ api, feed, messenger });
    const title = el('h2', { class: 'detail-title' });
    const state = el('span', { class: 'problem' });
    const balance = el('span', { class: 'balance', id: 'balance' });
    const amount = el('input', { type: 'text', id: 'depositAmount', placeholder: 'Amount', 'aria-label': 'Amount', inputmode: 'decimal' });
    const load = el('button', { type: 'button', id: 'depositButton', text: 'Load funds' });
    const partInEvent = el('div', { class: 'stack' });
    let shownPart;

    const others = createTable({
        id: 'usersTable',
        columns: [col('Name', user => user.name), num('Balance', user => money(user.balance)),
            col('Market maker', user => (user.marketMaker ? 'Yes' : 'No'))],
        keyOf: user => user.name,
        placeholder: 'Nobody else has logged in yet.',
    });
    const ledger = createTable({
        id: 'ledgerTable',
        columns: [num('#', line => line.number), col('Description', line => line.description, { wrap: true }),
            num('Amount', line => signedMoney(line.amount)), num('Balance', line => money(line.balanceAfter))],
        placeholder: 'Nothing has happened in your account yet.',
    });
    ledger.element.classList.add('grow');
    const involvement = createTable({
        id: 'involvementTable',
        columns: [col('Event', role => role.event.name), col('Status', role => role.event.status),
            col('Type', role => role.event.methodKind), col('My role', roleName), col('Holding', holdingSummary)],
        keyOf: role => role.event.number,
        placeholder: 'There are no events on the server yet.',
        onSelect: showPartIn,
    });

    const element = el('div', { class: 'split account-split' },
        el('section', { class: 'pane' },
            section('Other users'), others.element,
            section('My account'), ledger.element,
            el('div', { class: 'row' }, balance, el('span', { class: 'spacer' }), amount, load)),
        el('section', { class: 'pane' },
            el('div', { class: 'row' }, title, state), el('hr'),
            section('Events participation and ownership'), involvement.element,
            section('My part in the selected event'), partInEvent,
            section('Act on the selected event'), trade.element));

    feed.on('users', showOthers);
    feed.on('events', showMe);
    feed.on('me', (now, was) => {
        showMe();
        if (justBlocked(was, now)) {
            messenger.warn('Your balance has gone below zero', `Your balance is now ${money(now.balance)}. What you`
                + ' did went through, but from now on you can take no further part in the market.');
        }
    });
    feed.on('ledger', all => {
        ledger.show(all);
        ledger.scrollToEnd();
    });
    load.addEventListener('click', deposit);
    amount.addEventListener('keydown', event => {
        if (event.key === 'Enter') {
            deposit();
        }
    });
    ledger.show(feed.ledger);
    showMe();
    return { element };

    function showOthers() {
        others.show(othersThan(feed.users, feed.me));
    }

    function showMe() {
        const me = feed.me;
        showOthers();
        if (!me) {
            title.textContent = 'Fetching your account...';
            showPartIn(null);
            return;
        }
        title.textContent = me.name;
        state.textContent = me.blocked ? '(blocked — you have spent past zero)' : '';
        balance.textContent = `Balance ${money(me.balance)}`;
        involvement.show(rolesOf(me, feed.events));
        showPartIn(involvement.selected());
    }

    /**
     * What the user's part in the chosen event amounts to: an LMSR event by its run of purchases and
     * sales, an order book by what is held and what it cost; profit or loss once it is decided.
     */
    function showPartIn(role) {
        trade.show(role ? role.event : null);
        const json = JSON.stringify(role);
        if (json === shownPart) {
            return;
        }
        shownPart = json;
        if (!role) {
            partInEvent.replaceChildren(wrapping('Choose one of the events above.'));
            return;
        }
        const part = role.holding;
        if (!part) {
            partInEvent.replaceChildren(wrapping(role.runsIt
                ? 'You run this event but have not traded in it, so you hold nothing here.'
                : 'You have not taken part in this event.'));
            return;
        }
        const pieces = [];
        if (role.event.methodKind !== ORDER_BOOK) {
            pieces.push(wrapping('Everything you have bought and sold here, newest first.'), tradeTable(part.trades));
        }
        pieces.push(staticTable([col('Option', option => option.optionName), num('Shares held', option => shares(option.shares)),
            num('Paid', option => money(option.paidFor)), num('Worth now', option => moneyOrNothing(option.currentValue))],
        part.options, 'Nothing is held here.'));
        pieces.push(role.event.winningOptionName === null
            ? labelled('Commission paid', money(part.commissionPaid))
            : labelled('Commission paid', money(part.commissionPaid), 'Winning option', role.event.winningOptionName,
                'Profit or loss', signedMoney(part.netResult)));
        partInEvent.replaceChildren(...pieces);
    }

    async function deposit() {
        const wanted = parseAmount(amount.value);
        if (wanted.problem) {
            messenger.refused(new Failure(400, wanted.problem));
            return;
        }
        load.disabled = true;
        try {
            const updated = await api.deposit(wanted.value);
            amount.value = '';
            messenger.status(describeDeposit(wanted.value, updated));
            feed.pullEverything();
        } catch (failure) {
            messenger.refused(failure);
        } finally {
            load.disabled = false;
        }
    }
}
```

- [ ] **Step 2: Put the account tab into `app.js`** — add `import { createAccountTab } from './account.js';` after the events import, and in `marketScreens` replace `{ name: 'Account', element: el('div') },` with:

```js
        { name: 'Account', element: createAccountTab(parts).element },
```

- [ ] **Step 3: Run the tests**

Run: `cd web && node --test`
Expected: PASS.

- [ ] **Step 4: Check it live**

As Web1 in the browser, with Dana (curl) around:
- Other users lists Dana with her balance and "Yes" once she runs an event, and never Web1;
- Load funds `abc` → "\"abc\" is not an amount. Please type a number, such as 500."; `-5` → the server's refusal; `500` → "You loaded 500.00. Your balance is 500.00.", a ledger line `+500.00`, the top bar balance updates;
- buy in an LMSR event from the account tab's own panel → ledger lines appear and the table follows the newest; My part shows the trade and holdings; My role "Trading", Holding "Yes 10";
- after Dana closes the event: "Winning option" and "Profit or loss" appear, the Won line in the ledger;
- spending below zero (on-close commission event, see `README.md` decision on going below zero) → the warning dialog once, the panel's "You have spent past zero…".

- [ ] **Step 5: Commit**

```bash
git add web/public/js/account.js web/public/js/app.js
git commit -m "Show the user's account, the other users and every event the user is part of"
```

---

### Task 9: The chat tab

**Files:**
- Create: `web/public/js/chat.js`
- Modify: `web/public/js/app.js` (`marketScreens`, imports)

**Interfaces:**
- Consumes: `feed.on('chat', (all, added))`, `feed.chat` (Task 4), `api.say(message)` (Task 3), `chatLineText` (Task 2).
- Produces: `createChatTab({api, feed, messenger}) → {element}`.

- [ ] **Step 1: Write `web/public/js/chat.js`** (wording from `ChatController.java`)

```js
/**
 * The chat room of exercise 3's bonus, kept in the web client: everybody sees what everybody writes,
 * pulled from the server as a delta like the account's lines (ChatController).
 */
import { el, section } from './dom.js';
import { chatLineText } from './market.js';

export function createChatTab({ api, feed, messenger }) {
    const lines = el('ol', { class: 'chat-lines', id: 'chatLines' });
    const message = el('input', { type: 'text', id: 'chatMessage', placeholder: 'Write to everybody, then press Enter', 'aria-label': 'Message' });
    const send = el('button', { type: 'submit', class: 'primary-button', text: 'Send' });
    const writing = el('form', { class: 'row chat-writing' }, message, send);

    writing.addEventListener('submit', async event => {
        event.preventDefault();
        if (!message.value.trim()) {
            return;
        }
        send.disabled = true;
        try {
            await api.say(message.value);
            message.value = '';
            feed.pullEverything();
        } catch (failure) {
            messenger.refused(failure);
        } finally {
            send.disabled = false;
            message.focus();
        }
    });
    feed.on('chat', show);
    show(feed.chat);
    return { element: el('section', { class: 'pane chat' }, section('Chat with everybody in the market'), lines, writing) };

    function show(all) {
        if (all.length === 0) {
            lines.replaceChildren(el('li', { class: 'placeholder', text: 'Nobody has said anything yet. Say hello.' }));
            return;
        }
        lines.replaceChildren(...all.map(line => el('li', { text: chatLineText(line) })));
        lines.scrollTop = lines.scrollHeight;
    }
}
```

- [ ] **Step 2: Put the chat tab into `app.js`** — add `import { createChatTab } from './chat.js';` after the account import; in `marketScreens` replace `{ name: 'Chat', element: el('div') },` with `{ name: 'Chat', element: createChatTab(parts).element },`; and change `import { el, tabbed } from './dom.js';` to `import { tabbed } from './dom.js';` (`el` is no longer used there).

- [ ] **Step 3: Run the tests**

Run: `cd web && node --test`
Expected: PASS.

- [ ] **Step 4: Check it live**

Send `שלום <b>כולם</b> & "you"` from the web → it appears literally (no bold) on the web and in `curl …/chat?after=0` as Dana; Dana says something with `curl -X POST --data-urlencode "message=hi from curl" …/chat` → it appears on the web within a second; a 501-character message → the server's refusal in the dialog; Enter sends, the field clears.

- [ ] **Step 5: Commit**

```bash
git add web/public/js/chat.js web/public/js/app.js
git commit -m "Keep the chat in the web client"
```

---

### Task 10: Tests in `test.bat`, the readme, packaging, and the end-to-end check

**Files:**
- Modify: `test.bat` (labels `[n/4]` → `[n/5]`, a fifth step), `docs/make-readme-docx.py` (optional source/target), `.gitignore` (`docs/readme-web.docx`), `docs/superpowers/specs/2026-10-11-web-client-design.md` (`package-web.bat` instead of a `package.bat` target)
- Create: `web/README.md`, `package-web.bat`

**Interfaces:**
- Consumes: everything above; `build.bat`'s `build\guess-market.war`.
- Produces: `dist\guess-market-web.zip`; `docs\readme-web.docx` (gitignored).

- [ ] **Step 1: `test.bat` runs the web tests** — change the four step labels `[1/4]`…`[4/4]` to `[1/5]`…`[4/5]`, and replace the line `exit /b %ERRORLEVEL%` that follows the JUnit run with:

```bat
set RESULT=%ERRORLEVEL%

echo [5/5] Running the web client's tests...
where node >nul 2>&1
if errorlevel 1 (
    echo Node.js was not found, so the web client's tests were skipped.
    exit /b %RESULT%
)
pushd "%ROOT%web"
node --test
if errorlevel 1 set RESULT=1
popd
exit /b %RESULT%
```

Run: `cmd //c "C:\Users\User\IdeaProjects\guessMarket\test.bat"` with `JAVA_HOME` set.
Expected: the 197 offline Java tests pass (the 7 live ones skip without `GM_SERVER`), then the web tests pass; exit code 0.

- [ ] **Step 2: `make-readme-docx.py` takes an optional source and target** — change `def build(identityNumber):` to `def build(identityNumber, source_path=SOURCE, target_path=TARGET):`, read `source_path` instead of `SOURCE` at its first line and save to `target_path` instead of `TARGET` (and print it), and replace the `__main__` block with:

```python
if __name__ == "__main__":
    arguments = sys.argv[1:]
    if len(arguments) not in (1, 3) or not arguments[0].isdigit():
        raise SystemExit("usage: python docs/make-readme-docx.py <identity-number> [<source.md> <target.docx>]")
    if len(arguments) == 1:
        build(arguments[0])
    else:
        build(arguments[0], Path(arguments[1]).resolve(), Path(arguments[2]).resolve())
```

Also add to the module docstring: `For exercise 4: python docs/make-readme-docx.py <identity-number> web/README.md docs/readme-web.docx`.

Verify the ex3 output is unchanged: build `docs/readme.docx` before the change (copy it aside), rebuild after, and compare `word/document.xml` of the two with Python's `zipfile` — identical bytes.

- [ ] **Step 3: `.gitignore`** — under `docs/readme.docx` add:

```
docs/readme-web.docx
```

- [ ] **Step 4: Ask Eyal the AI-work questions** — the readme's personal answers are his. Ask, in one message: which AI tools he tried besides Claude Code and why this one; his front-end experience before this exercise; roughly how long the web client took and how long he thinks it would have taken without AI; whether he intervened in the code or only wrote prompts, in his own estimate; whether this way of working was enjoyable compared with the other exercises. Write his answers into Step 5's section in his words.

- [ ] **Step 5: Write `web/README.md`** (Hebrew, like `README.md`; this is the ex4 readme, separate from ex3's). Sections, in this order:
  1. `# Guess Market — תרגיל 4 (בונוס): קליינט WEB`, one paragraph: a browser client for the unchanged ex3 server, same API.
  2. **כתובת הכניסה** — in bold, first thing: `http://localhost:3000/` (opened automatically by `start.bat`).
  3. **פרטי המגיש** — the same table as `README.md` (row `שם מלא` first, so `make-readme-docx.py` can stamp the ID), and the GitHub link `https://github.com/Eyal98/guessMarket`, tag `ex4-submission`.
  4. **הפעלה** — (1) `guess-market.war` (the unchanged ex3 WAR, included) into Tomcat's `webapps`, `bin\startup.bat`; (2) `web\start.bat` — needs Node.js only, installs nothing, opens the browser; (3) other Tomcat/port: `start.bat http://localhost:8081/guess-market 3001`; (4) a second web user: a private window or another browser (one browser = one session); (5) closing the tab logs out at once, reload stays logged in; (6) the JavaFX client of ex3 works alongside; uploading files is done from it, as the exercise excludes upload from the web.
  5. **מה יש בקובץ ההגשה** — the zip tree from the spec §7.
  6. **מה הקליינט עושה** — the four screens; a note that Chat (the ex3 bonus) is included although not required.
  7. **איך זה בנוי** — why a relay (cookie + same origin, CORS would change the server); the module table (`relay.js`, `serve.js`, `api.js`, `feed.js`, `market.js`, `format.js`, `table.js`, `dom.js`, `shell.js`, `session.js`, `login.js`, `events.js`, `event-detail.js`, `trade.js`, `account.js`, `chat.js`, `app.js`), each one line and its JavaFX twin.
  8. **החלטות** — plain JS (no install, no build); the relay; text never as HTML; one trade panel module used twice; the situation key; logout beacon + silent re-login once; money formatting identical to Java's (the 1.005 case).
  9. **בדיקות** — the `node:test` files and counts, how to run (`cd web && node --test`, or `test.bat`), and the live checks performed (Task 10 Step 9).
  10. **עבודה עם כלי AI** — factual: built with Claude Code (Anthropic) in the Claude desktop app, from a design written and approved first, then a plan, then test-first implementation; where it needed correcting (record the real ones from this work, e.g. anything the live checks caught); then Eyal's answers from Step 4.

- [ ] **Step 6: Write `package-web.bat`** (CRLF)

```bat
@echo off
rem ---------------------------------------------------------------------------
rem  Assembles the exercise 4 submission - the web client - on its own, leaving
rem  the exercise 3 one (package.bat) alone.
rem
rem  Produces:  dist\guess-market-web\      the unpacked submission
rem             dist\guess-market-web.zip
rem
rem  Inside it:
rem    guess-market.war   the exercise 3 server, unchanged
rem    web\               start.bat, serve.js, relay.js, package.json, public\
rem    readme.docx        the exercise 4 readme (docs\readme-web.docx)
rem    sample-files\      events files for a JavaFX client to upload
rem    source\web\        the web client's source and its tests
rem
rem  Run it after package.bat, never before it: package.bat empties dist.
rem ---------------------------------------------------------------------------
setlocal

set ROOT=%~dp0
set DIST=%ROOT%dist
set STAGE=%DIST%\guess-market-web

call "%ROOT%build.bat"
if errorlevel 1 (
    echo.
    echo The build failed, so nothing was packaged.
    exit /b 1
)

if not exist "%ROOT%docs\readme-web.docx" (
    echo.
    echo Cannot find docs\readme-web.docx. Make it with:
    echo   python docs\make-readme-docx.py ^<identity-number^> web\README.md docs\readme-web.docx
    exit /b 1
)

where node >nul 2>&1
if errorlevel 1 (
    echo.
    echo Node.js is needed to test the web client before packaging it.
    exit /b 1
)
echo.
echo Testing the web client...
pushd "%ROOT%web"
node --test
if errorlevel 1 (
    popd
    echo.
    echo The web client's tests failed, so nothing was packaged.
    exit /b 1
)
popd

echo.
echo Gathering the submission...
if exist "%STAGE%" rmdir /s /q "%STAGE%"
if exist "%DIST%\guess-market-web.zip" del /q "%DIST%\guess-market-web.zip"
mkdir "%STAGE%\web"

copy /y "%ROOT%build\guess-market.war" "%STAGE%\" >nul
for %%F in (start.bat serve.js relay.js package.json) do copy /y "%ROOT%web\%%F" "%STAGE%\web\" >nul
xcopy /e /i /q /y "%ROOT%web\public" "%STAGE%\web\public" >nul
copy /y "%ROOT%docs\readme-web.docx" "%STAGE%\readme.docx" >nul
xcopy /e /i /q /y "%ROOT%test-files\ex3" "%STAGE%\sample-files" >nul
xcopy /e /i /q /y "%ROOT%web" "%STAGE%\source\web" >nul

rem The JDK's own jar tool writes the zip, as package.bat explains.
echo Zipping...
if defined JAVA_HOME (set JAR="%JAVA_HOME%\bin\jar") else (set JAR=jar)
%JAR% --create --no-manifest --file "%DIST%\guess-market-web.zip" -C "%DIST%" guess-market-web
if errorlevel 1 (
    echo.
    echo The zip could not be written.
    exit /b 1
)

echo.
echo Packaged into "%DIST%\guess-market-web.zip".
echo Unpacked copy is in "%STAGE%".
exit /b 0
```

Update the spec's §2 and §7 to name `package-web.bat` (and drop "`package.bat` grows a second target").

- [ ] **Step 7: Build the readme and the zip**

Run: `python docs/make-readme-docx.py <id> web/README.md docs/readme-web.docx` (Eyal's number, passed on the command line only), then `cmd //c "C:\Users\User\IdeaProjects\guessMarket\package-web.bat"` with `JAVA_HOME` set.
Expected: "Packaged into …\dist\guess-market-web.zip". `git status` shows no `.docx`.

- [ ] **Step 8: Prove the WAR is exercise 3's** — `git diff --stat ce04b55 HEAD -- server engine dto lib build.bat` is empty, and the class files inside `dist/guess-market-web/guess-market.war` equal those of a WAR built from `ce04b55` (compare entry names and CRC32 with Python's `zipfile`).

- [ ] **Step 9: End to end from a clean folder**

Unzip `dist/guess-market-web.zip` into a scratch folder whose path has a space. Unpack Tomcat 10.1 from `tools/downloads` into scratch, set its connector to 8083, copy the zip's `guess-market.war` into its `webapps`, start it. Run the zip's `web\start.bat http://localhost:8083/guess-market 3000` (from a different current directory). Then:
- the in-app browser as web user `Web1`; the ex3 JavaFX client (`build/client/run.bat http://localhost:8083/guess-market`) or curl as `Dana`;
- Dana uploads an LMSR file and an order-book file → both appear on the web within a second;
- Dana opens them; Web1 loads 500, buys and sells on LMSR, places resting and matching orders on the order book; Dana sees Web1's trades; Dana closes both → Web1's ledger shows the Won lines, My part shows profit or loss;
- Web1 as market maker: log Dana out, log in on the web as `Dana` → Open/Close controls appear on her events;
- chat both ways; 409 for a name in use; reload stays logged in; closing the tab frees the name for JavaFX at once;
- stop Tomcat → banner; start it → back to login with the session-ended sentence or silently re-logged in.
Stop the relay and Tomcat afterwards.

- [ ] **Step 10: Commit**

```bash
git add test.bat docs/make-readme-docx.py .gitignore web/README.md package-web.bat docs/superpowers/specs/2026-10-11-web-client-design.md
git commit -m "Package the web client as exercise 4, with its own readme"
```

- [ ] **Step 11: Report and ask** — tell Eyal what was verified and ask before pushing `main` and creating the `ex4-submission` tag. Do not push or tag without his yes.
