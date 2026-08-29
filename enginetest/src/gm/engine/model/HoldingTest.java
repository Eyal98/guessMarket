package gm.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What one user holds in one event.
 * <p>
 * Money is tracked per option rather than only as a total, because the order book screen has to show
 * what was paid for each option separately, and the profit or loss at closing time is the difference
 * between everything paid in and everything received back.
 */
class HoldingTest {

    private static final double TOLERANCE = 0.0001;

    @Test
    @DisplayName("A fresh holding owns nothing and has paid nothing")
    void aFreshHoldingIsEmpty() {
        Holding holding = new Holding(2);

        assertEquals(0, holding.shares(0));
        assertEquals(0, holding.shares(1));
        assertEquals(0.0, holding.paidFor(0), TOLERANCE);
        assertEquals(0.0, holding.commissionPaid(), TOLERANCE);
        assertTrue(holding.isEmpty());
    }

    @Test
    @DisplayName("A purchase adds shares and money to that option alone")
    void aPurchaseTouchesOnlyItsOwnOption() {
        Holding holding = new Holding(2);

        holding.recordPurchase(0, 100, 62.01, 3.10);

        assertEquals(100, holding.shares(0));
        assertEquals(0, holding.shares(1));
        assertEquals(62.01, holding.paidFor(0), TOLERANCE);
        assertEquals(0.0, holding.paidFor(1), TOLERANCE);
        assertEquals(3.10, holding.commissionPaid(), TOLERANCE);
        assertEquals(false, holding.isEmpty());
    }

    @Test
    @DisplayName("Purchases accumulate, and commission adds up across all of them")
    void purchasesAccumulate() {
        Holding holding = new Holding(2);

        holding.recordPurchase(0, 100, 62.01, 3.10);
        holding.recordPurchase(0, 50, 40.00, 2.00);
        holding.recordPurchase(1, 25, 10.00, 0.50);

        assertEquals(150, holding.shares(0));
        assertEquals(25, holding.shares(1));
        assertEquals(102.01, holding.paidFor(0), TOLERANCE);
        assertEquals(10.00, holding.paidFor(1), TOLERANCE);
        assertEquals(5.60, holding.commissionPaid(), TOLERANCE);
    }

    @Test
    @DisplayName("Selling gives shares up and takes the money back off what was paid")
    void sellingReducesSharesAndMoneyPaid() {
        Holding holding = new Holding(2);
        holding.recordPurchase(0, 100, 60.00, 0.0);

        holding.recordSale(0, 40, 28.00);

        assertEquals(60, holding.shares(0));
        assertEquals(32.00, holding.paidFor(0), TOLERANCE);
    }

    @Test
    @DisplayName("Nobody can sell shares they do not hold")
    void sellingMoreThanHeldIsRefused() {
        Holding holding = new Holding(2);
        holding.recordPurchase(0, 10, 6.00, 0.0);

        assertThrows(IllegalArgumentException.class, () -> holding.recordSale(0, 11, 7.00));
    }

    @Test
    @DisplayName("The result of an event is everything received less everything paid")
    void theResultIsReceivedLessPaid() {
        Holding holding = new Holding(2);
        holding.recordPurchase(0, 100, 60.00, 5.00);

        holding.recordPayout(100.00);

        assertEquals(35.00, holding.netResult(), TOLERANCE);
    }
}
