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
        return el('div', { class: 'option-book card' },
            el('h4', { class: 'option-title', text: option.name }),
            labelled('Last', moneyOrNothing(option.lastPrice), 'Bid', moneyOrNothing(option.bestBid),
                'Ask', moneyOrNothing(option.bestAsk), 'Mid', moneyOrNothing(option.midPrice),
                'Spread', moneyOrNothing(option.spread), 'Shares in issue', shares(option.sharesInIssue)),
            el('span', { class: 'caption', text: 'Buyers' }), orderTable(option.bids, 'Nobody is bidding.'),
            el('span', { class: 'caption', text: 'Sellers' }), orderTable(option.asks, 'Nobody is selling.'));
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
