/**
 * How numbers are written on screen: the same strings as the JavaFX client's Format.java, so the two
 * clients never disagree about a price.
 *
 * A price the market cannot supply is shown as a dash rather than a nought. A book with nobody
 * selling has no ask and therefore no spread, and writing 0.00 there would claim something untrue.
 */

/** What is shown where the market has no answer. */
export const NOTHING = '—';

/** Anything smaller than half a penny is shown as zero, so no total ever reads "-0.00". */
const ROUNDING_NOISE = 0.005;

/**
 * An amount of money, to two decimal places.
 *
 * Java's %.2f rounds the shortest decimal form of a number half up, so 1.005 is "1.01"; JavaScript's
 * toFixed rounds the binary value, which is a hair under 1.005, and says "1.00". Shifting the decimal
 * point in the text of the number, rather than multiplying, keeps the rounding on the digits a person
 * sees, which is what Java does.
 */
export function money(amount) {
    if (Math.abs(amount) < ROUNDING_NOISE) {
        return '0.00';
    }
    const sign = amount < 0 ? -1 : 1;
    const cents = Math.round(Number(`${Math.abs(amount)}e2`));
    return Number.isFinite(cents) ? (sign * cents / 100).toFixed(2) : amount.toFixed(2);
}

/** An amount that came in or went out, with its sign always shown: +5.80 received, -5.80 paid. */
export function signedMoney(amount) {
    const shown = money(amount);
    return shown.startsWith('-') || shown === money(0) ? shown : `+${shown}`;
}

/** An amount the market may not be able to give, shown as a dash when it cannot. */
export function moneyOrNothing(amount) {
    return amount === null || amount === undefined ? NOTHING : money(amount);
}

/** A whole number of shares. */
export function shares(count) {
    return Number(count).toLocaleString('en-US');
}

/** A percentage as it is written in the events file. */
export function percent(value) {
    return `${value}%`;
}

/** The time of day a chat line was sent, on this computer's clock. */
export function time(epochMillis) {
    const at = new Date(epochMillis);
    return [at.getHours(), at.getMinutes(), at.getSeconds()].map(part => String(part).padStart(2, '0')).join(':');
}
