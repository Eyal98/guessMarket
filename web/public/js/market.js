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

/**
 * Whether an event has been decided. The server writes JSON with Gson, which leaves a null field out
 * altogether, so an undecided event has no winningOptionName at all rather than a null one.
 */
export function isDecided(event) {
    return event.winningOptionName !== null && event.winningOptionName !== undefined;
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
