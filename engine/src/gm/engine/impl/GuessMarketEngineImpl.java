package gm.engine.impl;

import gm.dto.EventInfoDto;
import gm.dto.LedgerDto;
import gm.dto.LedgerLineDto;
import gm.dto.MarketStateDto;
import gm.dto.OptionHoldingDto;
import gm.dto.OptionMarketDto;
import gm.dto.OptionStateDto;
import gm.dto.OrderBookStateDto;
import gm.dto.OrderDto;
import gm.dto.ParticipantDto;
import gm.dto.ParticipationDto;
import gm.dto.PurchaseResultDto;
import gm.dto.TradeDto;
import gm.dto.UploadResultDto;
import gm.dto.UserDetailDto;
import gm.dto.UserDto;
import gm.engine.api.GuessMarketEngine;
import gm.engine.api.InvalidSelectionException;
import gm.engine.model.Commission;
import gm.engine.model.CommissionType;
import gm.engine.model.Event;
import gm.engine.model.EventOption;
import gm.engine.model.Holding;
import gm.engine.model.LmsrEvent;
import gm.engine.model.Market;
import gm.engine.model.OrderBookEvent;
import gm.engine.model.Trade;
import gm.engine.model.User;
import gm.engine.model.orderbook.Order;
import gm.engine.model.orderbook.OrderBook;
import gm.engine.model.orderbook.OrderSide;
import gm.engine.xml.EventsFileLoader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * The working engine. It holds the market, checks every selection that comes in, and hands back plain
 * data objects that travel to a client without anything of the model attached.
 * <p>
 * The server calls it from many threads at once. One read-write lock guards the whole market: any
 * number of questions may be answered together, while a change waits for them to finish and then has
 * the market to itself. A finer grained scheme would buy nothing here — every change is a few
 * arithmetic steps — and would be far easier to get wrong. Every answer is built completely while the
 * lock is held, so what leaves the engine is a consistent picture of one moment, never half of one
 * trade.
 * <p>
 * A faulty upload cannot disturb the market: the file is read in full first, and its events join only
 * if the whole file was sound.
 */
public final class GuessMarketEngineImpl implements GuessMarketEngine {

    private final EventsFileLoader fileLoader = new EventsFileLoader();
    private final Market market = new Market();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public UserDetailDto enterMarket(String userName) {
        if (userName == null || userName.isBlank()) {
            throw new InvalidSelectionException("A user needs a name.");
        }
        return changing(() -> detailOf(market.enter(userName)));
    }

    @Override
    public List<UserDto> listUsers() {
        return reading(() -> market.users().stream()
                .map(user -> new UserDto(user.name(), user.account().balance(), user.isBlocked(),
                        runsAnything(user)))
                .toList());
    }

    @Override
    public UserDetailDto userDetail(String userName) {
        return reading(() -> detailOf(userNamed(userName)));
    }

    @Override
    public LedgerDto ledger(String userName, int after) {
        if (after < 0) {
            throw new InvalidSelectionException("The number of lines already held cannot be negative,"
                    + " but it is " + after + ".");
        }
        return reading(() -> {
            List<User.LedgerLine> lines = userNamed(userName).ledger();
            List<LedgerLineDto> fresh = new ArrayList<>();
            for (int i = after; i < lines.size(); i++) {
                User.LedgerLine line = lines.get(i);
                fresh.add(new LedgerLineDto(line.number(), line.description(), line.amount(),
                        line.balanceAfter()));
            }
            return new LedgerDto(after, List.copyOf(fresh));
        });
    }

    @Override
    public UserDetailDto deposit(String userName, double amount) {
        return changing(() -> {
            User user = userNamed(userName);
            asSelectionFailure(() -> user.deposit(amount));
            return detailOf(user);
        });
    }

    @Override
    public UploadResultDto uploadEvents(String uploaderName, String fileName, InputStream content) {
        return changing(() -> {
            User uploader = userNamed(uploaderName);
            List<Event> arriving = fileLoader.read(fileName, content, market::hasEventNamed);
            arriving.forEach(event -> event.assignMarketMaker(uploader));
            market.addEvents(arriving);
            return new UploadResultDto(fileName.trim(), arriving.stream().map(Event::name).toList());
        });
    }

    @Override
    public List<EventInfoDto> listEvents() {
        return reading(() -> {
            List<Event> events = market.events();
            List<EventInfoDto> infos = new ArrayList<>(events.size());
            for (int i = 0; i < events.size(); i++) {
                infos.add(infoOf(events.get(i), i + 1));
            }
            return List.copyOf(infos);
        });
    }

    @Override
    public MarketStateDto marketState(int eventNumber) {
        return reading(() -> stateOf(lmsrEventAt(eventNumber), eventNumber));
    }

    @Override
    public OrderBookStateDto orderBookState(int eventNumber) {
        return reading(() -> {
            OrderBookEvent event = orderBookEventAt(eventNumber);
            List<OptionMarketDto> markets = new ArrayList<>();
            for (int i = 0; i < event.options().size(); i++) {
                markets.add(marketOf(event, i));
            }
            List<ParticipantDto> participants = new ArrayList<>();
            for (User user : event.participants()) {
                participants.add(new ParticipantDto(user.name(), holdingsOf(event, user), user.isBlocked()));
            }
            return new OrderBookStateDto(infoOf(event, eventNumber), List.copyOf(markets),
                    event.account().balance(), event.commissionCollected(), List.copyOf(participants),
                    event.baseValue(), event.allowsMint(), event.highestAllowedPrice());
        });
    }

    @Override
    public EventInfoDto openEvent(int eventNumber, String userName) {
        return changing(() -> {
            Event event = eventAt(eventNumber);
            User actor = userNamed(userName);
            asSelectionFailure(() -> event.open(actor));
            return infoOf(event, eventNumber);
        });
    }

    @Override
    public EventInfoDto closeEvent(int eventNumber, String userName, int winningOptionNumber) {
        return changing(() -> {
            Event event = eventAt(eventNumber);
            User actor = userNamed(userName);
            int optionIndex = optionIndexIn(event, winningOptionNumber);
            asSelectionFailure(() -> event.close(actor, optionIndex));
            return infoOf(event, eventNumber);
        });
    }

    @Override
    public PurchaseResultDto buyShares(int eventNumber, String userName, int optionNumber, long quantity) {
        return changing(() -> {
            LmsrEvent event = lmsrEventAt(eventNumber);
            User buyer = userNamed(userName);
            int optionIndex = optionIndexIn(event, optionNumber);
            Trade trade = asSelectionFailure(() -> event.buy(buyer, optionIndex, quantity));
            return receiptFor(trade, event, eventNumber);
        });
    }

    @Override
    public PurchaseResultDto sellShares(int eventNumber, String userName, int optionNumber, long quantity) {
        return changing(() -> {
            LmsrEvent event = lmsrEventAt(eventNumber);
            User seller = userNamed(userName);
            int optionIndex = optionIndexIn(event, optionNumber);
            Trade trade = asSelectionFailure(() -> event.sell(seller, optionIndex, quantity));
            return receiptFor(trade, event, eventNumber);
        });
    }

    @Override
    public List<TradeDto> submitOrder(int eventNumber, String userName, int optionNumber, OrderSide side,
                                      long quantity, double price) {
        if (side == null) {
            throw new InvalidSelectionException("An order must say whether it buys or sells.");
        }
        return changing(() -> {
            OrderBookEvent event = orderBookEventAt(eventNumber);
            User trader = userNamed(userName);
            int optionIndex = optionIndexIn(event, optionNumber);
            List<Trade> trades = asSelectionFailure(
                    () -> event.submitOrder(trader, optionIndex, side, quantity, price));
            return trades.stream().map(GuessMarketEngineImpl::asDto).toList();
        });
    }

    private <T> T reading(Supplier<T> question) {
        return holding(lock.readLock(), question);
    }

    private <T> T changing(Supplier<T> command) {
        return holding(lock.writeLock(), command);
    }

    private static <T> T holding(Lock held, Supplier<T> work) {
        held.lock();
        try {
            return work.get();
        } finally {
            held.unlock();
        }
    }

    /**
     * Turns a refusal from the model into one the caller was told to expect. The model throws plain
     * state and argument failures because it knows nothing of who is calling; this interface promises
     * a single family of failures, each already carrying a message fit to show.
     */
    private static void asSelectionFailure(Runnable action) {
        asSelectionFailure(() -> {
            action.run();
            return null;
        });
    }

    private static <T> T asSelectionFailure(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new InvalidSelectionException(e.getMessage());
        }
    }

    private boolean runsAnything(User user) {
        return market.events().stream().anyMatch(event -> event.marketMaker() == user);
    }

    private UserDetailDto detailOf(User user) {
        List<String> runs = new ArrayList<>();
        List<ParticipationDto> participations = new ArrayList<>();
        List<Event> events = market.events();
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (event.marketMaker() == user) {
                runs.add(event.name());
            }
            if (event.participants().contains(user)) {
                participations.add(participationOf(event, i + 1, user));
            }
        }
        return new UserDetailDto(user.name(), user.account().balance(), user.isBlocked(),
                List.copyOf(runs), List.copyOf(participations));
    }

    private ParticipationDto participationOf(Event event, int eventNumber, User user) {
        Holding holding = event.holdingOf(user);
        List<Trade> theirs = event.history().stream()
                .filter(trade -> trade.userName().equals(user.name()))
                .toList();
        return new ParticipationDto(infoOf(event, eventNumber), holdingsOf(event, user),
                holding.commissionPaid(), holding.netResult(), newestFirst(theirs));
    }

    private List<OptionHoldingDto> holdingsOf(Event event, User user) {
        Holding holding = event.holdingOf(user);
        List<OptionHoldingDto> options = new ArrayList<>();
        for (int i = 0; i < event.options().size(); i++) {
            options.add(new OptionHoldingDto(i + 1, event.options().get(i).name(),
                    holding.shares(i), holding.paidFor(i), worthOf(event, i, holding.shares(i))));
        }
        return List.copyOf(options);
    }

    private PurchaseResultDto receiptFor(Trade trade, LmsrEvent event, int eventNumber) {
        return new PurchaseResultDto(trade.optionName(), trade.quantity(), trade.sharesCost(),
                trade.commission(), trade.totalPaid(),
                event.commission().type() == CommissionType.ON_CLOSE, stateOf(event, eventNumber));
    }

    private OptionMarketDto marketOf(OrderBookEvent event, int optionIndex) {
        OrderBook book = event.bookFor(optionIndex);
        return new OptionMarketDto(optionIndex + 1, event.options().get(optionIndex).name(),
                asDtos(book.bids()), asDtos(book.asks()),
                orNull(book.lastTradedPrice()), orNull(book.bestBid()), orNull(book.bestAsk()),
                orNull(book.midPrice()), orNull(book.spread()),
                event.options().get(optionIndex).sharesBought());
    }

    /**
     * What a holding is worth at the market's present reckoning: the formula's value for an LMSR
     * event, and the last price two people actually agreed on for an order book. An option nobody
     * has traded has no price at all, and the answer is then nothing rather than nought.
     */
    private static Double worthOf(Event event, int optionIndex, long shares) {
        if (event instanceof LmsrEvent lmsr) {
            return shares * lmsr.valueOf(optionIndex);
        }
        if (event instanceof OrderBookEvent book) {
            OptionalDouble last = book.bookFor(optionIndex).lastTradedPrice();
            return last.isPresent() ? shares * last.getAsDouble() : null;
        }
        return null;
    }

    private static List<OrderDto> asDtos(List<Order> orders) {
        return orders.stream()
                .map(order -> new OrderDto(order.user().name(), order.side().displayName(),
                        order.remaining(), order.price()))
                .toList();
    }

    /** A price the book cannot supply is absent, not nought, and reaches the caller as null. */
    private static Double orNull(OptionalDouble value) {
        return value.isPresent() ? value.getAsDouble() : null;
    }

    private static TradeDto asDto(Trade trade) {
        return new TradeDto(trade.optionName(), trade.quantity(), trade.sharesCost(),
                trade.commission(), trade.totalPaid());
    }

    private User userNamed(String userName) {
        if (userName == null || userName.isBlank()) {
            throw new InvalidSelectionException("No user was named.");
        }
        return market.user(userName).orElseThrow(() -> new InvalidSelectionException(
                "There is no user called \"" + userName.trim() + "\". A user comes into being by logging in."));
    }

    private Event eventAt(int eventNumber) {
        List<Event> events = market.events();
        if (events.isEmpty()) {
            throw new InvalidSelectionException("There are no events yet. Upload an events file first.");
        }
        if (eventNumber < 1 || eventNumber > events.size()) {
            throw new InvalidSelectionException("There is no event number " + eventNumber + "."
                    + " Please choose a number between 1 and " + events.size() + ".");
        }
        return events.get(eventNumber - 1);
    }

    private LmsrEvent lmsrEventAt(int eventNumber) {
        Event event = eventAt(eventNumber);
        if (!(event instanceof LmsrEvent lmsr)) {
            throw new InvalidSelectionException("\"" + event.name() + "\" is traded through an order book,"
                    + " where shares are bought from other people rather than from the event.");
        }
        return lmsr;
    }

    private OrderBookEvent orderBookEventAt(int eventNumber) {
        Event event = eventAt(eventNumber);
        if (!(event instanceof OrderBookEvent book)) {
            throw new InvalidSelectionException("\"" + event.name() + "\" is priced by a formula rather"
                    + " than by an order book, so it has no books to show.");
        }
        return book;
    }

    private static int optionIndexIn(Event event, int optionNumber) {
        int optionCount = event.options().size();
        if (optionNumber < 1 || optionNumber > optionCount) {
            throw new InvalidSelectionException("The event \"" + event.name() + "\" has no option number "
                    + optionNumber + ". Please choose a number between 1 and " + optionCount + ".");
        }
        return optionNumber - 1;
    }

    private static EventInfoDto infoOf(Event event, int eventNumber) {
        Commission commission = event.commission();
        return new EventInfoDto(eventNumber, event.name(), event.description(),
                commission.percent(), commission.type().fileValue(), commission.type().displayName(),
                event.options().stream().map(EventOption::name).toList(),
                event.status().displayName(), event.methodDescription(), event.methodKind(),
                event.marketMaker() == null ? null : event.marketMaker().name(),
                event.account().balance(),
                event.winningOption() == null ? null : event.winningOption().name());
    }

    private static MarketStateDto stateOf(LmsrEvent event, int eventNumber) {
        List<OptionStateDto> options = new ArrayList<>();
        for (int i = 0; i < event.options().size(); i++) {
            EventOption option = event.options().get(i);
            options.add(new OptionStateDto(i + 1, option.name(), event.valueOf(i), option.sharesBought()));
        }
        EventOption winner = event.winningOption();
        return new MarketStateDto(infoOf(event, eventNumber), List.copyOf(options),
                event.account().balance(), event.commissionCollected(),
                event.marketMaker().account().balance(), newestFirst(event.history()),
                winner != null,
                winner == null ? null : winner.name(),
                winner == null ? 0L : winner.sharesBought(),
                event.totalPaidOut(), event.payoutPerWinningShare());
    }

    private static List<TradeDto> newestFirst(List<Trade> history) {
        List<TradeDto> newestFirst = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            newestFirst.add(asDto(history.get(i)));
        }
        return List.copyOf(newestFirst);
    }
}
