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
    /** Once stopped, a feed is done: answers still on their way are dropped, nothing more is sent. */
    let stopped = false;
    let reachable = true;
    const cutOff = new Set();

    const tell = (topic, ...values) => {
        if (!stopped) {
            listeners[topic].forEach(listener => listener(...values));
        }
    };

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

    /**
     * The server counts as reachable while no kind of pull is failing to reach it. Judging by the last
     * answer alone would flicker: one pull failing and the next succeeding would say "gone, back, gone".
     */
    function noteReach(pull, failure) {
        if (failure?.unreachable) {
            cutOff.add(pull);
        } else {
            cutOff.delete(pull);
        }
        const ok = cutOff.size === 0;
        if (ok !== reachable) {
            reachable = ok;
            tell('reachable', ok, failure);
        }
    }

    function failed(pull, failure) {
        noteReach(pull, failure);
        if (failure?.sessionLost) {
            if (running) {
                stop();
                onSessionLost();
            }
        } else if (!(failure?.status >= 0)) {
            console.error(failure);
        }
    }

    /** Starts a pull at once, turning a mistake thrown before its first await into a failure. */
    function begin(pull) {
        try {
            return Promise.resolve(pull());
        } catch (mistake) {
            return Promise.reject(mistake);
        }
    }

    /** One kind of pull, never more than one at a time. */
    function guarded(pull) {
        let underway = false;
        let askedAgain = false;
        return function run() {
            if (stopped) {
                return;
            }
            if (underway) {
                askedAgain = true;
                return;
            }
            underway = true;
            begin(pull)
                .then(() => noteReach(pull), failure => failed(pull, failure))
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
        stopped = true;
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
