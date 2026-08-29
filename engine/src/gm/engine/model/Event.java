package gm.engine.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
 * own shares, and whatever remains goes back to the market maker who funded it.
 */
public abstract sealed class Event implements Serializable permits LmsrEvent, OrderBookEvent {

    private static final long serialVersionUID = 1L;

    /** Fewer than this many outcomes would leave nothing to choose between. */
    public static final int MINIMUM_OPTIONS = 2;

    /**
     * Where every option stood after one particular trade.
     * <p>
     * A price may be missing. An order book has nothing to report until two people have actually
     * agreed on something, and writing nought there would claim a share is worthless when the truth
     * is that nobody has said yet, so the entry is null and whatever draws the chart leaves a gap.
     */
    public record PriceSample(int step, List<Double> pricePerOption) implements Serializable {
        public PriceSample {
            pricePerOption = Collections.unmodifiableList(new ArrayList<>(pricePerOption));
        }
    }

    private final int id;
    private final String name;
    private final String description;
    private final Commission commission;
    /** Always an immutable list, which is serializable; the declared type simply cannot say so. */
    @SuppressWarnings("serial")
    private final List<EventOption> options;
    private final Account account = new Account();
    /** Always an ArrayList, which is serializable; the declared type simply cannot say so. */
    @SuppressWarnings("serial")
    private final List<Trade> history = new ArrayList<>();
    /** Always an ArrayList, which is serializable; the declared type simply cannot say so. */
    @SuppressWarnings("serial")
    private final List<PriceSample> priceHistory = new ArrayList<>();
    /** Always a LinkedHashMap, which is serializable; the declared type simply cannot say so. */
    @SuppressWarnings("serial")
    private final Map<User, Holding> holdings = new LinkedHashMap<>();

    private EventStatus status = EventStatus.NOT_STARTED;
    private User marketMaker;
    private EventOption winningOption;
    private double commissionCollected;
    private double totalPaidOut;

    protected Event(int id, String name, String description, Commission commission,
                    List<String> optionNames) {
        this.id = id;
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
        onOpened();
        rememberPrices();
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
            double gross = winningShares * payoutPerWinningShare();
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
        onClosed();
    }

    /** What it costs this event's market maker to open it. */
    public abstract double openingCost();

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

    public int id() {
        return id;
    }

    /**
     * Records a completed trade and the commission it earned the market maker.
     * <p>
     * Every settlement in both kinds of event ends here, which makes it the one place that has to
     * remember where the prices stood afterwards. Hooking the chart in at this single point means no
     * future trading path can be added and quietly forget to record itself.
     */
    protected void recordTrade(Trade trade, double commissionEarned) {
        history.add(trade);
        commissionCollected += commissionEarned;
        rememberPrices();
    }

    /**
     * Every set of prices this event has stood at, oldest first, beginning with the moment it opened.
     * This is what a chart of the market is drawn from.
     */
    public List<PriceSample> priceHistory() {
        return List.copyOf(priceHistory);
    }

    private void rememberPrices() {
        priceHistory.add(new PriceSample(priceHistory.size(), currentPrices()));
    }

    /**
     * What each option is worth at this moment, in the order the options are listed, with null for an
     * option the market cannot price yet.
     */
    protected abstract List<Double> currentPrices();

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
        requireOpen(whatFailed);
    }

    protected void requireOpen(String whatFailed) {
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
