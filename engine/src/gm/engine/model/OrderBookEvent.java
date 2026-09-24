package gm.engine.model;

import gm.engine.model.orderbook.Order;
import gm.engine.model.orderbook.OrderBook;
import gm.engine.model.orderbook.OrderSide;

import java.util.ArrayList;
import java.util.List;

/**
 * An event traded through an order book, where people buy from and sell to each other rather than to
 * the event.
 * <p>
 * Opening it is not a subsidy but a purchase: the market maker pays the initial amount and receives
 * one share of every option for each base value spent, so with a base value of 1 and an initial of
 * 100 they pay 100 and hold 100 of each. Those shares are theirs to offer to the market.
 * <p>
 * Two quite different things can happen when an order arrives. It may meet somebody willing to take
 * the other side of that same option, in which case shares change hands and the money passes straight
 * between the two people. Or, where the event allows it, a buyer may meet buyers of <em>every other</em>
 * option whose prices added to their own reach the base value — and then no shares change hands at
 * all: a complete set, one new share of every option, is brought into existence and each buyer pays
 * the event for their part of it. With two options that is the single opposing buyer the course
 * describes; with more, a complete set is the only thing the event can always pay out on, whichever
 * option wins.
 */
public final class OrderBookEvent extends Event {

    /** No single share may be priced at a whole base value, so the finest step below it is a penny. */
    private static final double SMALLEST_PRICE_STEP = 0.01;
    /** Prices are compared in money, so a comparison must not be defeated by a floating point hair. */
    private static final double PRICE_TOLERANCE = 1e-9;

    private final int initialInvestment;
    private final int baseValue;
    private final boolean allowMint;
    private final List<OrderBook> books = new ArrayList<>();

    private long ordersReceived;

    public OrderBookEvent(String name, String description, Commission commission, List<String> optionNames,
                          int initialInvestment, int baseValue, boolean allowMint) {
        super(name, description, commission, optionNames);
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
        for (int i = 0; i < optionNames.size(); i++) {
            books.add(new OrderBook());
        }
    }

    /** What one share of the winning option pays, and so what a complete set is always worth. */
    public int baseValue() {
        return baseValue;
    }

    /** What the market maker pays to stock the market when opening it. */
    public int initialInvestment() {
        return initialInvestment;
    }

    /** Whether buyers of every option may between them bring new shares into existence. */
    public boolean allowsMint() {
        return allowMint;
    }

    /**
     * The highest price a single share may be offered at. A complete set is only ever worth the base
     * value, so no one share can be worth the whole of it.
     */
    public double highestAllowedPrice() {
        return baseValue - SMALLEST_PRICE_STEP;
    }

    /** The market in one option. */
    public OrderBook bookFor(int optionIndex) {
        return books.get(optionIndex);
    }

    @Override
    public double openingCost() {
        return initialInvestment;
    }

    @Override
    protected String openingPurpose() {
        return "opening stock: " + openingSets() + " of each option";
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
        long sets = openingSets();
        if (sets == 0) {
            return;
        }
        Holding holding = holdingFor(marketMaker());
        double paidPerOption = (double) initialInvestment / options().size();
        for (int optionIndex = 0; optionIndex < options().size(); optionIndex++) {
            options().get(optionIndex).addShares(sets);
            holding.recordPurchase(optionIndex, sets, paidPerOption, 0.0);
        }
    }


    /** How many complete sets, one share of every option, the opening investment buys. */
    private long openingSets() {
        return initialInvestment / baseValue;
    }

    /** Closing the market ends it for good, so nothing left waiting could ever be filled. */
    @Override
    protected void onClosed() {
        books.forEach(OrderBook::cancelAll);
    }

    /**
     * Puts an order into the market and settles whatever it can immediately.
     * <p>
     * The order is matched first against the other side of its own option, then — for a buyer, and
     * only where the event allows it — against buyers of the opposite option who between them reach
     * the base value. Whatever is left over rests in the book.
     *
     * @return the trades this order caused, in the order they happened
     */
    public List<Trade> submitOrder(User user, int optionIndex, OrderSide side, long quantity, double price) {
        requireTradable("An order cannot be placed");
        requireAbleToAct(user);
        requirePositive(quantity, side == OrderSide.BUY ? "buy" : "sell");
        requireSensiblePrice(price);
        if (side == OrderSide.SELL) {
            requireEnoughSharesToSell(user, optionIndex, quantity);
        }

        // Taking part starts with the order, not with the fill: the exercise counts somebody as a
        // participant from the moment they place one, whether or not it ever finds a match.
        holdingFor(user);

        Order order = new Order(++ordersReceived, user, side, quantity, price);
        List<Trade> trades = new ArrayList<>();
        if (side == OrderSide.BUY) {
            matchAgainstAsks(order, optionIndex, trades);
            if (allowMint) {
                mintCompleteSets(order, optionIndex, trades);
            }
        } else {
            matchAgainstBids(order, optionIndex, trades);
        }
        if (!order.isFilled()) {
            books.get(optionIndex).rest(order);
        }
        return List.copyOf(trades);
    }

    /** A buyer taking shares off people already offering that same option, cheapest first. */
    private void matchAgainstAsks(Order incoming, int optionIndex, List<Trade> trades) {
        OrderBook book = books.get(optionIndex);
        for (Order resting : book.asks()) {
            if (incoming.isFilled() || resting.price() > incoming.price() + PRICE_TOLERANCE) {
                break;
            }
            long filled = Math.min(incoming.remaining(), resting.remaining());
            book.recordTrade(resting.price());
            trades.add(settleResale(resting.user(), incoming.user(), optionIndex, filled, resting.price()));
            incoming.reduceBy(filled);
            resting.reduceBy(filled);
        }
        book.removeFilled();
    }

    /** A seller working through the people already bidding for that same option, best offer first. */
    private void matchAgainstBids(Order incoming, int optionIndex, List<Trade> trades) {
        OrderBook book = books.get(optionIndex);
        for (Order resting : book.bids()) {
            if (incoming.isFilled() || resting.price() < incoming.price() - PRICE_TOLERANCE) {
                break;
            }
            long filled = Math.min(incoming.remaining(), resting.remaining());
            book.recordTrade(resting.price());
            trades.add(settleResale(incoming.user(), resting.user(), optionIndex, filled, resting.price()));
            incoming.reduceBy(filled);
            resting.reduceBy(filled);
        }
        book.removeFilled();
    }

    /**
     * Shares changing hands between two people. The money goes straight from buyer to seller and the
     * event account is not involved, because nothing new was created. Commission is charged to the
     * buyer on top, and is the market maker's income.
     */
    private Trade settleResale(User seller, User buyer, int optionIndex, long quantity, double price) {
        double value = quantity * price;
        double fee = purchaseFeeFor(buyer, value);

        String at = " at " + amount(price);
        buyer.pay(value, "Bought " + sharesOf(quantity, optionIndex) + " from " + seller.name() + at);
        seller.receive(value, "Sold " + sharesOf(quantity, optionIndex) + " to " + buyer.name() + at);
        chargeCommission(buyer, fee, "buying " + sharesOf(quantity, optionIndex));

        holdingFor(seller).recordSale(optionIndex, quantity, value);
        holdingFor(buyer).recordPurchase(optionIndex, quantity, value, fee);

        Trade trade = new Trade(buyer.name(), options().get(optionIndex).name(), quantity, value, fee);
        recordTrade(trade);
        return trade;
    }

    /**
     * A buyer of one option meeting the best buyers of every other option, whose prices together reach
     * the base value.
     * <p>
     * Nobody gives up any shares here: a complete set is created for each unit matched and every buyer
     * pays the event for their share of it. The orders already resting keep the prices they asked for,
     * and the incoming one pays whatever completes the base value — which can only be the same as, or
     * better than, the price it was willing to pay. Sets are minted in the largest quantity every one
     * of the buyers can take, and the matching carries on for as long as the best remaining bids still
     * reach the base value.
     */
    private void mintCompleteSets(Order incoming, int optionIndex, List<Trade> trades) {
        while (!incoming.isFilled()) {
            List<Order> partners = bestBidOfEveryOtherOption(optionIndex);
            if (partners.isEmpty()) {
                return;
            }
            double partnersPay = partners.stream().mapToDouble(Order::price).sum();
            if (partnersPay + incoming.price() < baseValue - PRICE_TOLERANCE) {
                return;
            }
            long sets = incoming.remaining();
            for (Order partner : partners) {
                sets = Math.min(sets, partner.remaining());
            }
            for (Order partner : partners) {
                int partnerOption = optionOf(partner);
                books.get(partnerOption).recordTrade(partner.price());
                trades.add(settleMintedShare(partner.user(), partnerOption, sets, partner.price()));
                partner.reduceBy(sets);
                books.get(partnerOption).removeFilled();
            }
            double incomingPrice = baseValue - partnersPay;
            books.get(optionIndex).recordTrade(incomingPrice);
            trades.add(settleMintedShare(incoming.user(), optionIndex, sets, incomingPrice));
            incoming.reduceBy(sets);
        }
    }

    /**
     * The best bid resting on every option but one, in option order, or nothing at all if any of them
     * has no bid: a set with a missing share cannot be minted.
     */
    private List<Order> bestBidOfEveryOtherOption(int exceptOption) {
        List<Order> best = new ArrayList<>();
        for (int other = 0; other < books.size(); other++) {
            if (other == exceptOption) {
                continue;
            }
            List<Order> bids = books.get(other).bids();
            if (bids.isEmpty()) {
                return List.of();
            }
            best.add(bids.get(0));
        }
        return best;
    }

    private int optionOf(Order resting) {
        for (int option = 0; option < books.size(); option++) {
            if (books.get(option).bids().contains(resting)) {
                return option;
            }
        }
        throw new IllegalStateException("A resting order was found in no book at all.");
    }

    /** One buyer's part of a mint: they pay the event, and brand new shares appear in their hands. */
    private Trade settleMintedShare(User buyer, int optionIndex, long quantity, double price) {
        double value = quantity * price;
        double fee = purchaseFeeFor(buyer, value);

        buyer.pay(value, "Bought " + quantity + " new shares of \"" + options().get(optionIndex).name()
                + "\" in \"" + name() + "\" at " + amount(price));
        account().deposit(value);
        chargeCommission(buyer, fee, "buying " + sharesOf(quantity, optionIndex));

        options().get(optionIndex).addShares(quantity);
        holdingFor(buyer).recordPurchase(optionIndex, quantity, value, fee);

        Trade trade = new Trade(buyer.name(), options().get(optionIndex).name(), quantity, value, fee);
        recordTrade(trade);
        return trade;
    }

    private void requireSensiblePrice(double price) {
        if (price <= 0) {
            throw new IllegalArgumentException("A price must be above zero, but it is " + price + ".");
        }
        if (price > highestAllowedPrice() + PRICE_TOLERANCE) {
            throw new IllegalArgumentException("A single share cannot be priced at " + price
                    + ": a winning share is only ever worth " + baseValue + ", so the most one share can"
                    + " ask is " + amount(highestAllowedPrice()) + ".");
        }
    }

    private void requireEnoughSharesToSell(User seller, int optionIndex, long quantity) {
        long held = holdingOf(seller).shares(optionIndex);
        if (quantity > held) {
            throw new IllegalArgumentException(seller.name() + " holds " + held + " shares of \""
                    + options().get(optionIndex).name() + "\", so " + quantity + " cannot be offered.");
        }
    }
}
