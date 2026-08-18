package gm.engine.method;

/**
 * Logarithmic Market Scoring Rule, the automated market maker described in appendix A.
 * <p>
 * Two formulas drive everything. The cost function holds the total value of the pot:
 * <pre>    C(q) = b * ln( sum of e^(qi/b) )</pre>
 * and the value of a single option is that option's share of the same sum:
 * <pre>    value(i) = e^(qi/b) / sum of e^(qj/b)</pre>
 * A purchase costs the difference between the pot after it and the pot before it, so a buyer pays a
 * price somewhere between the option's value before the purchase and its value afterwards.
 * <p>
 * Both formulas are evaluated with a shift by the largest exponent. Without it, a purchase that is
 * large relative to {@code b} sends {@code Math.exp} to infinity and every result becomes NaN, so a
 * user buying a million shares would break the event instead of merely paying a lot for it.
 */
public final class LmsrMethod implements TradingMethod {

    private static final long serialVersionUID = 1L;

    /** The liquidity index, called b in the course material. Higher means steadier prices. */
    private final int liquidity;

    public LmsrMethod(int liquidity) {
        if (liquidity <= 0) {
            throw new IllegalArgumentException(
                    "The liquidity index (b) must be a positive whole number, but it is " + liquidity + ".");
        }
        this.liquidity = liquidity;
    }

    public int liquidity() {
        return liquidity;
    }

    @Override
    public double initialPot(int optionCount) {
        return cost(new long[optionCount]);
    }

    @Override
    public double optionValue(long[] shares, int optionIndex) {
        double[] weights = shiftedWeights(shares);
        return weights[optionIndex] / sum(weights);
    }

    @Override
    public double buyCost(long[] shares, int optionIndex, long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "The number of shares to buy must be positive, but it is " + quantity + ".");
        }
        long[] sharesAfterPurchase = shares.clone();
        sharesAfterPurchase[optionIndex] += quantity;
        return cost(sharesAfterPurchase) - cost(shares);
    }

    @Override
    public String describe() {
        return "LMSR (b=" + liquidity + ")";
    }

    /** The cost function C(q), written so that the exponents can never overflow. */
    private double cost(long[] shares) {
        return liquidity * (largestExponent(shares) + Math.log(sum(shiftedWeights(shares))));
    }

    /**
     * e^(qi/b - m) for every option, where m is the largest qi/b. Subtracting m leaves every weight
     * in the range (0, 1] while keeping all the ratios between them intact, which is exactly what
     * both formulas need.
     */
    private double[] shiftedWeights(long[] shares) {
        double shift = largestExponent(shares);
        double[] weights = new double[shares.length];
        for (int i = 0; i < shares.length; i++) {
            weights[i] = Math.exp(exponentOf(shares[i]) - shift);
        }
        return weights;
    }

    private double largestExponent(long[] shares) {
        double largest = Double.NEGATIVE_INFINITY;
        for (long shareCount : shares) {
            largest = Math.max(largest, exponentOf(shareCount));
        }
        return largest;
    }

    private double exponentOf(long shareCount) {
        return shareCount / (double) liquidity;
    }

    private static double sum(double[] values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total;
    }
}
