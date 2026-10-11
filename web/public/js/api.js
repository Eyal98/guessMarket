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

/** How long a request may take before it counts as unanswered, as OkHttp's default in the JavaFX client. */
const PATIENCE_MS = 10000;

export function createApi(base, fetchImpl = (...args) => globalThis.fetch(...args), { timeoutMs = PATIENCE_MS } = {}) {
    const url = (path, parameters = {}) => {
        const query = new URLSearchParams(parameters).toString();
        return `${base}/${path}${query ? `?${query}` : ''}`;
    };

    async function send(method, path, parameters, body) {
        let response;
        let text;
        try {
            response = await fetchImpl(url(path, parameters), {
                method, body, credentials: 'same-origin', headers: { Accept: 'application/json' },
                signal: AbortSignal.timeout(timeoutMs),
            });
            text = await response.text();
        } catch (cause) {
            if (cause?.name === 'TimeoutError' || cause?.name === 'AbortError') {
                throw new Failure(UNREACHABLE, `The Guess Market server did not answer within ${timeoutMs / 1000}`
                    + ' seconds.');
            }
            throw new Failure(UNREACHABLE, 'The web client cannot reach its own relay. Please check that the'
                + ' window start.bat opened is still running.');
        }
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
