package gm.engine.model;

import gm.engine.method.LmsrMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The money rules of a single event, end to end. The numbers come from appendix A: an event with
 * b = 100 starts with a 69.31 subsidy, and buying 100 shares of one option costs 62.01.
 */
class EventTest {

    private static final double TOLERANCE = 0.0001;
    private static final double SUBSIDY = 69.3147;
    private static final double PURCHASE_COST = 62.0115;

    private static Event eventWith(Commission commission) {
        return new Event(3, "Earth Quake on Dead Sea", "Will there be an earth quake this year?",
                commission, List.of("Yes", "No"), new LmsrMethod(100));
    }

    private static Event fundedEvent(Commission commission, Account marketMaker) {
        Event event = eventWith(commission);
        event.fundSubsidy(marketMaker);
        return event;
    }

    @Test
    @DisplayName("Funding an event moves the subsidy from the market maker into the event account")
    void subsidyMovesFromMarketMaker() {
        Account marketMaker = new Account();
        Event event = fundedEvent(new Commission(0, CommissionType.ON_CLOSE), marketMaker);

        assertEquals(SUBSIDY, event.account().balance(), TOLERANCE);
        assertEquals(-SUBSIDY, marketMaker.balance(), TOLERANCE);
    }

    @Test
    @DisplayName("A new event is open, has no trades and has sold no shares")
    void newEventIsEmptyAndOpen() {
        Event event = fundedEvent(new Commission(10, CommissionType.ON_CLOSE), new Account());

        assertTrue(event.isOpen());
        assertTrue(event.history().isEmpty());
        assertEquals(0, event.options().get(0).sharesBought());
        assertEquals(0.0, event.commissionCollected(), TOLERANCE);
    }

    @Test
    @DisplayName("An on-purchase commission is added on top of the price and lands in the event account")
    void onPurchaseCommissionIsAddedToThePrice() {
        Account marketMaker = new Account();
        Event event = fundedEvent(new Commission(50, CommissionType.ON_PURCHASE), marketMaker);

        Trade trade = event.buy(0, 100);

        assertEquals(PURCHASE_COST, trade.sharesCost(), TOLERANCE);
        assertEquals(PURCHASE_COST * 0.5, trade.commission(), TOLERANCE);
        assertEquals(PURCHASE_COST * 1.5, trade.totalPaid(), TOLERANCE);
        assertEquals(SUBSIDY + PURCHASE_COST * 1.5, event.account().balance(), TOLERANCE);
        assertEquals(PURCHASE_COST * 0.5, event.commissionCollected(), TOLERANCE);
        assertEquals(100, event.options().get(0).sharesBought());
        assertEquals(0, event.options().get(1).sharesBought());
    }

    @Test
    @DisplayName("An on-close commission costs the buyer nothing at purchase time")
    void onCloseCommissionIsNotChargedOnPurchase() {
        Event event = fundedEvent(new Commission(50, CommissionType.ON_CLOSE), new Account());

        Trade trade = event.buy(0, 100);

        assertEquals(PURCHASE_COST, trade.sharesCost(), TOLERANCE);
        assertEquals(0.0, trade.commission(), TOLERANCE);
        assertEquals(0.0, event.commissionCollected(), TOLERANCE);
    }

    @Test
    @DisplayName("Trade history is kept in the order the trades happened")
    void historyIsChronological() {
        Event event = fundedEvent(new Commission(0, CommissionType.ON_CLOSE), new Account());

        event.buy(0, 10);
        event.buy(1, 20);
        event.buy(0, 30);

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
        Account marketMaker = new Account();
        Event event = fundedEvent(new Commission(0, CommissionType.ON_CLOSE), marketMaker);
        event.buy(0, 100);

        event.close(0, marketMaker);

        assertFalse(event.isOpen());
        assertSame(event.options().get(0), event.winningOption());
        assertEquals(100.0, event.totalPaidOut(), TOLERANCE);
        assertEquals(0.0, event.commissionCollected(), TOLERANCE);
    }

    @Test
    @DisplayName("An on-close commission is taken out of the winners' payout")
    void closingCommissionReducesThePayout() {
        Account marketMaker = new Account();
        Event event = fundedEvent(new Commission(50, CommissionType.ON_CLOSE), marketMaker);
        event.buy(0, 100);

        event.close(0, marketMaker);

        assertEquals(50.0, event.totalPaidOut(), TOLERANCE);
        assertEquals(50.0, event.commissionCollected(), TOLERANCE);
    }

    @Test
    @DisplayName("Whatever is left after paying the winners goes back to the market maker")
    void leftoverReturnsToTheMarketMaker() {
        Account marketMaker = new Account();
        Event event = fundedEvent(new Commission(0, CommissionType.ON_CLOSE), marketMaker);
        event.buy(0, 100);

        event.close(0, marketMaker);

        double expectedLeftover = SUBSIDY + PURCHASE_COST - 100.0;
        assertEquals(0.0, event.account().balance(), TOLERANCE);
        assertEquals(-SUBSIDY + expectedLeftover, marketMaker.balance(), TOLERANCE);
    }

    @Test
    @DisplayName("Closing on the option nobody bought pays nothing and returns the whole pot")
    void closingOnTheEmptyOptionPaysNothing() {
        Account marketMaker = new Account();
        Event event = fundedEvent(new Commission(20, CommissionType.ON_CLOSE), marketMaker);
        event.buy(0, 100);

        event.close(1, marketMaker);

        assertEquals(0.0, event.totalPaidOut(), TOLERANCE);
        assertEquals(0.0, event.commissionCollected(), TOLERANCE);
        assertEquals(-SUBSIDY + SUBSIDY + PURCHASE_COST, marketMaker.balance(), TOLERANCE);
    }

    @Test
    @DisplayName("The event account never runs dry, however much is bought")
    void thePotAlwaysCoversThePayout() {
        Account marketMaker = new Account();
        Event event = fundedEvent(new Commission(0, CommissionType.ON_CLOSE), marketMaker);
        event.buy(0, 5000);
        event.buy(1, 300);

        event.close(0, marketMaker);

        assertEquals(0.0, event.account().balance(), TOLERANCE);
        assertEquals(5000.0, event.totalPaidOut(), TOLERANCE);
    }

    @Test
    @DisplayName("A closed event cannot be traded on or closed again")
    void aClosedEventIsFinished() {
        Account marketMaker = new Account();
        Event event = fundedEvent(new Commission(0, CommissionType.ON_CLOSE), marketMaker);
        event.close(0, marketMaker);

        assertThrows(IllegalStateException.class, () -> event.buy(0, 10));
        assertThrows(IllegalStateException.class, () -> event.close(1, marketMaker));
    }

    @Test
    @DisplayName("An event must have a positive id, a name, a description, options and a method")
    void constructorRejectsBrokenInput() {
        Commission commission = new Commission(0, CommissionType.ON_CLOSE);

        assertThrows(NullPointerException.class,
                () -> new Event(1, null, "d", commission, List.of("Yes", "No"), new LmsrMethod(100)));
        assertThrows(NullPointerException.class,
                () -> new Event(1, "n", "d", null, List.of("Yes", "No"), new LmsrMethod(100)));
        assertThrows(IllegalArgumentException.class,
                () -> new Event(1, "n", "d", commission, List.of("Yes"), new LmsrMethod(100)));
    }
}
