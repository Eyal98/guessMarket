package gm.engine.model;

import gm.engine.model.orderbook.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the market remembers of its own past, which is what a chart is drawn from.
 * <p>
 * A chart cannot be built from the present alone. The event has to keep where its prices have been
 * and the user has to keep where their money has been, and both have to start recording before
 * anything happens — otherwise the first line drawn begins after the interesting part.
 */
@DisplayName("Remembering how the market got here")
class HistoryTest {

    private static final double TOLERANCE = 0.0001;

    private final User marketMaker = new User("Avrum", 10_000);
    private final User trader = new User("Tikva", 10_000);

    private LmsrEvent lmsr() {
        LmsrEvent event = new LmsrEvent(1, "Mujtaba is Dead", "Is he?",
                new Commission(5, CommissionType.ON_PURCHASE), List.of("Hell Yea !", "No Way"), 100);
        event.assignMarketMaker(marketMaker);
        return event;
    }

    private OrderBookEvent orderBook() {
        OrderBookEvent event = new OrderBookEvent(2, "World Cap Winner", "Who wins?",
                new Commission(15, CommissionType.ON_CLOSE), List.of("Argentina", "Spain"),
                100, 1, true);
        event.assignMarketMaker(marketMaker);
        return event;
    }

    @Test
    @DisplayName("A user's opening balance is the first point on their chart")
    void theOpeningBalanceIsTheFirstPoint() {
        User bob = new User("Bob", 500);

        assertEquals(List.of(new User.BalanceSample(0, 500.0)), bob.balanceHistory(),
                "a chart drawn from nothing but later trades would hide where the user started");
    }

    @Test
    @DisplayName("Every movement of money adds a point")
    void everyMovementOfMoneyAddsAPoint() {
        User bob = new User("Bob", 500);

        bob.pay(120);
        bob.receive(20);

        assertEquals(3, bob.balanceHistory().size());
        assertEquals(380.0, bob.balanceHistory().get(1).balance(), TOLERANCE);
        assertEquals(400.0, bob.balanceHistory().get(2).balance(), TOLERANCE);
    }

    @Test
    @DisplayName("Opening an event records where its prices started")
    void openingRecordsTheStartingPrices() {
        LmsrEvent event = lmsr();

        event.open(marketMaker);

        assertEquals(1, event.priceHistory().size());
        Event.PriceSample start = event.priceHistory().get(0);
        assertEquals(0.5, start.pricePerOption().get(0), TOLERANCE);
        assertEquals(0.5, start.pricePerOption().get(1), TOLERANCE);
    }

    @Test
    @DisplayName("Every trade adds a point, and the line moves the way the trade pushed it")
    void everyTradeAddsAPoint() {
        LmsrEvent event = lmsr();
        event.open(marketMaker);

        event.buy(trader, 0, 40);

        assertEquals(2, event.priceHistory().size());
        Event.PriceSample after = event.priceHistory().get(1);
        assertTrue(after.pricePerOption().get(0) > 0.5,
                "buying the first option should have pushed its value up, but it is "
                        + after.pricePerOption().get(0));
        assertTrue(after.pricePerOption().get(1) < 0.5, "and the other option down");
    }

    @Test
    @DisplayName("An order book has no price to draw until somebody actually trades")
    void anOrderBookHasNoPriceUntilItTrades() {
        OrderBookEvent event = orderBook();
        event.open(marketMaker);

        Event.PriceSample start = event.priceHistory().get(0);
        assertNull(start.pricePerOption().get(0),
                "nothing has changed hands, so there is no traded price - and nought would be a lie");
        assertNull(start.pricePerOption().get(1));
    }

    @Test
    @DisplayName("Once an order book trades, the traded price is what the chart draws")
    void anOrderBookDrawsItsTradedPrice() {
        OrderBookEvent event = orderBook();
        event.open(marketMaker);

        event.submitOrder(marketMaker, 0, OrderSide.SELL, 20, 0.60);
        event.submitOrder(trader, 0, OrderSide.BUY, 20, 0.60);

        Event.PriceSample latest = event.priceHistory().get(event.priceHistory().size() - 1);
        assertNotNull(latest.pricePerOption().get(0));
        assertEquals(0.60, latest.pricePerOption().get(0), TOLERANCE);
        assertNull(latest.pricePerOption().get(1), "Spain still has not traded");
    }

    @Test
    @DisplayName("The steps count up, so a chart can put them in order")
    void theStepsCountUp() {
        LmsrEvent event = lmsr();
        event.open(marketMaker);
        event.buy(trader, 0, 10);
        event.buy(trader, 1, 10);

        for (int i = 0; i < event.priceHistory().size(); i++) {
            assertEquals(i, event.priceHistory().get(i).step());
        }
    }
}
