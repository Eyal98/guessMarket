package gm.engine.model;

import gm.engine.method.TradingMethod;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A single event people can trade on: its description, its options, the method that prices them, its
 * own account and everything that has happened to it so far.
 * <p>
 * All the money of an event flows through {@link #account()}. It is filled with the subsidy when the
 * event is created, grows with every purchase, and is emptied when the event closes: the winners are
 * paid, and whatever is left goes back to the market maker who funded it. The pot can never run dry,
 * because the LMSR cost function is always at least as large as the largest number of shares held in
 * any one option.
 */
public final class Event implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Every share of the winning option is worth this much when the event closes. */
    public static final double PAYOUT_PER_WINNING_SHARE = 1.0;

    /** Fewer than this many outcomes would leave nothing to choose between. */
    public static final int MINIMUM_OPTIONS = 2;

    private final int id;
    private final String name;
    private final String description;
    private final Commission commission;
    /** Always an immutable list, which is serializable; the declared type simply cannot say so. */
    @SuppressWarnings("serial")
    private final List<EventOption> options;
    private final TradingMethod method;
    private final Account account = new Account();
    /** Always an ArrayList, which is serializable; the declared type simply cannot say so. */
    @SuppressWarnings("serial")
    private final List<Trade> history = new ArrayList<>();
    /** Always a LinkedHashMap, which is serializable; the declared type simply cannot say so. */
    @SuppressWarnings("serial")
    private final Map<User, Holding> holdings = new LinkedHashMap<>();

    private EventStatus status = EventStatus.NOT_STARTED;
    private User marketMaker;
    private EventOption winningOption;
    private double commissionCollected;
    private double totalPaidOut;

    public Event(int id, String name, String description, Commission commission,
                 List<String> optionNames, TradingMethod method) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.commission = Objects.requireNonNull(commission, "commission");
        this.method = Objects.requireNonNull(method, "method");
        Objects.requireNonNull(optionNames, "optionNames");
        if (optionNames.size() < MINIMUM_OPTIONS) {
            throw new IllegalArgumentException("An event needs at least " + MINIMUM_OPTIONS
                    + " options, but \"" + name + "\" has " + optionNames.size() + ".");
        }
        this.options = optionNames.stream().map(EventOption::new).toList();
    }

    /**
     * Names the user who runs this event. The file lists events before it lists users, so the market
     * maker arrives after the event itself and can only ever be named once.
     */
    public void assignMarketMaker(User owner) {
        if (marketMaker != null) {
            throw new IllegalStateException("The event \"" + name + "\" already belongs to "
                    + marketMaker.name() + ".");
        }
        this.marketMaker = Objects.requireNonNull(owner, "owner");
    }

    public User marketMaker() {
        return marketMaker;
    }

    /** What it costs the market maker to open this event. */
    public double openingCost() {
        return method.initialPot(options.size());
    }

    /**
     * Starts the event trading, at the market maker's expense.
     * <p>
     * The money is checked before any of it moves, so a market maker who cannot afford the event is
     * turned away without being charged and, in particular, without being blocked for overspending.
     */
    public void open(User actor) {
        requireMarketMaker(actor, "open");
        requireMove(EventStatus.ACTIVE, "opened");
        double cost = openingCost();
        if (marketMaker.account().balance() < cost) {
            throw new IllegalStateException(marketMaker.name() + " cannot open \"" + name + "\": it costs "
                    + cost + " and the account holds " + marketMaker.account().balance() + ".");
        }
        marketMaker.pay(cost);
        account.deposit(cost);
        status = EventStatus.ACTIVE;
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

        EventOption option = options.get(optionIndex);
        double sharesCost = method.buyCost(sharesPerOption(), optionIndex, quantity);
        double fee = commission.purchaseFee(sharesCost);

        buyer.pay(sharesCost + fee);
        account.deposit(sharesCost);
        marketMaker.receive(fee);
        commissionCollected += fee;
        option.addShares(quantity);
        holdingFor(buyer).recordPurchase(optionIndex, quantity, sharesCost, fee);

        Trade trade = new Trade(buyer.name(), option.name(), quantity, sharesCost, fee);
        history.add(trade);
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

        EventOption option = options.get(optionIndex);
        double proceeds = method.sellProceeds(sharesPerOption(), optionIndex, quantity);

        account.withdraw(proceeds);
        seller.receive(proceeds);
        option.removeShares(quantity);
        holding.recordSale(optionIndex, quantity, proceeds);

        Trade trade = new Trade(seller.name(), option.name(), -quantity, -proceeds, 0.0);
        history.add(trade);
        return trade;
    }

    /** What this user holds here. Reading it does not make them a participant. */
    public Holding holdingOf(User user) {
        Holding holding = holdings.get(user);
        return holding == null ? new Holding(options.size()) : holding;
    }

    /** Everyone who has ever acted on this event, in the order they first did. */
    public List<User> participants() {
        return List.copyOf(holdings.keySet());
    }

    /**
     * Decides the event: pays the holders of the winning option, takes the closing commission if the
     * event charges one, and returns what is left to the market maker.
     *
     * @param winningOptionIndex the zero based index of the option the event ended on
     * @param marketMaker        the account that funded the subsidy
     */
    public void close(User actor, int winningOptionIndex) {
        requireMarketMaker(actor, "close");
        requireMove(EventStatus.CLOSED, "closed");
        winningOption = options.get(winningOptionIndex);
        status = EventStatus.CLOSED;

        for (Map.Entry<User, Holding> entry : holdings.entrySet()) {
            long winningShares = entry.getValue().shares(winningOptionIndex);
            if (winningShares == 0) {
                continue;
            }
            double gross = winningShares * PAYOUT_PER_WINNING_SHARE;
            double closingFee = commission.closingFee(gross);
            double net = gross - closingFee;

            account.withdraw(gross);
            marketMaker.receive(closingFee);
            entry.getKey().receive(net);
            entry.getValue().recordPayout(net);
            commissionCollected += closingFee;
            totalPaidOut += net;
        }
        account.drainInto(marketMaker.account());
    }

    /** The current value of one option, between 0 and 1. */
    public double valueOf(int optionIndex) {
        return method.optionValue(sharesPerOption(), optionIndex);
    }

    /** What buying {@code quantity} shares of an option would cost, before commission. */
    public double quoteFor(int optionIndex, long quantity) {
        return method.buyCost(sharesPerOption(), optionIndex, quantity);
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Commission commission() {
        return commission;
    }

    /** The options of this event, in the order they were declared. */
    public List<EventOption> options() {
        return options;
    }

    public Account account() {
        return account;
    }

    /** The trades of this event, oldest first. */
    public List<Trade> history() {
        return Collections.unmodifiableList(history);
    }

    public EventStatus status() {
        return status;
    }

    public boolean isOpen() {
        return status.allowsTrading();
    }

    /** The option the event ended on, or {@code null} while the event is still open. */
    public EventOption winningOption() {
        return winningOption;
    }

    /** Everything this event has taken in commission, whenever it was charged. */
    public double commissionCollected() {
        return commissionCollected;
    }

    /** What the winners received when the event closed, zero while it is open. */
    public double totalPaidOut() {
        return totalPaidOut;
    }

    public String methodDescription() {
        return method.describe();
    }

    private long[] sharesPerOption() {
        long[] shares = new long[options.size()];
        for (int i = 0; i < shares.length; i++) {
            shares[i] = options.get(i).sharesBought();
        }
        return shares;
    }

    private Holding holdingFor(User user) {
        return holdings.computeIfAbsent(user, ignored -> new Holding(options.size()));
    }

    private void requireAbleToAct(User user) {
        Objects.requireNonNull(user, "user");
        if (user.isBlocked()) {
            throw new IllegalStateException(user.name()
                    + " has already spent past zero and can take no further part in the market.");
        }
    }

    private static void requirePositive(long quantity, String what) {
        if (quantity < 1) {
            throw new IllegalArgumentException("The number of shares to " + what
                    + " must be at least 1, but it is " + quantity + ".");
        }
    }

    private void requireTradable(String whatFailed) {
        requireOpen(whatFailed);
    }

    private void requireOpen(String whatFailed) {
        if (!isOpen()) {
            throw new IllegalStateException(whatFailed + " because the event \"" + name
                    + "\" is " + status.displayName().toLowerCase(java.util.Locale.US) + ".");
        }
    }

    private void requireMarketMaker(User actor, String what) {
        if (marketMaker == null) {
            throw new IllegalStateException("The event \"" + name + "\" has no market maker to " + what + " it.");
        }
        if (actor != marketMaker) {
            throw new IllegalStateException("Only " + marketMaker.name() + " can " + what + " \"" + name
                    + "\", and the request came from " + (actor == null ? "nobody" : actor.name()) + ".");
        }
    }

    private void requireMove(EventStatus next, String what) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("The event \"" + name + "\" cannot be " + what
                    + " because it is " + status.displayName().toLowerCase(java.util.Locale.US) + ".");
        }
    }
}
