package gm.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The money rules of a single LMSR event, end to end. The numbers come from appendix A: an event with
 * b = 100 costs 69.31 to open, and buying 100 shares of one option costs 62.01.
 * <p>
 * Every event here is opened by its market maker first, because in this version an event arrives from
 * the file dormant and is funded by whoever runs it rather than by the system at load time.
 */
class EventTest {

    private static final double TOLERANCE = 0.0001;
    private static final double SUBSIDY = 69.3147;
    private static final double PURCHASE_COST = 62.0115;
    private static final double MARKET_MAKER_CASH = 10000.0;

    private final User marketMaker = new User("Tikva", MARKET_MAKER_CASH);
    private final User buyer = new User("Menash", 100000);

    private LmsrEvent eventWith(Commission commission) {
        LmsrEvent event = new LmsrEvent(3, "Earth Quake on Dead Sea", "Will there be an earth quake this year?",
                commission, List.of("Yes", "No"), 100);
        event.assignMarketMaker(marketMaker);
        return event;
    }

    private LmsrEvent openedEvent(Commission commission) {
        LmsrEvent event = eventWith(commission);
        event.open(marketMaker);
        return event;
    }

    @Test
    @DisplayName("Opening an event moves the subsidy from the market maker into the event account")
    void openingFundsTheEvent() {
        LmsrEvent event = openedEvent(new Commission(0, CommissionType.ON_CLOSE));

        assertEquals(SUBSIDY, event.account().balance(), TOLERANCE);
        assertEquals(MARKET_MAKER_CASH - SUBSIDY, marketMaker.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("A freshly opened event has no trades and has sold no shares")
    void anOpenedEventIsEmpty() {
        LmsrEvent event = openedEvent(new Commission(10, CommissionType.ON_CLOSE));

        assertTrue(event.isOpen());
        assertTrue(event.history().isEmpty());
        assertEquals(0, event.options().get(0).sharesBought());
        assertEquals(0.0, event.commissionCollected(), TOLERANCE);
    }

    @Test
    @DisplayName("Nothing can be traded on an event that has not been opened")
    void tradingNeedsAnOpenEvent() {
        LmsrEvent event = eventWith(new Commission(0, CommissionType.ON_CLOSE));

        assertThrows(IllegalStateException.class, () -> event.buy(buyer, 0, 10));
    }

    @Test
    @DisplayName("An on-purchase commission is added on top of the price")
    void onPurchaseCommissionIsAddedToThePrice() {
        LmsrEvent event = openedEvent(new Commission(50, CommissionType.ON_PURCHASE));

        Trade trade = event.buy(buyer, 0, 100);

        assertEquals(PURCHASE_COST, trade.sharesCost(), TOLERANCE);
        assertEquals(PURCHASE_COST * 0.5, trade.commission(), TOLERANCE);
        assertEquals(PURCHASE_COST * 1.5, trade.totalPaid(), TOLERANCE);
        assertEquals(PURCHASE_COST * 0.5, event.commissionCollected(), TOLERANCE);
        assertEquals(100, event.options().get(0).sharesBought());
        assertEquals(0, event.options().get(1).sharesBought());
    }

    @Test
    @DisplayName("An on-close commission costs the buyer nothing at purchase time")
    void onCloseCommissionIsNotChargedOnPurchase() {
        LmsrEvent event = openedEvent(new Commission(50, CommissionType.ON_CLOSE));

        Trade trade = event.buy(buyer, 0, 100);

        assertEquals(PURCHASE_COST, trade.sharesCost(), TOLERANCE);
        assertEquals(0.0, trade.commission(), TOLERANCE);
        assertEquals(0.0, event.commissionCollected(), TOLERANCE);
    }

    @Test
    @DisplayName("Trade history is kept in the order the trades happened")
    void historyIsChronological() {
        LmsrEvent event = openedEvent(new Commission(0, CommissionType.ON_CLOSE));

        event.buy(buyer, 0, 10);
        event.buy(buyer, 1, 20);
        event.buy(buyer, 0, 30);

        List<Trade> history = event.history();
        assertEquals(3, history.size());
        assertEquals(10, history.get(0).quantity());
        assertEquals(20, history.get(1).quantity());
        assertEquals(30, history.get(2).quantity());
        assertEquals("No", history.get(1).optionName());
    }

    @Test
    @DisplayName("Every winning share pays 1.00 when there is no closing commission")
    void closingPaysOnePerWinningShare() {
        LmsrEvent event = openedEvent(new Commission(0, CommissionType.ON_CLOSE));
        event.buy(buyer, 0, 100);

        event.close(marketMaker, 0);

        assertFalse(event.isOpen());
        assertEquals(100.0, event.totalPaidOut(), TOLERANCE);
        assertEquals(0.0, event.commissionCollected(), TOLERANCE);
    }

    @Test
    @DisplayName("An on-close commission is taken out of the winners' payout")
    void closingCommissionReducesThePayout() {
        LmsrEvent event = openedEvent(new Commission(50, CommissionType.ON_CLOSE));
        event.buy(buyer, 0, 100);

        event.close(marketMaker, 0);

        assertEquals(50.0, event.totalPaidOut(), TOLERANCE);
        assertEquals(50.0, event.commissionCollected(), TOLERANCE);
    }

    @Test
    @DisplayName("Whatever an LMSR event has left after paying the winners goes back to its market maker")
    void leftoverReturnsToTheMarketMaker() {
        LmsrEvent event = openedEvent(new Commission(0, CommissionType.ON_CLOSE));
        event.buy(buyer, 0, 100);

        event.close(marketMaker, 0);

        double leftover = SUBSIDY + PURCHASE_COST - 100.0;
        assertEquals(0.0, event.account().balance(), TOLERANCE);
        assertEquals(MARKET_MAKER_CASH - SUBSIDY + leftover, marketMaker.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("Closing on the option nobody bought pays nothing and returns the whole pot")
    void closingOnTheEmptyOptionPaysNothing() {
        LmsrEvent event = openedEvent(new Commission(20, CommissionType.ON_CLOSE));
        event.buy(buyer, 0, 100);

        event.close(marketMaker, 1);

        assertEquals(0.0, event.totalPaidOut(), TOLERANCE);
        assertEquals(0.0, event.commissionCollected(), TOLERANCE);
        assertEquals(MARKET_MAKER_CASH + PURCHASE_COST, marketMaker.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("The event account never runs dry, however much is bought")
    void thePotAlwaysCoversThePayout() {
        LmsrEvent event = openedEvent(new Commission(0, CommissionType.ON_CLOSE));
        event.buy(buyer, 0, 5000);
        event.buy(buyer, 1, 300);

        event.close(marketMaker, 0);

        assertEquals(0.0, event.account().balance(), TOLERANCE);
        assertEquals(5000.0, event.totalPaidOut(), TOLERANCE);
    }

    @Test
    @DisplayName("An event must have a name, a description, options and a method")
    void constructorRejectsBrokenInput() {
        Commission commission = new Commission(0, CommissionType.ON_CLOSE);

        assertThrows(NullPointerException.class,
                () -> new LmsrEvent(1, null, "d", commission, List.of("Yes", "No"), 100));
        assertThrows(NullPointerException.class,
                () -> new LmsrEvent(1, "n", "d", null, List.of("Yes", "No"), 100));
        assertThrows(IllegalArgumentException.class,
                () -> new LmsrEvent(1, "n", "d", commission, List.of("Yes"), 100));
    }
}
