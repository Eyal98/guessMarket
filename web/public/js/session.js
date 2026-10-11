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
