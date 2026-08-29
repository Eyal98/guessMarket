package gm.engine.model.orderbook;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * The market in one option: everyone waiting to buy it and everyone waiting to sell it.
 * <p>
 * Both sides are kept in the order they would be served. Buyers queue with the best offer first and
 * sellers with the cheapest first; at the same price the order that arrived earlier goes first. That
 * last rule is not mere fairness — appendix B's mint rule turns on which of two orders was already
 * resting, and the resting one keeps its own price.
 * <p>
 * Every price here can be absent rather than zero. A book with nothing to sell has no ask, and no
 * spread either; saying so is the honest answer and the supplied simulation shows a dash in exactly
 * those places.
 */
public final class OrderBook implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Best offer first, and among equals whoever arrived first. */
    private static final Comparator<Order> BEST_BID_FIRST =
            Comparator.comparingDouble(Order::price).reversed().thenComparingLong(Order::sequence);
    /** Cheapest first, and among equals whoever arrived first. */
    private static final Comparator<Order> BEST_ASK_FIRST =
            Comparator.comparingDouble(Order::price).thenComparingLong(Order::sequence);

    /** Always ArrayLists, which are serializable; the declared types simply cannot say so. */
    @SuppressWarnings("serial")
    private final List<Order> bids = new ArrayList<>();
    @SuppressWarnings("serial")
    private final List<Order> asks = new ArrayList<>();

    private Double lastTradedPrice;

    /** Puts an order into the book, in the place it will be served from. */
    public void rest(Order order) {
        List<Order> side = order.side() == OrderSide.BUY ? bids : asks;
        side.add(order);
        side.sort(order.side() == OrderSide.BUY ? BEST_BID_FIRST : BEST_ASK_FIRST);
    }

    /** Everyone waiting to buy, best offer first. */
    public List<Order> bids() {
        return List.copyOf(bids);
    }

    /** Everyone waiting to sell, cheapest first. */
    public List<Order> asks() {
        return List.copyOf(asks);
    }

    /** The most anyone is currently willing to pay. */
    public OptionalDouble bestBid() {
        return bids.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(bids.get(0).price());
    }

    /** The least anyone is currently willing to accept. */
    public OptionalDouble bestAsk() {
        return asks.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(asks.get(0).price());
    }

    /** The gap between the two sides, which needs both of them to exist. */
    public OptionalDouble spread() {
        return bestBid().isPresent() && bestAsk().isPresent()
                ? OptionalDouble.of(bestAsk().getAsDouble() - bestBid().getAsDouble())
                : OptionalDouble.empty();
    }

    /** Halfway between the two sides, usually the best guess at what a share is worth. */
    public OptionalDouble midPrice() {
        return bestBid().isPresent() && bestAsk().isPresent()
                ? OptionalDouble.of((bestBid().getAsDouble() + bestAsk().getAsDouble()) / 2)
                : OptionalDouble.empty();
    }

    /** What the most recent trade in this option went through at. */
    public OptionalDouble lastTradedPrice() {
        return lastTradedPrice == null ? OptionalDouble.empty() : OptionalDouble.of(lastTradedPrice);
    }

    public void recordTrade(double price) {
        lastTradedPrice = price;
    }

    /** Drops every order that has nothing left to fill. */
    public void removeFilled() {
        bids.removeIf(Order::isFilled);
        asks.removeIf(Order::isFilled);
    }

    /** Empties the book. Closing an event cancels whatever never found a match. */
    public void cancelAll() {
        bids.clear();
        asks.clear();
    }
}
