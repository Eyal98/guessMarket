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
