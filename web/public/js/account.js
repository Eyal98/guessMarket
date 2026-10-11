/**
 * The account tab: everybody else on the left with my own account under them, and on the right where
 * I stand in every event, my part in the chosen one, and the means of acting on it (AccountController,
 * without uploading a file, as exercise 4 asks).
 */
import { el, section, wrapping, labelled } from './dom.js';
import { createTable, staticTable, col, num, statusTone, moneyTone } from './table.js';
import { money, signedMoney, moneyOrNothing, shares } from './format.js';
import { Failure } from './api.js';
import {
    ORDER_BOOK, rolesOf, roleName, holdingSummary, othersThan, justBlocked, describeDeposit, parseAmount, isDecided,
} from './market.js';
import { createTradePanel } from './trade.js';
import { tradeTable } from './event-detail.js';

export function createAccountTab({ api, feed, messenger }) {
    const trade = createTradePanel({ api, feed, messenger });
    const title = el('h2', { class: 'detail-title' });
    const state = el('span', { class: 'problem' });
    const balance = el('span', { class: 'balance', id: 'balance' });
    const amount = el('input', { type: 'text', id: 'depositAmount', placeholder: 'Amount', 'aria-label': 'Amount', inputmode: 'decimal' });
    const load = el('button', { type: 'button', id: 'depositButton', class: 'primary-button', text: 'Load funds' });
    const partInEvent = el('div', { class: 'stack' });
    let shownPart;

    const others = createTable({
        id: 'usersTable',
        columns: [col('Name', user => user.name), num('Balance', user => money(user.balance)),
            col('Market maker', user => (user.marketMaker ? 'Yes' : 'No'))],
        keyOf: user => user.name,
        placeholder: 'Nobody else has logged in yet.',
    });
    const ledger = createTable({
        id: 'ledgerTable',
        columns: [num('#', line => line.number), col('Description', line => line.description, { wrap: true }),
            num('Amount', line => signedMoney(line.amount), { tone: line => moneyTone(line.amount) }),
            num('Balance', line => money(line.balanceAfter))],
        placeholder: 'Nothing has happened in your account yet.',
    });
    ledger.element.classList.add('grow');
    const involvement = createTable({
        id: 'involvementTable',
        columns: [col('Event', role => role.event.name),
            col('Status', role => role.event.status, { tone: role => statusTone(role.event.status) }),
            col('Type', role => role.event.methodKind), col('My role', roleName), col('Holding', holdingSummary)],
        keyOf: role => role.event.number,
        placeholder: 'There are no events on the server yet.',
        onSelect: showPartIn,
    });

    const element = el('div', { class: 'split account-split' },
        el('section', { class: 'pane' },
            section('Other users'), others.element,
            section('My account'), ledger.element,
            el('div', { class: 'row' }, balance, el('span', { class: 'spacer' }), amount, load)),
        el('section', { class: 'pane' },
            el('div', { class: 'row' }, title, state), el('hr'),
            section('Events participation and ownership'), involvement.element,
            section('My part in the selected event'), partInEvent,
            section('Act on the selected event'), trade.element));

    feed.on('users', showOthers);
    feed.on('events', showMe);
    feed.on('me', (now, was) => {
        showMe();
        if (justBlocked(was, now)) {
            messenger.warn('Your balance has gone below zero', `Your balance is now ${money(now.balance)}. What you`
                + ' did went through, but from now on you can take no further part in the market.');
        }
    });
    feed.on('ledger', all => {
        ledger.show(all);
        ledger.scrollToEnd();
    });
    load.addEventListener('click', deposit);
    amount.addEventListener('keydown', event => {
        if (event.key === 'Enter') {
            deposit();
        }
    });
    ledger.show(feed.ledger);
    showMe();
    return { element };

    function showOthers() {
        others.show(othersThan(feed.users, feed.me));
    }

    function showMe() {
        const me = feed.me;
        showOthers();
        if (!me) {
            title.textContent = 'Fetching your account...';
            showPartIn(null);
            return;
        }
        title.textContent = me.name;
        state.textContent = me.blocked ? '(blocked — you have spent past zero)' : '';
        balance.textContent = `Balance ${money(me.balance)}`;
        involvement.show(rolesOf(me, feed.events));
        showPartIn(involvement.selected());
    }

    /**
     * What the user's part in the chosen event amounts to: an LMSR event by its run of purchases and
     * sales, an order book by what is held and what it cost; profit or loss once it is decided.
     */
    function showPartIn(role) {
        trade.show(role ? role.event : null);
        const json = JSON.stringify(role);
        if (json === shownPart) {
            return;
        }
        shownPart = json;
        if (!role) {
            partInEvent.replaceChildren(wrapping('Choose one of the events above.'));
            return;
        }
        const part = role.holding;
        if (!part) {
            partInEvent.replaceChildren(wrapping(role.runsIt
                ? 'You run this event but have not traded in it, so you hold nothing here.'
                : 'You have not taken part in this event.'));
            return;
        }
        const pieces = [];
        if (role.event.methodKind !== ORDER_BOOK) {
            pieces.push(wrapping('Everything you have bought and sold here, newest first.'), tradeTable(part.trades));
        }
        pieces.push(staticTable([col('Option', option => option.optionName), num('Shares held', option => shares(option.shares)),
            num('Paid', option => money(option.paidFor)), num('Worth now', option => moneyOrNothing(option.currentValue))],
        part.options, 'Nothing is held here.'));
        pieces.push(!isDecided(role.event)
            ? labelled('Commission paid', money(part.commissionPaid))
            : labelled('Commission paid', money(part.commissionPaid), 'Winning option', role.event.winningOptionName,
                'Profit or loss', signedMoney(part.netResult)));
        partInEvent.replaceChildren(...pieces);
    }

    async function deposit() {
        const wanted = parseAmount(amount.value);
        if (wanted.problem) {
            messenger.refused(new Failure(400, wanted.problem));
            return;
        }
        load.disabled = true;
        try {
            const updated = await api.deposit(wanted.value);
            amount.value = '';
            messenger.status(describeDeposit(wanted.value, updated));
            feed.pullEverything();
        } catch (failure) {
            messenger.refused(failure);
        } finally {
            load.disabled = false;
        }
    }
}
