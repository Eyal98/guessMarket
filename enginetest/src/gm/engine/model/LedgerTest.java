package gm.engine.model;

import gm.engine.model.orderbook.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static gm.engine.TestUsers.funded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each person's ledger says after the market has moved their money.
 * <p>
 * The exercise asks for every movement to appear on its own line, including the ones somebody else
 * caused, and gives receiving a commission as its example. So a purchase is not one line but several:
 * what the shares cost, what the commission cost, and on the market maker's side what the commission
 * earned.
 */
class LedgerTest {

    private static final double TOLERANCE = 0.0001;

    private final User tikva = funded("Tikva", 1000);
    private final User menash = funded("Menash", 1000);
    private final User avrum = funded("Avrum", 1000);

    private LmsrEvent rain(int percent, CommissionType timing) {
        LmsrEvent event = new LmsrEvent("Rain", "Will it rain?", new Commission(percent, timing),
                List.of("Yes", "No"), 100);
        event.assignMarketMaker(tikva);
        event.open(tikva);
        return event;
    }

    private static User.LedgerLine last(User user) {
        return user.ledger().get(user.ledger().size() - 1);
    }

    private static User.LedgerLine fromEnd(User user, int back) {
        return user.ledger().get(user.ledger().size() - 1 - back);
    }

    @Test
    @DisplayName("Opening an event is a line of its own in the market maker's ledger")
    void openingIsALine() {
        rain(0, CommissionType.ON_PURCHASE);

        User.LedgerLine opening = last(tikva);
        assertEquals("Opened \"Rain\" (LMSR subsidy)", opening.description());
        assertEquals(-69.3147, opening.amount(), TOLERANCE);
        assertEquals(1000 - 69.3147, opening.balanceAfter(), TOLERANCE);
    }

    @Test
    @DisplayName("A purchase with commission is two lines for the buyer and one for the market maker")
    void aPurchaseAndItsCommission() {
        LmsrEvent event = rain(10, CommissionType.ON_PURCHASE);

        event.buy(menash, 0, 100);

        assertEquals("Bought 100 of \"Yes\" in \"Rain\"", fromEnd(menash, 1).description());
        assertEquals(-62.0115, fromEnd(menash, 1).amount(), TOLERANCE);
        assertEquals("Commission to Tikva for buying 100 of \"Yes\" in \"Rain\"", last(menash).description());
        assertEquals(-6.2012, last(menash).amount(), TOLERANCE);
        assertEquals("Commission from Menash for buying 100 of \"Yes\" in \"Rain\"", last(tikva).description());
        assertEquals(6.2012, last(tikva).amount(), TOLERANCE);
    }

    @Test
    @DisplayName("No commission means no commission line, rather than a line for nothing")
    void noCommissionNoLine() {
        LmsrEvent event = rain(0, CommissionType.ON_PURCHASE);
        int tikvaLines = tikva.ledger().size();

        event.buy(menash, 0, 10);

        assertEquals(2, menash.ledger().size(), "the deposit and the purchase");
        assertEquals(tikvaLines, tikva.ledger().size(), "the market maker earned nothing, so nothing is written");
    }

    @Test
    @DisplayName("Closing writes the win, the closing commission, and what was left for the market maker")
    void closingLines() {
        LmsrEvent event = rain(10, CommissionType.ON_CLOSE);
        event.buy(menash, 0, 100);

        event.close(tikva, 0);

        assertEquals("Won 100 of \"Yes\" in \"Rain\"", fromEnd(menash, 1).description());
        assertEquals(100.0, fromEnd(menash, 1).amount(), TOLERANCE);
        assertEquals("Commission to Tikva for closing \"Rain\"", last(menash).description());
        assertEquals(-10.0, last(menash).amount(), TOLERANCE);

        assertEquals("Commission from Menash for closing \"Rain\"", fromEnd(tikva, 1).description());
        assertEquals("What was left in \"Rain\" when it closed", last(tikva).description());
        assertEquals(69.3147 + 62.0115 - 100, last(tikva).amount(), TOLERANCE);
    }

    @Test
    @DisplayName("A market maker trading in their own event pays no commission to themselves")
    void noCommissionToOneself() {
        LmsrEvent event = rain(10, CommissionType.ON_PURCHASE);
        int before = tikva.ledger().size();

        event.buy(tikva, 0, 100);

        assertEquals(before + 1, tikva.ledger().size(), "the purchase, and no commission paid to herself");
        assertEquals(0.0, event.holdingOf(tikva).commissionPaid(), TOLERANCE);
        assertEquals(0.0, event.commissionCollected(), TOLERANCE, "nothing was collected from anybody");
    }

    @Test
    @DisplayName("A market maker who wins in their own event is paid in full, with no closing commission")
    void noClosingCommissionToOneself() {
        OrderBookEvent cup = new OrderBookEvent("Cup", "Who wins?",
                new Commission(15, CommissionType.ON_CLOSE), List.of("Argentina", "Spain"), 100, 1, true);
        cup.assignMarketMaker(tikva);
        cup.open(tikva);
        cup.submitOrder(tikva, 0, OrderSide.SELL, 20, 0.60);
        cup.submitOrder(menash, 0, OrderSide.BUY, 20, 0.60);

        cup.close(tikva, 0);

        assertEquals("Won 80 of \"Argentina\" in \"Cup\"", fromEnd(tikva, 1).description());
        assertEquals("Commission from Menash for closing \"Cup\"", last(tikva).description(),
                "Menash pays his 15%, Tikva pays nothing on her own 80");
        assertEquals(80 - (100 - 12.0), cup.holdingOf(tikva).netResult(), TOLERANCE,
                "she paid 100 for her stock, sold 20 for 12 and was paid 80 for the rest");
    }

    @Test
    @DisplayName("Selling back to an LMSR event is a line for the seller")
    void sellingIsALine() {
        LmsrEvent event = rain(0, CommissionType.ON_PURCHASE);
        event.buy(menash, 0, 100);

        event.sell(menash, 0, 100);

        assertEquals("Sold 100 of \"Yes\" in \"Rain\" back to the event", last(menash).description());
        assertEquals(62.0115, last(menash).amount(), TOLERANCE);
    }

    @Test
    @DisplayName("A resale in an order book names the other person and the price on both sides")
    void aResaleNamesBothSides() {
        OrderBookEvent cup = new OrderBookEvent("Cup", "Who wins?",
                new Commission(0, CommissionType.ON_PURCHASE), List.of("Argentina", "Spain"), 100, 1, true);
        cup.assignMarketMaker(tikva);
        cup.open(tikva);

        cup.submitOrder(tikva, 0, OrderSide.SELL, 10, 0.58);
        cup.submitOrder(menash, 0, OrderSide.BUY, 10, 0.58);

        assertEquals("Bought 10 of \"Argentina\" in \"Cup\" from Tikva at 0.58", last(menash).description());
        assertEquals(-5.8, last(menash).amount(), TOLERANCE);
        assertEquals("Sold 10 of \"Argentina\" in \"Cup\" to Menash at 0.58", last(tikva).description());
        assertEquals("Opened \"Cup\" (opening stock: 100 of each option)", fromEnd(tikva, 1).description());
    }

    @Test
    @DisplayName("A mint says the shares were new, and at what each buyer actually paid")
    void aMintSaysTheSharesAreNew() {
        OrderBookEvent cup = new OrderBookEvent("Cup", "Who wins?",
                new Commission(0, CommissionType.ON_PURCHASE), List.of("Argentina", "Spain"), 0, 1, true);
        cup.assignMarketMaker(tikva);
        cup.open(tikva);

        cup.submitOrder(menash, 0, OrderSide.BUY, 10, 0.60);
        cup.submitOrder(avrum, 1, OrderSide.BUY, 10, 0.45);

        assertEquals("Bought 10 new shares of \"Argentina\" in \"Cup\" at 0.60", last(menash).description());
        assertEquals("Bought 10 new shares of \"Spain\" in \"Cup\" at 0.40", last(avrum).description(),
                "the order that arrived second pays only what completes the base value");
        assertTrue(last(avrum).amount() < 0);
    }
}
