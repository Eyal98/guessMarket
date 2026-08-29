package gm.engine.model.orderbook;

import gm.engine.model.User;

import java.io.Serializable;
import java.util.Objects;

/**
 * One standing instruction to buy or sell shares of one option at a stated price per share.
 * <p>
 * An order is filled a piece at a time, so it carries both what was asked for and what is left. The
 * sequence number records the order it arrived in, which decides more than politeness: appendix B's
 * mint rule turns on which of two orders was already resting, and the resting one has its price
 * honoured exactly.
 */
public final class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    private final long sequence;
    private final User user;
    private final OrderSide side;
    private final long quantity;
    private final double price;

    private long remaining;

    public Order(long sequence, User user, OrderSide side, long quantity, double price) {
        if (quantity < 1) {
            throw new IllegalArgumentException("An order must be for at least one share, but it is for "
                    + quantity + ".");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("An order needs a price above zero, but it is " + price + ".");
        }
        this.sequence = sequence;
        this.user = Objects.requireNonNull(user, "user");
        this.side = Objects.requireNonNull(side, "side");
        this.quantity = quantity;
        this.price = price;
        this.remaining = quantity;
    }

    /** When this order arrived, relative to the others in its event. */
    public long sequence() {
        return sequence;
    }

    public User user() {
        return user;
    }

    public OrderSide side() {
        return side;
    }

    /** How many shares were originally asked for. */
    public long quantity() {
        return quantity;
    }

    /** What the order is willing to pay, or wants to be paid, for each share. */
    public double price() {
        return price;
    }

    /** How much of the order is still waiting to be filled. */
    public long remaining() {
        return remaining;
    }

    public boolean isFilled() {
        return remaining == 0;
    }

    /** Takes a filled portion off the order. */
    public void reduceBy(long filled) {
        if (filled < 0 || filled > remaining) {
            throw new IllegalArgumentException("Cannot fill " + filled + " shares of an order with "
                    + remaining + " left.");
        }
        remaining -= filled;
    }
}
