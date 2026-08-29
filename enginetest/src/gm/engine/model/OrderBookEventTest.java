package gm.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opening an order book event, where the market maker does not subsidise a formula but buys the
 * stock the market will trade.
 * <p>
 * Appendix B is concrete about it: paying the initial amount buys one share of every option for each
 * base value spent, so with d = 1 and an initial of 100 the market maker pays 100 and receives 100
 * "Yes" and 100 "No". Those shares are genuinely theirs, and they may offer them to the market.
 */
class OrderBookEventTest {

    private static final double TOLERANCE = 0.0001;

    private final User marketMaker = new User("Avrum", 1000);

    private OrderBookEvent event(int initial, int baseValue, boolean allowMint) {
        OrderBookEvent event = new OrderBookEvent(2, "World Cap Winner", "Who wins?",
                new Commission(15, CommissionType.ON_CLOSE), List.of("Argentina", "Spain"),
                initial, baseValue, allowMint);
        event.assignMarketMaker(marketMaker);
        return event;
    }

    @Test
    @DisplayName("Opening buys the market maker one share of each option per base value spent")
    void openingBuysTheInitialStock() {
        OrderBookEvent event = event(100, 1, true);

        event.open(marketMaker);

        assertEquals(EventStatus.ACTIVE, event.status());
        assertEquals(900.0, marketMaker.account().balance(), TOLERANCE);
        assertEquals(100.0, event.account().balance(), TOLERANCE);
        assertEquals(100, event.options().get(0).sharesBought());
        assertEquals(100, event.options().get(1).sharesBought());
    }

    @Test
    @DisplayName("The initial stock belongs to the market maker, who is therefore a participant")
    void theInitialStockIsTheMarketMakersOwn() {
        OrderBookEvent event = event(100, 1, true);

        event.open(marketMaker);

        Holding holding = event.holdingOf(marketMaker);
        assertEquals(100, holding.shares(0));
        assertEquals(100, holding.shares(1));
        assertEquals(List.of(marketMaker), event.participants());
    }

    @Test
    @DisplayName("A base value above one buys proportionally fewer pairs")
    void theBaseValueDecidesHowManyPairs() {
        OrderBookEvent event = event(100, 5, true);

        event.open(marketMaker);

        assertEquals(20, event.options().get(0).sharesBought());
        assertEquals(20, event.options().get(1).sharesBought());
        assertEquals(100.0, event.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("A market maker who cannot afford the initial stock cannot open the event")
    void openingNeedsTheMoney() {
        OrderBookEvent event = event(5000, 1, true);

        assertThrows(IllegalStateException.class, () -> event.open(marketMaker));

        assertEquals(EventStatus.NOT_STARTED, event.status());
        assertEquals(1000.0, marketMaker.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("An event may legitimately start with no stock at all")
    void anInitialOfZeroIsAllowed() {
        OrderBookEvent event = event(0, 1, false);

        event.open(marketMaker);

        assertEquals(EventStatus.ACTIVE, event.status());
        assertEquals(0, event.options().get(0).sharesBought());
        assertEquals(1000.0, marketMaker.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("Every winning share is worth the base value when the event closes")
    void theBaseValueIsWhatAWinningShareIsWorth() {
        OrderBookEvent event = event(100, 5, true);

        assertEquals(5.0, event.payoutPerWinningShare(), TOLERANCE);
    }

    @Test
    @DisplayName("The event remembers how it was configured")
    void theEventKnowsItsOwnTerms() {
        OrderBookEvent event = event(100, 1, true);

        assertEquals(1, event.baseValue());
        assertEquals(100, event.initialInvestment());
        assertTrue(event.allowsMint());
        assertEquals("Order book (d=1, initial=100, mint allowed)", event.methodDescription());
    }

    @Test
    @DisplayName("A base value of zero or less makes no sense")
    void theBaseValueMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> event(100, 0, true));
    }
}
