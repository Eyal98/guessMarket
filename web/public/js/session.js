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

/**
 * Whether a lost session may be recovered without asking. A reload's own logout beacon can reach the
 * server just after the reloaded page logged back in; that race is the only reason, so it is allowed
 * once, and only before the page has had a working session. Any later loss — Tomcat restarted, the
 * session timed out — goes back to the login screen, as in the JavaFX client.
 */
export function maySilentlyReenter({ remembered: name, alreadyTried, settled }) {
    return Boolean(name) && !alreadyTried && !settled;
}

/** What the login screen says when logging back in by itself failed: the server's own reason. */
export function reentryRefusal(failure, sessionEnded) {
    return failure.sessionLost ? sessionEnded : failure.message;
}

export function logOutWhenLeaving(logoutUrl) {
    window.addEventListener('pagehide', () => {
        if (remembered()) {
            navigator.sendBeacon(logoutUrl);
        }
    });
}
