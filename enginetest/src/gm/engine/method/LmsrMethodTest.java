package gm.engine.method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the LMSR implementation against the worked example in appendix A of the course document
 * (b = 100, buying 100 YES shares on an untouched event).
 */
class LmsrMethodTest {

    private static final double TOLERANCE = 0.0001;
    private static final int LIQUIDITY = 100;

    private final LmsrMethod method = new LmsrMethod(LIQUIDITY);

    @Test
    @DisplayName("The subsidy of an untouched binary event is b * ln(2) = 69.31")
    void initialPotMatchesAppendixA() {
        assertEquals(69.3147, method.initialPot(2), TOLERANCE);
    }

    @Test
    @DisplayName("Both options of an untouched event are worth 0.50")
    void untouchedEventIsBalanced() {
        long[] shares = {0, 0};

        assertEquals(0.5, method.optionValue(shares, 0), TOLERANCE);
        assertEquals(0.5, method.optionValue(shares, 1), TOLERANCE);
    }

    @Test
    @DisplayName("Buying 100 shares of the first option costs 62.01")
    void buyCostMatchesAppendixA() {
        assertEquals(62.0115, method.buyCost(new long[] {0, 0}, 0, 100), TOLERANCE);
    }

    @Test
    @DisplayName("After buying 100 shares the options are worth 0.73 and 0.27")
    void valuesAfterPurchaseMatchAppendixA() {
        long[] shares = {100, 0};

        assertEquals(0.7311, method.optionValue(shares, 0), TOLERANCE);
        assertEquals(0.2689, method.optionValue(shares, 1), TOLERANCE);
    }

    @Test
    @DisplayName("The values of all options always sum to 1")
    void optionValuesSumToOne() {
        long[] shares = {734, 219};

        assertEquals(1.0, method.optionValue(shares, 0) + method.optionValue(shares, 1), TOLERANCE);
    }

    @Test
    @DisplayName("Buying the same amount twice costs more the second time")
    void pricesRiseAsSharesAreBought() {
        double firstPurchase = method.buyCost(new long[] {0, 0}, 0, 100);
        double secondPurchase = method.buyCost(new long[] {100, 0}, 0, 100);

        assertTrue(secondPurchase > firstPurchase,
                "expected the second purchase (" + secondPurchase + ") to cost more than the first ("
                        + firstPurchase + ")");
    }

    @Test
    @DisplayName("The two options behave identically from a symmetric position")
    void optionsAreSymmetric() {
        assertEquals(method.buyCost(new long[] {0, 0}, 0, 250),
                method.buyCost(new long[] {0, 0}, 1, 250), TOLERANCE);
    }

    @Test
    @DisplayName("A purchase far larger than b does not overflow to infinity")
    void largePurchaseStaysFinite() {
        double cost = method.buyCost(new long[] {0, 0}, 0, 1_000_000);

        assertTrue(Double.isFinite(cost), "cost overflowed: " + cost);
        assertTrue(cost > 999_000 && cost < 1_000_100,
                "a purchase this large should cost roughly its share count, but cost " + cost);
    }

    @Test
    @DisplayName("Option values stay within 0..1 for extreme share counts")
    void extremeSharesKeepValuesInRange() {
        long[] shares = {1_000_000, 0};

        double leading = method.optionValue(shares, 0);
        double trailing = method.optionValue(shares, 1);

        assertTrue(Double.isFinite(leading) && Double.isFinite(trailing),
                "values overflowed: " + leading + ", " + trailing);
        assertEquals(1.0, leading, TOLERANCE);
        assertEquals(0.0, trailing, TOLERANCE);
    }

    @Test
    @DisplayName("A liquidity index of zero or less is rejected")
    void liquidityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new LmsrMethod(0));
        assertThrows(IllegalArgumentException.class, () -> new LmsrMethod(-5));
    }

    @Test
    @DisplayName("Buying a non positive amount of shares is rejected")
    void quantityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> method.buyCost(new long[] {0, 0}, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> method.buyCost(new long[] {0, 0}, 0, -3));
    }

    @Test
    @DisplayName("The method describes itself for display purposes")
    void describesItself() {
        assertEquals("LMSR (b=100)", method.describe());
    }
}
