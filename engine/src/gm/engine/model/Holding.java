package gm.engine.model;

import java.io.Serializable;
import java.util.Arrays;

/**
 * What one user holds in one event: shares of each option, the money paid for each, the commission
 * paid along the way, and anything received back when the event closed.
 * <p>
 * Money is kept per option rather than as a single total because the order book screen has to show
 * what was paid for each option on its own. The running total of everything paid and everything
 * received is what makes the profit or loss at closing time a subtraction rather than a
 * reconstruction from history.
 */
public final class Holding implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long[] shares;
    private final double[] paid;
    private double commissionPaid;
    private double received;

    public Holding(int optionCount) {
        if (optionCount < 1) {
            throw new IllegalArgumentException(
                    "A holding needs at least one option, but was asked for " + optionCount + ".");
        }
        this.shares = new long[optionCount];
        this.paid = new double[optionCount];
    }

    public long shares(int optionIndex) {
        return shares[optionIndex];
    }

    /** What this user has paid for one option, net of anything sold back. */
    public double paidFor(int optionIndex) {
        return paid[optionIndex];
    }

    public double commissionPaid() {
        return commissionPaid;
    }

    /** Whether this user has taken no position at all in the event. */
    public boolean isEmpty() {
        return Arrays.stream(shares).allMatch(count -> count == 0)
                && commissionPaid == 0
                && Arrays.stream(paid).allMatch(amount -> amount == 0);
    }

    public void recordPurchase(int optionIndex, long quantity, double cost, double commission) {
        shares[optionIndex] += quantity;
        paid[optionIndex] += cost;
        commissionPaid += commission;
    }

    public void recordSale(int optionIndex, long quantity, double proceeds) {
        if (quantity > shares[optionIndex]) {
            throw new IllegalArgumentException("Cannot sell " + quantity + " shares while holding only "
                    + shares[optionIndex] + ".");
        }
        shares[optionIndex] -= quantity;
        paid[optionIndex] -= proceeds;
    }

    /** What the event paid this user when it closed. */
    public void recordPayout(double amount) {
        received += amount;
    }

    /** Everything received back, less everything paid in, commission included. */
    public double netResult() {
        return received - Arrays.stream(paid).sum() - commissionPaid;
    }
}
