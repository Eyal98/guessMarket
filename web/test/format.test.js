import { test } from 'node:test';
import assert from 'node:assert/strict';
import { NOTHING, money, signedMoney, moneyOrNothing, shares, percent, time } from '../public/js/format.js';

// Every expected string below was printed by String.format(Locale.US, "%.2f", x) in Java 25, after
// Format.java's own rule that anything smaller than half a penny is shown as zero.
test('money is written exactly as the JavaFX client writes it', () => {
    const javaSays = [
        [0, '0.00'], [5.8, '5.80'], [-5.8, '-5.80'], [1.005, '1.01'], [0.125, '0.13'], [2.675, '2.68'],
        [-1.005, '-1.01'], [1234567.891, '1234567.89'], [0.004, '0.00'], [-0.004, '0.00'], [0.005, '0.01'],
        [62.006, '62.01'], [0.615, '0.62'], [10.555, '10.56'], [1.0049999, '1.00'],
    ];
    for (const [amount, expected] of javaSays) {
        assert.equal(money(amount), expected, `money(${amount})`);
    }
});

test('a signed amount always shows its sign, except nought', () => {
    assert.equal(signedMoney(5.8), '+5.80');
    assert.equal(signedMoney(-5.8), '-5.80');
    assert.equal(signedMoney(0), '0.00');
    assert.equal(signedMoney(-0.001), '0.00');
});

test('a price the market cannot give is a dash', () => {
    assert.equal(moneyOrNothing(null), NOTHING);
    assert.equal(moneyOrNothing(undefined), NOTHING);
    assert.equal(moneyOrNothing(0.5), '0.50');
    assert.equal(NOTHING, '—');
});

test('shares carry thousands separators, as %,d does', () => {
    assert.equal(shares(0), '0');
    assert.equal(shares(1234567), '1,234,567');
    assert.equal(shares(-1500), '-1,500');
});

test('percent and time', () => {
    assert.equal(percent(3), '3%');
    assert.equal(time(new Date(2026, 9, 11, 14, 3, 9).getTime()), '14:03:09');
    assert.equal(time(new Date(2026, 9, 11, 0, 0, 0).getTime()), '00:00:00');
});
