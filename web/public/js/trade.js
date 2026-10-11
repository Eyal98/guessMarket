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
    const root = el('div', { class: 'trade-panel card' });
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
        const open = button('Open this event', 'primary-button');
        open.addEventListener('click', () => act(open, () => api.open(event.number), describeOpening));
        return el('div', { class: 'stack' },
            wrapping(`You run this event. Opening it costs ${money(event.openingCost)}, paid from your own account.`),
            el('div', {}, open));
    }

    function lmsrControls() {
        const option = optionChoice('Option');
        const quantity = quantityField();
        const buy = button('Buy', 'buy-button');
        const sell = button('Sell', 'sell-button');
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
        const place = button('Place order', 'primary-button');
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
        // Text rather than a number box: a number box empties itself on "abc", and the refusal could
        // then not say what was typed.
        return el('input', { type: 'text', class: 'quantity', 'aria-label': 'Shares', inputmode: 'numeric', value: '10' });
    }

    function button(text, kind = null) {
        return el('button', { type: 'button', class: kind, text });
    }
}
