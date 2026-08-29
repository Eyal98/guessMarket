package gm.engine.model.orderbook;

import gm.engine.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One side of one option's market: the orders resting in it and what they say about the price.
 * <p>
 * Ordering is the whole point of a book. Buyers queue best offer first, sellers cheapest first, and
 * within a price the one that arrived earlier is served earlier — which is what makes the mint rule
 * in appendix B decidable, since it turns on which of two orders was already resting.
 */
class OrderBookTest {

    private static final double TOLERANCE = 0.0001;

    private final User bob = new User("Bob", 1000);
    private final User carol = new User("Carol", 1000);
    private final User zoe = new User("Zoe", 1000);

    private static Order buy(User user, long quantity, double price, long sequence) {
        return new Order(sequence, user, OrderSide.BUY, quantity, price);
    }

    private static Order sell(User user, long quantity, double price, long sequence) {
        return new Order(sequence, user, OrderSide.SELL, quantity, price);
    }

    @Test
    @DisplayName("An empty book quotes nothing at all")
    void anEmptyBookQuotesNothing() {
        OrderBook book = new OrderBook();

        assertTrue(book.bestBid().isEmpty());
        assertTrue(book.bestAsk().isEmpty());
        assertTrue(book.spread().isEmpty());
        assertTrue(book.midPrice().isEmpty());
        assertTrue(book.lastTradedPrice().isEmpty());
        assertTrue(book.bids().isEmpty());
        assertTrue(book.asks().isEmpty());
    }

    @Test
    @DisplayName("Buyers queue with the best offer first")
    void buyersQueueBestFirst() {
        OrderBook book = new OrderBook();
        book.rest(buy(carol, 15, 0.48, 2));
        book.rest(buy(bob, 20, 0.50, 1));

        assertEquals(List.of("Bob", "Carol"), book.bids().stream().map(o -> o.user().name()).toList());
        assertEquals(0.50, book.bestBid().getAsDouble(), TOLERANCE);
    }

    @Test
    @DisplayName("Sellers queue with the cheapest first")
    void sellersQueueCheapestFirst() {
        OrderBook book = new OrderBook();
        book.rest(sell(zoe, 15, 0.65, 2));
        book.rest(sell(zoe, 25, 0.58, 1));

        assertEquals(List.of(0.58, 0.65), book.asks().stream().map(Order::price).toList());
        assertEquals(0.58, book.bestAsk().getAsDouble(), TOLERANCE);
    }

    @Test
    @DisplayName("At the same price the order that arrived first is served first")
    void tiesGoToWhoeverWasThereFirst() {
        OrderBook book = new OrderBook();
        book.rest(buy(carol, 10, 0.50, 7));
        book.rest(buy(bob, 10, 0.50, 3));
        book.rest(buy(zoe, 10, 0.50, 5));

        assertEquals(List.of("Bob", "Zoe", "Carol"), book.bids().stream().map(o -> o.user().name()).toList());
    }

    @Test
    @DisplayName("Spread and mid-price need both sides of the book")
    void spreadAndMidNeedBothSides() {
        OrderBook book = new OrderBook();
        book.rest(buy(bob, 20, 0.50, 1));

        assertTrue(book.spread().isEmpty(), "a spread with nothing to sell against is not a spread");

        book.rest(sell(zoe, 25, 0.58, 2));

        assertEquals(0.08, book.spread().getAsDouble(), TOLERANCE);
        assertEquals(0.54, book.midPrice().getAsDouble(), TOLERANCE);
    }

    @Test
    @DisplayName("The last traded price is remembered once something trades")
    void theLastPriceIsRemembered() {
        OrderBook book = new OrderBook();

        book.recordTrade(0.58);

        assertEquals(0.58, book.lastTradedPrice().getAsDouble(), TOLERANCE);
    }

    @Test
    @DisplayName("An order that is used up leaves the book")
    void filledOrdersLeaveTheBook() {
        OrderBook book = new OrderBook();
        Order order = buy(bob, 20, 0.50, 1);
        book.rest(order);

        order.reduceBy(20);
        book.removeFilled();

        assertTrue(book.bids().isEmpty());
        assertTrue(order.isFilled());
    }

    @Test
    @DisplayName("A partly used order stays, with only what is left of it")
    void partlyFilledOrdersKeepTheirRemainder() {
        OrderBook book = new OrderBook();
        Order order = buy(carol, 15, 0.48, 1);
        book.rest(order);

        order.reduceBy(10);
        book.removeFilled();

        assertEquals(1, book.bids().size());
        assertEquals(5, book.bids().get(0).remaining());
        assertFalse(order.isFilled());
    }

    @Test
    @DisplayName("Closing the event cancels whatever never found a match")
    void closingCancelsWhatIsLeft() {
        OrderBook book = new OrderBook();
        book.rest(buy(bob, 20, 0.50, 1));
        book.rest(sell(zoe, 25, 0.58, 2));

        book.cancelAll();

        assertTrue(book.bids().isEmpty());
        assertTrue(book.asks().isEmpty());
    }
}
