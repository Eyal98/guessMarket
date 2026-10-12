# Exercise 4 — the web client: design

Date: 2026-10-11. Exercise 4 of the MTA Java course (Guess Market): a bonus worth 10 points, due
15.10.26, submitted in a box of its own apart from exercise 3.

## 1. What the exercise asks, and what we add

From the specification (`Guess Market - v3.docx`, section "תרגיל 4"):

- A web client — HTML, CSS and JavaScript in a browser — that works against the exercise 3 server
  as it is, through exactly the same API the JavaFX client uses. The work is on the client side only.
- Three screens: **login** (as in the JavaFX client), **events** (similar in make-up and look to the
  JavaFX events screen) and **user** (similar to the JavaFX user screen, without uploading a file).
- Bonuses of exercise 3 are not expected in the web client.
- The client must not change how Tomcat is set up or run. Node.js may be assumed installed and
  `npm install` may run inside a bat; nothing else may be asked of the checker.
- A readme of its own, stating the exact URL to open and describing the work with AI tools (which
  tool and why, other tools tried, where the AI fell short, how much was AI alone, time taken and
  the estimate without AI, prior front-end experience, whether it was enjoyable).

Decided with Eyal on 2026-10-11:

- **Plain JavaScript** (ES modules), no framework and no build step.
- **The look blends the JavaFX client with polymarket.com** (Polymarket asked for during the build; the
  blend decided at the requirements check, since the events screen must look like the JavaFX one): the
  JavaFX colours — navy bar, navy and blue titles, grey desk and status line — drawn Polymarket's way,
  with white rounded cards, pill filters and green and red tints for buying and selling. The make-up of
  each screen stays the JavaFX one.
- **The chat of exercise 3 is kept** in the web client although the exercise does not require it.
- **The ex4 zip carries the unchanged exercise 3 WAR**, so it can be checked on its own.

## 2. What does not change

Exercise 3 stays exactly as submitted: the server and its WAR, the engine, the DTOs, the JavaFX
client, the 204 tests, `build.bat`, `test.bat`'s existing steps, `README.md`, the `ex3-submission`
tag (`ce04b55`) and `dist\guess-market.zip`. Everything new lives under `web/`. The only shared files
that grow are `test.bat` (an added step for the web tests), `docs/make-readme-docx.py` (optional
arguments for the second readme, its ex3 output unchanged) and `.gitignore` (the second docx, which
carries the identity number, stays out of git like the first). The ex4 zip is built by a new `package-web.bat`; `package.bat`
itself is not touched.

## 3. Architecture and running

```
browser ──► http://localhost:3000/                web/public/*   (HTML, CSS, JS modules)
        └─► http://localhost:3000/guess-market/*  ──relay──►  http://localhost:8080/guess-market/*
                                                              (Tomcat, the ex3 WAR unchanged)
```

A browser sends the session cookie only to the address its page came from, and a page served from
anywhere but Tomcat could reach Tomcat only if the server added CORS headers — a server change the
exercise forbids. Putting the pages inside the WAR would change the ex3 server too. So a small relay
serves the pages and passes the API through, and to the browser everything is one address.

- **`web/serve.js`** — one file using only Node's own modules (`http`, `fs`, `path`, `url`); there is
  no `package.json` and nothing to install.
  - Serves `web/public/` at `/`. Only files inside `public/` are served; `..` and anything that
    resolves outside it is refused with 404. No directory listings. `Cache-Control: no-store`.
  - Passes everything under the server's context path (`/guess-market/*`) through to Tomcat
    untouched — method, path, query, body, headers, status and `Set-Cookie` — dropping only the
    hop-by-hop headers. Tomcat's `JSESSIONID` (path `/guess-market`) therefore works exactly as it
    does for the JavaFX client. The relay knows nothing of the API, so it cannot drift from it.
  - Tomcat unreachable → `502` with an `ErrorDto`-shaped JSON body, which the page shows as
    "Cannot reach the server".
  - `GET /config.json` tells the page the API's path (the path of the server address,
    `/guess-market` by default).
- **`web/start.bat [server-address] [port]`** — checks that `node` exists, starts the relay and opens
  the browser at `http://localhost:3000/`. The server address defaults to
  `http://localhost:8080/guess-market`, as the exercise allows; the port defaults to 3000. A taken
  port or a missing Node gives a sentence, not a stack trace. Works from any folder, including one
  with spaces in its path.
- **One browser = one session = one user.** A second web user needs a private window or another
  browser; the readme says so.

## 4. The screens

The page mirrors the JavaFX window: a top bar with "Guess Market", the user's name and balance and
**Log out**; three tabs — **Events**, **Account**, **Chat**; a status line at the bottom. Captions
and messages are the JavaFX client's own wording.

**Login** — one name field and **Enter**. An empty name, one over 30 characters, or one in use
(`409`) shows the server's own message under the field. On load the page asks `GET /account`; if the
browser is already logged in it skips the login.

**Events** (mirrors `EventsController`, `EventDetailView`, `TradePanel`):
- Left: filters Type / Status / Commission, a summary line, and the events table — # · Name · Status
  · Type · Commission · Account · Market maker.
- Right, for the selected event: a heading (Status, Type, Commission, Market maker), then
  - **LMSR**: Current standing (Option · Value · Shares held), Accounts (Event account, Commission
    collected, Market maker holds, A winning share pays), Trading history newest first (Option ·
    Shares · Cost · Commission · Total), and Result once closed (Winning option, Winning shares,
    Paid out);
  - **Order book**: Accounts (Event account, Commission collected, A winning share pays, Highest
    price allowed, Minting), one book per option two to a row (Last · Bid · Ask · Mid · Spread ·
    Shares in issue; Buyers and Sellers: Trader · Shares · Price), and Participants;
  - the trade panel.
- **Trade panel** (one module, used on both the Events and Account tabs, as JavaFX reuses
  `TradePanel`): the market maker sees **Open this event** (with its cost) before the event starts
  and **Close on this outcome** with a winner choice while it trades; others see Option · Shares ·
  **Buy** · **Sell** on LMSR, or Option · Side · Shares · Price · **Place order** on an order book;
  otherwise the JavaFX sentences ("waiting for its market maker", "closed, decided on …", "you have
  spent past zero …"). Success messages are the JavaFX ones ("You bought …", "Your order is waiting
  in the book.", …).

**Account** (mirrors `AccountController`, without upload):
- Other users — Name · Balance · Market maker.
- My account — the ledger (# · Description · Amount · Balance; the description wraps, the newest
  line stays in view) and Amount + **Load funds**.
- Events participation and ownership — Event · Status · Type · My role · Holding; under it, My part
  in the selected event (Option · Shares held · Paid · Worth now, commission paid, net result) and
  Act on the selected event (the trade panel).
- Going below zero raises the same one-time alert as JavaFX.

**Chat** (mirrors `ChatController`): "Chat with everybody in the market", the lines (time
`HH:mm:ss` · name · text), and a field "Write to everybody, then press Enter" with **Send**. Enter
sends; the server's limit of 500 characters and its refusals show in the status line.

**Layout** — CSS grid and flex: the two panes of a tab sit side by side when the window is wide and
stack when it is narrow; tables scroll inside their pane, never the page. The colours are the JavaFX
client's, drawn the Polymarket way (see §1).

## 5. Modules and data flow

`web/public/js/`, ES modules, one job each:

| Module | Job |
|---|---|
| `api.js` | The only module that knows URLs: one function per endpoint over `fetch` (same-origin, so the cookie rides along). Every answer becomes data or a `Failure {status, message, problems, sessionLost, unreachable}`. |
| `feed.js` | The web `MarketFeed`: every 500 ms it pulls events, users and account whole, the ledger and chat as deltas (`after=n`), and the selected event (`/events/lmsr` or `/events/book`). At most one request per kind in flight; a value is published only when its JSON differs from the last one. |
| `format.js` | Money, signed money, shares, percent — the same strings as `Format.java`. |
| `table.js` | One table builder for every table; rebuilds keep the selected row by its key (event number, user name). |
| `login.js`, `events.js`, `account.js`, `trade.js`, `chat.js` | The screens. |
| `app.js` | Starts everything; switches between login and the market; owns the status line. |
| `session.js` | Log out, close-tab and reload handling (below). |

The pure decisions — formatting, which row stays selected, the trade panel's situation key, a
user's role in an event, the holding summary, the feed's in-flight and changed-only rules — are
functions apart from the DOM, so Node can test them.

**Keeping what the user is doing**: tables keep their selection across refreshes; the trade panel
is rebuilt only when its situation key changes (event state, role, blocked), so a refresh never
clears a half-typed quantity; nothing redraws when nothing changed.

**Errors**: a refusal (`400`, `409`) shows the server's message in the status line and the form
keeps its input; an unreachable server shows a banner until the next pull succeeds. Every piece of
server text is inserted as text (`textContent`), never as HTML.

**Session**:
- Log out — `POST /logout`, back to login.
- Closing the tab — `navigator.sendBeacon` to `/logout` frees the name at once, so it can be reused
  in JavaFX without waiting out the 2-minute session timeout.
- Reload — the beacon fires on reload too, so the name is kept in `sessionStorage` and the reloaded
  page logs back in silently.
- A `401` on a pull — one silent re-login with the remembered name; if that fails (for instance the
  name was taken meanwhile), back to login with the name filled in and the reason shown.

## 6. Testing

- **Unit tests** with Node's own `node:test` (no install), in `web/test/`, written first:
  `format.js` against the strings `Format.java` gives; the feed's in-flight and changed-only rules and
  the ledger and chat deltas appended in order; table selection kept by key; the trade panel's
  situation key; role and holding summaries.
- **The relay** against a stand-in Tomcat made with `http.createServer`: method, query, body and
  `Set-Cookie` pass through; paths outside `public/` and the context path are refused, `..` included;
  an unreachable Tomcat gives `502` with a message.
- `test.bat` runs the web tests after the Java ones when Node is present.
- **Live end to end** on Tomcat 10.1 (port 8083): the in-app browser as a web user and the JavaFX
  script harness as a second user — log in; a JavaFX upload appears on the web within a second; open,
  trade both ways and close; ledger and deposit; chat both ways; `409` for a name in use; reload and
  closed-tab behaviour.
- **Clean folder**: the ex4 zip unpacked into a path with spaces, Tomcat from scratch, `start.bat`.

## 7. Packaging and the readme

`package-web.bat` builds `dist\guess-market-web.zip`:

```
guess-market.war   the exercise 3 server, unchanged
client\            the exercise 3 JavaFX client, unchanged, to upload events files with
web\               start.bat, serve.js, public\
readme.docx        how to run, the exact URL (http://localhost:3000/), the classes, the AI work
sample-files\      the ex3 event files, for a JavaFX client to upload and the web client to show
source\            the web client's source and its tests
```

The readme is written as `web/README.md` (Hebrew, like exercise 3) and turned into the docx by
`docs/make-readme-docx.py`; the identity number is passed as an argument and the docx is gitignored,
as for exercise 3. It opens with the URL and how to run, mentions that the chat is included though
not required, documents the modules, and links to GitHub. Its AI section states the facts of the
work (Claude Code; what it built; where it needed correcting); the personal answers — prior
front-end experience, other tools tried, time estimates, whether it was enjoyable — come from Eyal
and are not invented.

The work lands as new commits on `main` after `ce04b55`, tagged `ex4-submission` once verified and
approved. Nothing is pushed without asking.
