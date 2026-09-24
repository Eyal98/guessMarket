package gm.engine.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A single event people can trade on: what it is, who runs it, its own account, who holds what, and
 * where it stands in its life.
 * <p>
 * Everything here is true of both kinds of event. How shares are priced is not: an LMSR event quotes
 * against a formula while an order book matches people against each other, and the two have almost
 * nothing in common beyond the money moving. That is why this class is sealed over exactly two
 * subclasses rather than delegating to one interface that would be half meaningless to each of them.
 * <p>
 * All the money of an event flows through {@link #account()}. The market maker fills it on opening,
 * trading adds to it, and closing empties it: every holder of the winning option is paid for their
 * own shares, and whatever remains goes back to the market maker who funded it. Every one of those
 * movements that touches a person is written into that person's ledger with a description of its own.
 * <p>
 * An event has no number of its own. Events arrive from many files uploaded by many people, and what
 * tells them apart is their name.
 */
public abstract sealed class Event permits LmsrEvent, OrderBookEvent {

    /** Fewer than this many outcomes would leave nothing to choose between. */
    public static final int MINIMUM_OPTIONS = 2;

    /**
     * Anything left in an event's account smaller than this, after every winner has been paid, is
     * the dust of floating point arithmetic rather than money, and is not worth a line in anybody's
     * ledger.
     */
    private static final double MONEY_DUST = 1e-9;

    private final String name;
    private final String description;
    private final Commission commission;
    private final List<EventOption> options;
    private final Account account = new Account();
    private final List<Trade> history = new ArrayList<>();
    private final Map<User, Holding> holdings = new LinkedHashMap<>();

    private EventStatus status = EventStatus.NOT_STARTED;
    private User marketMaker;
    private EventOption winningOption;
    private double commissionCollected;
    private double totalPaidOut;

    protected Event(String name, String description, Commission commission, List<String> optionNames) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.commission = Objects.requireNonNull(commission, "commission");
        Objects.requireNonNull(optionNames, "optionNames");
        if (optionNames.size() < MINIMUM_OPTIONS) {
            throw new IllegalArgumentException("An event needs at least " + MINIMUM_OPTIONS
                    + " options, but \"" + name + "\" has " + optionNames.size() + ".");
        }
        this.options = optionNames.stream().map(EventOption::new).toList();
    }

    /**
     * Names the user who runs this event: whoever uploaded the file it came in. It can only ever be
     * named once.
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

    /**
     * Starts the event trading, at the market maker's expense.
     * <p>
     * The money is checked before any of it moves, so a market maker who cannot afford the event is
     * turned away without being charged and, in particular, without being blocked for overspending.
     */
    public void open(User actor) {
        requireMarketMaker(actor, "open");
        requireAbleToAct(actor);
        requireMove(EventStatus.ACTIVE, "opened");
        double cost = openingCost();
        if (marketMaker.account().balance() < cost) {
            throw new IllegalStateException(marketMaker.name() + " cannot open \"" + name + "\": it costs "
                    + amount(cost) + " and the account holds " + amount(marketMaker.account().balance())
                    + ". Load more funds first.");
        }
        marketMaker.pay(cost, "Opened \"" + name + "\" (" + openingPurpose() + ")");
        account.deposit(cost);
        status = EventStatus.ACTIVE;
        onOpened();
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
     * @param actor              who is asking, who must be the market maker
     * @param winningOptionIndex the zero based index of the option the event ended on
     */
    public void close(User actor, int winningOptionIndex) {
        requireMarketMaker(actor, "close");
        requireAbleToAct(actor);
        requireMove(EventStatus.CLOSED, "closed");
        winningOption = options.get(winningOptionIndex);
        status = EventStatus.CLOSED;

        for (Map.Entry<User, Holding> entry : holdings.entrySet()) {
            long winningShares = entry.getValue().shares(winningOptionIndex);
            if (winningShares == 0) {
                continue;
            }
            User winner = entry.getKey();
            double gross = winningShares * payoutPerWinningShare();
            double closingFee = commission.closingFee(gross);

            account.withdraw(gross);
            winner.receive(gross, "Won " + winningShares + " of \"" + winningOption.name() + "\" in \""
                    + name + "\"");
            chargeCommission(winner, closingFee, "closing \"" + name + "\"");
            entry.getValue().recordPayout(gross - closingFee);
            totalPaidOut += gross - closingFee;
        }
        settleWhatIsLeft();
        onClosed();
    }

    /**
     * Hands whatever the winners did not take back to the market maker who funded the event. The
     * formulas never leave a real shortfall, but an account can end a hair below zero through
     * floating point alone, so dust is swept away and anything beyond it is settled in whichever
     * direction it points.
     */
    private void settleWhatIsLeft() {
        double leftover = account.balance();
        if (Math.abs(leftover) < MONEY_DUST) {
            return;
        }
        if (leftover > 0) {
            account.withdraw(leftover);
            marketMaker.receive(leftover, "What was left in \"" + name + "\" when it closed");
        } else {
            marketMaker.pay(-leftover, "Covered the shortfall of \"" + name + "\" when it closed");
            account.deposit(-leftover);
        }
    }

    /**
     * Takes a commission from somebody and hands it to the market maker, whose income it is. Both
     * sides get a line in their ledgers, so the market maker can see who paid them and what for.
     *
     * @param forWhat what the commission was charged on, in words that complete "commission for ..."
     */
    protected void chargeCommission(User payer, double fee, String forWhat) {
        if (fee <= 0) {
            return;
        }
        payer.pay(fee, "Commission to " + marketMaker.name() + " for " + forWhat);
        marketMaker.receive(fee, "Commission from " + payer.name() + " for " + forWhat);
        commissionCollected += fee;
    }

    /** What it costs this event's market maker to open it. */
    public abstract double openingCost();

    /** What the opening payment buys, in a few words, for the market maker's ledger. */
    protected abstract String openingPurpose();

    /** What one share of the winning option is worth once the event closes. */
    public abstract double payoutPerWinningShare();

    /** A short description of how this event is traded, for display. */
    public abstract String methodDescription();

    /** Which of the two markets this is, in one word, for filtering and grouping. */
    public abstract String methodKind();

    /**
     * Anything the event itself must do once it has been opened and paid for. LMSR has nothing to do;
     * an order book hands the market maker the stock they have just bought.
     */
    protected void onOpened() {
        // Nothing by default.
    }

    /**
     * Anything the event itself must do once it has been decided. LMSR has nothing to do; an order
     * book cancels whatever never found a match, since it can never be filled now.
     */
    protected void onClosed() {
        // Nothing by default.
    }

    /** Records a completed trade, for the event's history. */
    protected void recordTrade(Trade trade) {
        history.add(trade);
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

    protected long[] sharesPerOption() {
        long[] shares = new long[options.size()];
        for (int i = 0; i < shares.length; i++) {
            shares[i] = options.get(i).sharesBought();
        }
        return shares;
    }

    protected Holding holdingFor(User user) {
        return holdings.computeIfAbsent(user, ignored -> new Holding(options.size()));
    }

    protected void requireAbleToAct(User user) {
        Objects.requireNonNull(user, "user");
        if (user.isBlocked()) {
            throw new IllegalStateException(user.name()
                    + " has already spent past zero and can take no further part in the market.");
        }
    }

    protected static void requirePositive(long quantity, String what) {
        if (quantity < 1) {
            throw new IllegalArgumentException("The number of shares to " + what
                    + " must be at least 1, but it is " + quantity + ".");
        }
    }

    protected void requireTradable(String whatFailed) {
        if (!isOpen()) {
            throw new IllegalStateException(whatFailed + " because the event \"" + name
                    + "\" is " + status.displayName().toLowerCase(Locale.US) + ".");
        }
    }

    /** "10 of "Yes" in "Rain"", the phrase every trading line in a ledger is built around. */
    protected String sharesOf(long quantity, int optionIndex) {
        return quantity + " of \"" + options.get(optionIndex).name() + "\" in \"" + name + "\"";
    }

    /**
     * A price or an amount as a person would write it: never more than four decimals, and no trailing
     * zeros, so a price agreed at 0.58 reads 0.58 even when the arithmetic that produced it did not
     * land on it exactly.
     */
    protected static String amount(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
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
                    + " because it is " + status.displayName().toLowerCase(Locale.US) + ".");
        }
    }
}
