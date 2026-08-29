package gm.engine.model;

import java.util.List;

/**
 * An event traded through an order book, where people buy from and sell to each other rather than to
 * the event.
 * <p>
 * Opening it is not a subsidy but a purchase: the market maker pays the initial amount and receives
 * one share of every option for each base value spent, so with a base value of 1 and an initial of
 * 100 they pay 100 and hold 100 of each. Those shares are theirs to offer to the market.
 */
public final class OrderBookEvent extends Event {

    private static final long serialVersionUID = 1L;

    private final int initialInvestment;
    private final int baseValue;
    private final boolean allowMint;

    public OrderBookEvent(int id, String name, String description, Commission commission,
                          List<String> optionNames, int initialInvestment, int baseValue,
                          boolean allowMint) {
        super(id, name, description, commission, optionNames);
        if (baseValue < 1) {
            throw new IllegalArgumentException(
                    "The base value (d) must be a positive whole number, but it is " + baseValue + ".");
        }
        if (initialInvestment < 0) {
            throw new IllegalArgumentException(
                    "The initial investment cannot be negative, but it is " + initialInvestment + ".");
        }
        this.initialInvestment = initialInvestment;
        this.baseValue = baseValue;
        this.allowMint = allowMint;
    }

    /** What one share of the winning option pays, and the most a whole pair can ever be worth. */
    public int baseValue() {
        return baseValue;
    }

    /** What the market maker pays to stock the market when opening it. */
    public int initialInvestment() {
        return initialInvestment;
    }

    /** Whether two opposing buyers may between them bring new shares into existence. */
    public boolean allowsMint() {
        return allowMint;
    }

    /**
     * The highest price a single share may be offered at. A pair is only ever worth the base value,
     * so no one share can be worth the whole of it.
     */
    public double highestAllowedPrice() {
        return baseValue - 0.01;
    }

    @Override
    public double openingCost() {
        return initialInvestment;
    }

    @Override
    public double payoutPerWinningShare() {
        return baseValue;
    }

    @Override
    public String methodDescription() {
        return "Order book (d=" + baseValue + ", initial=" + initialInvestment
                + ", mint " + (allowMint ? "allowed" : "not allowed") + ")";
    }

    @Override
    public String methodKind() {
        return "Order book";
    }

    /**
     * Hands the market maker the stock they have just paid for: one share of every option for each
     * base value spent, with the money they paid split evenly across the options.
     */
    @Override
    protected void onOpened() {
        long pairs = initialInvestment / baseValue;
        if (pairs == 0) {
            return;
        }
        Holding holding = holdingFor(marketMaker());
        double paidPerOption = (double) initialInvestment / options().size();
        for (int optionIndex = 0; optionIndex < options().size(); optionIndex++) {
            options().get(optionIndex).addShares(pairs);
            holding.recordPurchase(optionIndex, pairs, paidPerOption, 0.0);
        }
    }
}
