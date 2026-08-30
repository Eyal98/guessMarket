package gm.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who may start and finish an event, and when.
 * <p>
 * Exercise 2 puts a market maker in charge of every event. An event arrives from the file dormant,
 * its market maker alone may open it — paying for the privilege out of their own pocket — and its
 * market maker alone may decide it. Nobody else can do either, and neither can happen twice.
 */
class EventLifecycleTest {

    private static final double TOLERANCE = 0.0001;
    private static final double SUBSIDY_AT_B_100 = 69.3147;

    private final User marketMaker = new User("Tikva", 10000);
    private final User bystander = new User("Menash", 100);

    private LmsrEvent event() {
        return new LmsrEvent(1, "Mujtaba is Dead", "Is he?",
                new Commission(5, CommissionType.ON_PURCHASE),
                List.of("Hell Yea !", "No way !"), 100);
    }

    private LmsrEvent eventOwnedByTheMarketMaker() {
        LmsrEvent event = event();
        event.assignMarketMaker(marketMaker);
        return event;
    }

    @Test
    @DisplayName("A market maker who has spent past zero can no longer open their event")
    void aBlockedMarketMakerCannotOpen() {
        // An order book stocked with nothing costs nothing to open, so the affordability check
        // cannot be what refuses this. Only the blocking rule can.
        OrderBookEvent free = new OrderBookEvent(2, "Free to open", "Costs nothing",
                new Commission(0, CommissionType.ON_PURCHASE), List.of("Yes", "No"), 0, 1, false);
        free.assignMarketMaker(bystander);
        bystander.pay(500);

        assertTrue(bystander.isBlocked(), "the setup has to leave them blocked for this to mean anything");
        assertEquals(0.0, free.openingCost(), TOLERANCE, "and opening it must genuinely be free");
        assertThrows(IllegalStateException.class, () -> free.open(bystander),
                "blocking means taking no further part, and opening an event is taking part");
    }

    @Test
    @DisplayName("A market maker who has spent past zero can no longer close their event")
    void aBlockedMarketMakerCannotClose() {
        LmsrEvent event = eventOwnedByTheMarketMaker();
        event.open(marketMaker);
        marketMaker.pay(100_000);

        assertTrue(marketMaker.isBlocked());
        assertThrows(IllegalStateException.class, () -> event.close(marketMaker, 0),
                "deciding an event is the biggest act of all, so it cannot be the one blocking allows");
    }

    @Test
    @DisplayName("An event arrives from the file dormant, with nothing in its account")
    void anEventArrivesDormant() {
        LmsrEvent event = eventOwnedByTheMarketMaker();

        assertEquals(EventStatus.NOT_STARTED, event.status());
        assertEquals(0.0, event.account().balance(), TOLERANCE);
        assertEquals(10000.0, marketMaker.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("An event knows which user runs it, and is told only once")
    void theMarketMakerIsAssignedOnce() {
        LmsrEvent event = eventOwnedByTheMarketMaker();

        assertSame(marketMaker, event.marketMaker());
        assertThrows(IllegalStateException.class, () -> event.assignMarketMaker(bystander));
    }

    @Test
    @DisplayName("Opening it moves the subsidy out of the market maker's own pocket")
    void openingChargesTheMarketMaker() {
        LmsrEvent event = eventOwnedByTheMarketMaker();

        event.open(marketMaker);

        assertEquals(EventStatus.ACTIVE, event.status());
        assertEquals(SUBSIDY_AT_B_100, event.account().balance(), TOLERANCE);
        assertEquals(10000.0 - SUBSIDY_AT_B_100, marketMaker.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("Nobody but the market maker may open an event")
    void onlyTheMarketMakerMayOpen() {
        LmsrEvent event = eventOwnedByTheMarketMaker();

        assertThrows(IllegalStateException.class, () -> event.open(bystander));
        assertEquals(EventStatus.NOT_STARTED, event.status());
        assertEquals(100.0, bystander.account().balance(), TOLERANCE, "the bystander pays nothing");
    }

    @Test
    @DisplayName("A market maker who cannot afford the subsidy cannot open the event")
    void openingNeedsTheMoneyUpFront() {
        LmsrEvent event = event();
        User pauper = new User("Avrum", 10);
        event.assignMarketMaker(pauper);

        assertThrows(IllegalStateException.class, () -> event.open(pauper));

        assertEquals(EventStatus.NOT_STARTED, event.status());
        assertEquals(10.0, pauper.account().balance(), TOLERANCE);
        assertTrue(!pauper.isBlocked(), "a refused action must not block anyone");
    }

    @Test
    @DisplayName("An event cannot be opened twice")
    void openingHappensOnce() {
        LmsrEvent event = eventOwnedByTheMarketMaker();
        event.open(marketMaker);

        assertThrows(IllegalStateException.class, () -> event.open(marketMaker));
    }

    @Test
    @DisplayName("An event that never opened cannot be closed")
    void closingNeedsAnOpenEvent() {
        LmsrEvent event = eventOwnedByTheMarketMaker();

        assertThrows(IllegalStateException.class, () -> event.close(marketMaker, 0));
    }

    @Test
    @DisplayName("Nobody but the market maker may close an event")
    void onlyTheMarketMakerMayClose() {
        LmsrEvent event = eventOwnedByTheMarketMaker();
        event.open(marketMaker);

        assertThrows(IllegalStateException.class, () -> event.close(bystander, 0));
        assertEquals(EventStatus.ACTIVE, event.status());
    }

    @Test
    @DisplayName("Closing decides the event for good")
    void closingIsFinal() {
        LmsrEvent event = eventOwnedByTheMarketMaker();
        event.open(marketMaker);

        event.close(marketMaker, 0);

        assertEquals(EventStatus.CLOSED, event.status());
        assertSame(event.options().get(0), event.winningOption());
        assertThrows(IllegalStateException.class, () -> event.close(marketMaker, 1));
        assertThrows(IllegalStateException.class, () -> event.open(marketMaker));
    }
}
