package gm.engine.model;

import gm.engine.method.TradingMethod;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    private EventStatus status = EventStatus.ACTIVE;
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
     * Moves the subsidy this event needs from the market maker into the event account. Called once,
     * when the system loads the event.
     */
    public void fundSubsidy(Account marketMaker) {
        double subsidy = method.initialPot(options.size());
        marketMaker.withdraw(subsidy);
        account.deposit(subsidy);
    }

    /**
     * Buys shares of one option and records the trade.
     *
     * @param optionIndex the zero based index of the option being bought
     * @param quantity    how many shares to buy, must be positive
     * @return the trade that was carried out, including what it cost
     */
    public Trade buy(int optionIndex, long quantity) {
        requireOpen("Shares cannot be bought");
        EventOption option = options.get(optionIndex);
        double sharesCost = method.buyCost(sharesPerOption(), optionIndex, quantity);
        double fee = commission.purchaseFee(sharesCost);

        option.addShares(quantity);
        account.deposit(sharesCost + fee);
        commissionCollected += fee;

        Trade trade = new Trade(option.name(), quantity, sharesCost, fee);
        history.add(trade);
        return trade;
    }

    /**
     * Decides the event: pays the holders of the winning option, takes the closing commission if the
     * event charges one, and returns what is left to the market maker.
     *
     * @param winningOptionIndex the zero based index of the option the event ended on
     * @param marketMaker        the account that funded the subsidy
     */
    public void close(int winningOptionIndex, Account marketMaker) {
        requireOpen("The event cannot be closed");
        winningOption = options.get(winningOptionIndex);
        status = EventStatus.CLOSED;

        double grossPayout = winningOption.sharesBought() * PAYOUT_PER_WINNING_SHARE;
        double closingFee = commission.closingFee(grossPayout);
        totalPaidOut = grossPayout - closingFee;
        commissionCollected += closingFee;

        account.withdraw(totalPaidOut);
        account.drainInto(marketMaker);
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

    private void requireOpen(String whatFailed) {
        if (!isOpen()) {
            throw new IllegalStateException(whatFailed + " because the event \"" + name + "\" is already closed.");
        }
    }
}
