package gm.ui.fx;

import java.util.Locale;

/**
 * How numbers are written on screen.
 * <p>
 * Everything is formatted with {@link Locale#US} on purpose: left to the machine's own locale, nought
 * point seven three would come out as "0,73" on a great many computers, and money and probabilities
 * in this program are always written with a full stop.
 * <p>
 * A price the market cannot supply is shown as a dash rather than a nought. A book with nobody
 * selling has no ask and therefore no spread, and writing 0.00 there would claim something untrue.
 */
public final class Format {

    /** What is shown where the market has no answer. */
    public static final String NOTHING = "—";

    /** Anything smaller than half a penny is shown as zero, so no total ever reads "-0.00". */
    private static final double ROUNDING_NOISE = 0.005;

    private Format() {
    }

    /** An amount of money, to two decimal places. */
    public static String money(double amount) {
        double shown = Math.abs(amount) < ROUNDING_NOISE ? 0.0 : amount;
        return String.format(Locale.US, "%.2f", shown);
    }

    /** A price the market may not be able to give, shown as a dash when it cannot. */
    public static String price(Double amount) {
        return amount == null ? NOTHING : money(amount);
    }

    /** A whole number of shares. */
    public static String shares(long count) {
        return String.format(Locale.US, "%,d", count);
    }

    /** A percentage as it is written in the events file. */
    public static String percent(int value) {
        return value + "%";
    }
}
