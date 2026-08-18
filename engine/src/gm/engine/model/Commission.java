package gm.engine.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * The commission of an event: how much, as a whole percentage, and when it is charged.
 *
 * @param percent between {@value #MINIMUM_PERCENT} and {@value #MAXIMUM_PERCENT}
 * @param type    when the commission is taken
 */
public record Commission(int percent, CommissionType type) implements Serializable {

    public static final int MINIMUM_PERCENT = 0;
    public static final int MAXIMUM_PERCENT = 90;

    public Commission {
        Objects.requireNonNull(type, "type");
        if (!isValidPercent(percent)) {
            throw new IllegalArgumentException("A commission must be a whole number between "
                    + MINIMUM_PERCENT + " and " + MAXIMUM_PERCENT + ", but it is " + percent + ".");
        }
    }

    public static boolean isValidPercent(int percent) {
        return percent >= MINIMUM_PERCENT && percent <= MAXIMUM_PERCENT;
    }

    /** What a buyer pays on top of the price of the shares. */
    public double purchaseFee(double sharesCost) {
        return type.purchaseFee(sharesCost, percent);
    }

    /** What is taken out of the winners' payout when the event closes. */
    public double closingFee(double grossPayout) {
        return type.closingFee(grossPayout, percent);
    }
}
