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
 * between the two people. Or, where the event allows it, a buyer may meet a buyer of the
 * <em>opposite</em> option whose price added to their own reaches the base value — and then no shares
 * change hands at all: a new pair is brought into existence and both buyers pay the event for their
 * half of it.
 */
public final class OrderBookEvent extends Event {

    private static final long serialVersionUID = 1L;

    /** No single share may be priced at a whole base value, so the finest step below it is a penny. */
    private static final double SMALLEST_PRICE_STEP = 0.01;
    /** Prices are compared in money, so a comparison must not be defeated by a floating point hair. */
    private static final double PRICE_TOLERANCE = 1e-9;

    private final int initialInvestment;
    private final int baseValue;
    private final boolean allowMint;
    /** Always an ArrayList, which is serializable; the declared type simply cannot say so. */
    @SuppressWarnings("serial")
    private final List<OrderBook> books = new ArrayList<>();

    private long ordersReceived;

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
        for (int i = 0; i < optionNames.size(); i++) {
            books.add(new OrderBook());
        }
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
                mintAgainstOpposingBuyers(order, optionIndex, trades);
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
            trades.add(settleResale(resting.user(), incoming.user(), optionIndex, filled, resting.price()));
            incoming.reduceBy(filled);
            resting.reduceBy(filled);
            book.recordTrade(resting.price());
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
            trades.add(settleResale(incoming.user(), resting.user(), optionIndex, filled, resting.price()));
            incoming.reduceBy(filled);
            resting.reduceBy(filled);
            book.recordTrade(resting.price());
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
        double fee = commission().purchaseFee(value);

        buyer.pay(value + fee);
        seller.receive(value);
        marketMaker().receive(fee);

        holdingFor(seller).recordSale(optionIndex, quantity, value);
        holdingFor(buyer).recordPurchase(optionIndex, quantity, value, fee);

        Trade trade = new Trade(buyer.name(), options().get(optionIndex).name(), quantity, value, fee);
        recordTrade(trade, fee);
        return trade;
    }

    /**
     * A buyer of one option meeting buyers of the other whose prices together reach the base value.
     * <p>
     * Nobody gives up any shares here: a new pair is created for each match and both buyers pay the
     * event for their half. The order that was already resting keeps the price it asked for, and the
     * incoming one pays whatever completes the base value — which can only be the same as, or better
     * than, the price it was willing to pay.
     */
    private void mintAgainstOpposingBuyers(Order incoming, int optionIndex, List<Trade> trades) {
        for (int otherIndex = 0; otherIndex < books.size(); otherIndex++) {
            if (otherIndex == optionIndex) {
                continue;
            }
            OrderBook otherBook = books.get(otherIndex);
            for (Order resting : otherBook.bids()) {
                if (incoming.isFilled()
                        || resting.price() + incoming.price() < baseValue - PRICE_TOLERANCE) {
                    break;
                }
                long minted = Math.min(incoming.remaining(), resting.remaining());
                double restingPrice = resting.price();
                double incomingPrice = baseValue - restingPrice;

                trades.add(settleMintedHalf(resting.user(), otherIndex, minted, restingPrice));
                trades.add(settleMintedHalf(incoming.user(), optionIndex, minted, incomingPrice));

                incoming.reduceBy(minted);
                resting.reduceBy(minted);
                otherBook.recordTrade(restingPrice);
                books.get(optionIndex).recordTrade(incomingPrice);
            }
            otherBook.removeFilled();
        }
    }

    /** One buyer's side of a mint: they pay the event, and brand new shares appear in their hands. */
    private Trade settleMintedHalf(User buyer, int optionIndex, long quantity, double price) {
        double value = quantity * price;
        double fee = commission().purchaseFee(value);

        buyer.pay(value + fee);
        account().deposit(value);
        marketMaker().receive(fee);

        options().get(optionIndex).addShares(quantity);
        holdingFor(buyer).recordPurchase(optionIndex, quantity, value, fee);

        Trade trade = new Trade(buyer.name(), options().get(optionIndex).name(), quantity, value, fee);
        recordTrade(trade, fee);
        return trade;
    }

    private void requireSensiblePrice(double price) {
        if (price <= 0) {
            throw new IllegalArgumentException("A price must be above zero, but it is " + price + ".");
        }
        if (price > highestAllowedPrice() + PRICE_TOLERANCE) {
            throw new IllegalArgumentException("A single share cannot be priced at " + price
                    + ": a whole pair is only worth " + baseValue + ", so the most one share can ask is "
                    + highestAllowedPrice() + ".");
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
