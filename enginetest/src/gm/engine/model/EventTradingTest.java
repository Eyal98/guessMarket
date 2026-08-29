package gm.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the money goes when people trade, which is what exercise 2 changes most.
 * <p>
 * Commission no longer stays with the event. It is the market maker's income, paid into their own
 * account the moment it is charged, and the supplied order book simulation says so outright: buyers
 * "pay 1% of the trade value to Zoe on top of the price", Zoe being the market maker. What the shares
 * themselves cost still goes to the event, because that is what the event pays out from.
 */
class EventTradingTest {

    private static final double TOLERANCE = 0.0001;
    private static final double SUBSIDY = 69.3147;
    private static final double COST_OF_100 = 62.0115;

    private final User marketMaker = new User("Tikva", 10000);
    private final User buyer = new User("Menash", 1000);

    private LmsrEvent openedEvent(int percent, CommissionType type) {
        LmsrEvent event = new LmsrEvent(1, "Mujtaba is Dead", "Is he?", new Commission(percent, type),
                List.of("Hell Yea !", "No way !"), 100);
        event.assignMarketMaker(marketMaker);
        event.open(marketMaker);
        return event;
    }

    @Test
    @DisplayName("The buyer pays, the event keeps the price and the market maker keeps the commission")
    void commissionIsTheMarketMakersIncome() {
        LmsrEvent event = openedEvent(50, CommissionType.ON_PURCHASE);
        double marketMakerAfterOpening = marketMaker.account().balance();
        double fee = COST_OF_100 * 0.5;

        event.buy(buyer, 0, 100);

        assertEquals(1000.0 - COST_OF_100 - fee, buyer.account().balance(), TOLERANCE);
        assertEquals(SUBSIDY + COST_OF_100, event.account().balance(), TOLERANCE);
        assertEquals(marketMakerAfterOpening + fee, marketMaker.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("A purchase is remembered against the buyer, not just the event")
    void thePurchaseLandsInTheBuyersHolding() {
        LmsrEvent event = openedEvent(50, CommissionType.ON_PURCHASE);

        event.buy(buyer, 0, 100);

        Holding holding = event.holdingOf(buyer);
        assertEquals(100, holding.shares(0));
        assertEquals(COST_OF_100, holding.paidFor(0), TOLERANCE);
        assertEquals(COST_OF_100 * 0.5, holding.commissionPaid(), TOLERANCE);
        assertEquals(List.of(buyer), event.participants());
    }

    @Test
    @DisplayName("Somebody who has only looked at an event is not a participant in it")
    void watchingIsNotParticipating() {
        LmsrEvent event = openedEvent(0, CommissionType.ON_CLOSE);

        assertEquals(List.of(), event.participants());
    }

    @Test
    @DisplayName("Selling gives the money back from the event and takes the shares away")
    void sellingReturnsMoneyFromTheEvent() {
        LmsrEvent event = openedEvent(0, CommissionType.ON_CLOSE);
        event.buy(buyer, 0, 100);

        event.sell(buyer, 0, 100);

        assertEquals(1000.0, buyer.account().balance(), TOLERANCE);
        assertEquals(SUBSIDY, event.account().balance(), TOLERANCE);
        assertEquals(0, event.holdingOf(buyer).shares(0));
        assertEquals(0, event.options().get(0).sharesBought());
    }

    @Test
    @DisplayName("Nobody can sell shares they never bought")
    void sellingNeedsTheShares() {
        LmsrEvent event = openedEvent(0, CommissionType.ON_CLOSE);
        event.buy(buyer, 0, 10);

        assertThrows(IllegalArgumentException.class, () -> event.sell(buyer, 0, 11));
    }

    @Test
    @DisplayName("A blocked user cannot start anything new")
    void aBlockedUserIsTurnedAway() {
        LmsrEvent event = openedEvent(0, CommissionType.ON_CLOSE);
        User pauper = new User("Avrum", 5);
        event.buy(pauper, 0, 100);

        assertTrue(pauper.isBlocked(), "the purchase should have taken this user past zero");
        assertThrows(IllegalStateException.class, () -> event.buy(pauper, 0, 1));
    }

    @Test
    @DisplayName("Closing pays each holder for their own shares")
    void closingPaysEachHolderTheirOwnShare() {
        LmsrEvent event = openedEvent(10, CommissionType.ON_CLOSE);
        User other = new User("Avrum", 1000);
        event.buy(buyer, 0, 100);
        event.buy(other, 0, 50);
        double buyerBeforeClosing = buyer.account().balance();
        double otherBeforeClosing = other.account().balance();

        event.close(marketMaker, 0);

        // 150 winning shares at 1.00 each, less a tenth taken as commission.
        assertEquals(buyerBeforeClosing + 90.0, buyer.account().balance(), TOLERANCE);
        assertEquals(otherBeforeClosing + 45.0, other.account().balance(), TOLERANCE);
        assertEquals(150.0 - 15.0, event.totalPaidOut(), TOLERANCE);
    }

    @Test
    @DisplayName("The closing commission is the market maker's, not the event's")
    void theClosingCommissionGoesToTheMarketMaker() {
        LmsrEvent event = openedEvent(10, CommissionType.ON_CLOSE);
        event.buy(buyer, 0, 100);
        double marketMakerBeforeClosing = marketMaker.account().balance();
        double eventHoldsBeforeClosing = event.account().balance();

        event.close(marketMaker, 0);

        // A tenth of the 100 owed to the holder, plus whatever the event had left over.
        double leftover = eventHoldsBeforeClosing - 100.0;
        assertEquals(marketMakerBeforeClosing + 10.0 + leftover,
                marketMaker.account().balance(), TOLERANCE);
        assertEquals(10.0, event.commissionCollected(), TOLERANCE);
        assertEquals(0.0, event.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("Holders of the losing option are paid nothing")
    void losersGetNothing() {
        LmsrEvent event = openedEvent(0, CommissionType.ON_CLOSE);
        User loser = new User("Avrum", 1000);
        event.buy(buyer, 0, 100);
        event.buy(loser, 1, 40);
        double loserBeforeClosing = loser.account().balance();

        event.close(marketMaker, 0);

        assertEquals(loserBeforeClosing, loser.account().balance(), TOLERANCE);
    }
}
