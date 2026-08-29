package gm.engine.model;

import gm.engine.method.LmsrMethod;

import java.util.List;

/**
 * An event priced by the Logarithmic Market Scoring Rule.
 * <p>
 * Nobody trades against anybody else here. Buying and selling both happen against the event's own
 * account, which is why the market maker has to subsidise it before anyone can trade: the subsidy is
 * the money the formula pays out of when a buyer turns out to be right.
 */
public final class LmsrEvent extends Event {

    private static final long serialVersionUID = 1L;

    /** Every share of the winning option is worth this much when the event closes. */
    public static final double PAYOUT_PER_WINNING_SHARE = 1.0;

    private final LmsrMethod method;

    public LmsrEvent(int id, String name, String description, Commission commission,
                     List<String> optionNames, int liquidity) {
        super(id, name, description, commission, optionNames);
        this.method = new LmsrMethod(liquidity);
    }

    /** The liquidity index, called b in the course material. */
    public int liquidity() {
        return method.liquidity();
    }

    @Override
    public double openingCost() {
        return method.initialPot(options().size());
    }

    @Override
    public double payoutPerWinningShare() {
        return PAYOUT_PER_WINNING_SHARE;
    }

    @Override
    public String methodDescription() {
        return method.describe();
    }

    @Override
    public String methodKind() {
        return "LMSR";
    }

    /** The current value of one option, between 0 and 1. */
    public double valueOf(int optionIndex) {
        return method.optionValue(sharesPerOption(), optionIndex);
    }

    /** What buying {@code quantity} shares of an option would cost, before commission. */
    public double quoteFor(int optionIndex, long quantity) {
        return method.buyCost(sharesPerOption(), optionIndex, quantity);
    }

    /** What selling {@code quantity} shares of an option back would return. */
    public double quoteForSelling(int optionIndex, long quantity) {
        return method.sellProceeds(sharesPerOption(), optionIndex, quantity);
    }

    /**
     * Buys shares of one option for a user.
     * <p>
     * The price of the shares goes to the event, which is what the event pays out from, and the
     * commission goes straight into the market maker's own account, because in this version the
     * commission is their income rather than the event's.
     */
    public Trade buy(User buyer, int optionIndex, long quantity) {
        requireTradable("Shares cannot be bought");
        requireAbleToAct(buyer);
        requirePositive(quantity, "buy");

        EventOption option = options().get(optionIndex);
        double sharesCost = method.buyCost(sharesPerOption(), optionIndex, quantity);
        double fee = commission().purchaseFee(sharesCost);

        buyer.pay(sharesCost + fee);
        account().deposit(sharesCost);
        marketMaker().receive(fee);
        option.addShares(quantity);
        holdingFor(buyer).recordPurchase(optionIndex, quantity, sharesCost, fee);

        Trade trade = new Trade(buyer.name(), option.name(), quantity, sharesCost, fee);
        recordTrade(trade, fee);
        return trade;
    }

    /**
     * Sells shares back to the event. The money comes out of the event account, and no commission is
     * charged: an on-purchase commission is taken from buyers only.
     */
    public Trade sell(User seller, int optionIndex, long quantity) {
        requireTradable("Shares cannot be sold");
        requireAbleToAct(seller);
        requirePositive(quantity, "sell");

        Holding holding = holdingFor(seller);
        if (quantity > holding.shares(optionIndex)) {
            throw new IllegalArgumentException(seller.name() + " holds only "
                    + holding.shares(optionIndex) + " shares of that option, so " + quantity
                    + " cannot be sold.");
        }

        EventOption option = options().get(optionIndex);
        double proceeds = method.sellProceeds(sharesPerOption(), optionIndex, quantity);

        account().withdraw(proceeds);
        seller.receive(proceeds);
        option.removeShares(quantity);
        holding.recordSale(optionIndex, quantity, proceeds);

        Trade trade = new Trade(seller.name(), option.name(), -quantity, -proceeds, 0.0);
        recordTrade(trade, 0.0);
        return trade;
    }
}
