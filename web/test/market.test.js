import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
    ALL, passesFilters, eventsSummary, commissionOf, rolesOf, roleName, holdingSummary, othersThan,
    justBlocked, tradeSituation, describeOpening, describeClosing, describePurchase, describeSale,
    describeOrder, describeDeposit, parseQuantity, parsePrice, parseAmount, chatLineText, isDecided,
} from '../public/js/market.js';

const event = (fields = {}) => ({
    number: 1, name: 'Rain', description: 'Will it rain?', commissionPercent: 3, commissionType: 'on-purchase',
    commissionTiming: 'charged on every purchase', optionNames: ['Yes', 'No'], status: 'Active',
    tradingMethod: 'LMSR (b = 100)', methodKind: 'LMSR', marketMakerName: 'Dana', accountBalance: 69.31,
    openingCost: 69.31, winningOptionName: null, ...fields,
});
const me = (fields = {}) => ({ name: 'Avi', balance: 100, blocked: false, marketMakerOf: [], participations: [], ...fields });
const everything = { methodKind: ALL, status: ALL, commissionType: ALL };

test('filters pass an event only when every chosen group agrees', () => {
    assert.equal(passesFilters(event(), everything), true);
    assert.equal(passesFilters(event(), { ...everything, methodKind: 'Order book' }), false);
    assert.equal(passesFilters(event(), { ...everything, status: 'Active', commissionType: 'on-purchase' }), true);
    assert.equal(passesFilters(event({ status: 'Closed' }), { ...everything, status: 'Active' }), false);
});

test('the summary line says how many events there are and how many are shown', () => {
    assert.equal(eventsSummary(0, 0), 'No events on the server yet.');
    assert.equal(eventsSummary(1, 1), '1 event');
    assert.equal(eventsSummary(4, 4), '4 events');
    assert.equal(eventsSummary(2, 4), '2 of 4 events shown');
});

test('commission is written as percent and type', () => {
    assert.equal(commissionOf(event()), '3% on-purchase');
});

test('every event gets a role, whether the user runs it, trades in it, or neither', () => {
    const holding = { event: event({ number: 2 }), options: [{ number: 1, optionName: 'Yes', shares: 10, paidFor: 6.2, currentValue: 7.3 }, { number: 2, optionName: 'No', shares: 1500, paidFor: 1, currentValue: null }], commissionPaid: 0.2, netResult: 0, trades: [] };
    const roles = rolesOf(me({ marketMakerOf: ['Rain'], participations: [holding] }),
        [event(), event({ number: 2, name: 'Snow' }), event({ number: 3, name: 'Sun' })]);
    assert.deepEqual(roles.map(roleName), ['Market maker', 'Trading', 'Not taken part yet']);
    assert.deepEqual(roles.map(holdingSummary), ['—', 'Yes 10, No 1,500', '—']);
    const both = rolesOf(me({ marketMakerOf: ['Snow'], participations: [holding] }), [event({ number: 2, name: 'Snow' })]);
    assert.equal(roleName(both[0]), 'Market maker, trading');
});

test('the other users are everybody but me', () => {
    const users = [{ name: 'Avi' }, { name: 'Dana' }];
    assert.deepEqual(othersThan(users, me()), [{ name: 'Dana' }]);
    assert.deepEqual(othersThan(users, null), users);
});

test('the moment of going below zero is noticed once', () => {
    assert.equal(justBlocked(me(), me({ blocked: true })), true);
    assert.equal(justBlocked(me({ blocked: true }), me({ blocked: true })), false);
    assert.equal(justBlocked(null, me({ blocked: true })), false);
});

test('the trade panel situation ignores everything that moves twice a second', () => {
    const before = tradeSituation(event(), me());
    assert.equal(tradeSituation(event({ accountBalance: 500, description: 'changed' }), me({ balance: 3 })), before);
    assert.notEqual(tradeSituation(event({ status: 'Closed' }), me()), before);
    assert.notEqual(tradeSituation(event(), me({ blocked: true })), before);
    assert.notEqual(tradeSituation(event({ marketMakerName: 'Avi' }), me()), before);
    assert.notEqual(tradeSituation(event({ number: 2 }), me()), before);
    assert.equal(tradeSituation(null, me()), null);
    assert.equal(tradeSituation(event(), null), null);
});

test('what an action did is told in the JavaFX client words', () => {
    assert.equal(describeOpening(event()), 'You opened "Rain". It is trading now.');
    assert.equal(describeClosing(event({ winningOptionName: 'Yes' })), '"Rain" is closed on "Yes". Its winners have been paid.');
    assert.equal(describePurchase({ optionName: 'Yes', quantity: 10, sharesCost: 5.25, commission: 0.16 }),
        'You bought 10 of "Yes" for 5.25 plus 0.16 commission.');
    assert.equal(describePurchase({ optionName: 'Yes', quantity: 1000, sharesCost: 5, commission: 0 }),
        'You bought 1,000 of "Yes" for 5.00.');
    assert.equal(describeSale({ optionName: 'No', quantity: -4, sharesCost: -1.5 }), 'You sold 4 of "No" for 1.50.');
    assert.equal(describeOrder([]), 'Your order is waiting in the book.');
    assert.equal(describeOrder([{ quantity: 4 }]), 'Your order went through: 1 trade, 4 shares changing hands or newly minted.');
    assert.equal(describeOrder([{ quantity: 4 }, { quantity: 6 }]), 'Your order went through: 2 trades, 10 shares changing hands or newly minted.');
    assert.equal(describeDeposit(500, me({ balance: 600 })), 'You loaded 500.00. Your balance is 600.00.');
});

test('a number of shares must be a whole number from 1 up', () => {
    assert.deepEqual(parseQuantity(' 10 '), { value: 10 });
    for (const typed of ['', 'abc', '0', '1.5', '-3', '1e3', '10 shares']) {
        assert.ok(parseQuantity(typed).problem, `"${typed}" should be refused`);
    }
    assert.equal(parseQuantity('abc').problem, '"abc" is not a number of shares. Please type a whole number from 1 up, such as 10.');
});

test('a price and an amount must be numbers, and the server judges the rest', () => {
    assert.deepEqual(parsePrice('0.55'), { value: 0.55 });
    assert.deepEqual(parsePrice('.5'), { value: 0.5 });
    assert.equal(parsePrice('0.5.5').problem, '"0.5.5" is not a price. Please type a number, such as 0.50.');
    assert.ok(parsePrice('').problem);
    assert.deepEqual(parseAmount('500'), { value: 500 });
    assert.deepEqual(parseAmount('-5'), { value: -5 });
    assert.equal(parseAmount('lots').problem, '"lots" is not an amount. Please type a number, such as 500.');
    assert.ok(parseAmount('  ').problem);
    assert.ok(parseAmount('0x10').problem);
});

test('an event is decided only once it has a winner, however the server leaves the winner out', () => {
    const undecided = event();
    delete undecided.winningOptionName; // Gson leaves null fields out of its JSON altogether
    assert.equal(isDecided(undecided), false);
    assert.equal(isDecided(event({ winningOptionName: null })), false);
    assert.equal(isDecided(event({ status: 'Closed', winningOptionName: 'Yes' })), true);
});

test('a chat line reads time, name, text', () => {
    const sentAt = new Date(2026, 9, 11, 9, 5, 0).getTime();
    assert.equal(chatLineText({ number: 1, userName: 'Dana', text: '<b>hi</b>', sentAt }), '09:05:00  Dana:  <b>hi</b>');
});
