/**
 * Starts the page: finds out where the API is, sees whether this browser is already logged in, and
 * moves between the login screen and the market — including when the server forgets the session.
 */
import { createApi } from './api.js';
import { createFeed } from './feed.js';
import { createShell } from './shell.js';
import { showLogin } from './login.js';
import { tabbed } from './dom.js';
import { createEventsTab } from './events.js';
import { createAccountTab } from './account.js';
import { createChatTab } from './chat.js';
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

function marketScreens(feedOfThisLogin) {
    const parts = { api, feed: feedOfThisLogin, messenger: shell.messenger };
    return tabbed([
        { name: 'Events', element: createEventsTab(parts).element },
        { name: 'Account', element: createAccountTab(parts).element },
        { name: 'Chat', element: createChatTab(parts).element },
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
    const name = remembered() ?? '';
    forget();
    login(SESSION_ENDED, name);
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
