package gm.engine.impl;

import gm.engine.api.GuessMarketEngine;
import gm.engine.api.InvalidSelectionException;
import gm.engine.api.NoFileLoadedException;
import gm.engine.api.dto.BalanceHistoryDto;
import gm.engine.api.dto.BalancePointDto;
import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.LoadResultDto;
import gm.engine.api.dto.MarketStateDto;
import gm.engine.api.dto.OptionHoldingDto;
import gm.engine.api.dto.OptionMarketDto;
import gm.engine.api.dto.OptionStateDto;
import gm.engine.api.dto.OrderBookStateDto;
import gm.engine.api.dto.OrderDto;
import gm.engine.api.dto.ParticipantDto;
import gm.engine.api.dto.ParticipationDto;
import gm.engine.api.dto.PriceHistoryDto;
import gm.engine.api.dto.PricePointDto;
import gm.engine.api.dto.PurchaseResultDto;
import gm.engine.api.dto.UserDetailDto;
import gm.engine.api.dto.UserDto;
import gm.engine.api.dto.TradeDto;
import gm.engine.model.Commission;
import gm.engine.model.Event;
import gm.engine.model.Holding;
import gm.engine.model.LmsrEvent;
import gm.engine.model.OrderBookEvent;
import gm.engine.model.orderbook.Order;
import gm.engine.model.orderbook.OrderBook;
import gm.engine.model.orderbook.OrderSide;
import gm.engine.model.User;
import gm.engine.model.EventOption;
import gm.engine.model.SystemState;
import gm.engine.model.CommissionType;
import gm.engine.model.Trade;
import gm.engine.persistence.StateSerializer;
import gm.engine.xml.EventsFileLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The working engine. It holds the loaded system, checks every selection that comes in, and hands
 * back plain data objects that a user interface can display without knowing anything about the model.
 * <p>
 * Loading is deliberately arranged so that a faulty file cannot disturb what is already loaded: the
 * new state is built completely first, and only a state that was built without complaint replaces the
 * old one.
 */
public final class GuessMarketEngineImpl implements GuessMarketEngine {

    private final EventsFileLoader fileLoader = new EventsFileLoader();
    private final StateSerializer serializer = new StateSerializer();

    private SystemState state;

    @Override
    public LoadResultDto loadEventsFile(String path) {
        SystemState loaded = fileLoader.load(path);
        state = loaded;
        return new LoadResultDto(path == null ? "" : path.trim(), loaded.eventCount(),
                loaded.costOfOpeningEverything());
    }

    @Override
    public boolean isLoaded() {
        return state != null;
    }

    @Override
    public List<EventInfoDto> listEvents() {
        return eventsMatching(event -> true);
    }

    @Override
    public List<EventInfoDto> listOpenEvents() {
        return eventsMatching(Event::isOpen);
    }

    @Override
    public MarketStateDto marketState(int eventNumber) {
        return stateOf(eventAt(eventNumber), eventNumber);
    }

    @Override
    public PriceHistoryDto priceHistory(int eventNumber) {
        Event event = eventAt(eventNumber);
        List<PricePointDto> points = new ArrayList<>();
        for (Event.PriceSample sample : event.priceHistory()) {
            points.add(new PricePointDto(sample.step(), sample.pricePerOption()));
        }
        List<String> optionNames = new ArrayList<>();
        event.options().forEach(option -> optionNames.add(option.name()));
        return new PriceHistoryDto(event.name(), optionNames, points);
    }

    @Override
    public BalanceHistoryDto balanceHistory(int userNumber) {
        User user = userAt(userNumber);
        List<BalancePointDto> points = new ArrayList<>();
        for (User.BalanceSample sample : user.balanceHistory()) {
            points.add(new BalancePointDto(sample.step(), sample.balance()));
        }
        return new BalanceHistoryDto(user.name(), points);
    }

    @Override
    public List<UserDto> listUsers() {
        List<User> users = currentState().users();
        List<UserDto> summaries = new ArrayList<>();
        for (int i = 0; i < users.size(); i++) {
            summaries.add(summaryOf(users.get(i), i + 1));
        }
        return List.copyOf(summaries);
    }

    @Override
    public UserDetailDto userDetail(int userNumber) {
        User user = userAt(userNumber);
        List<String> runs = new ArrayList<>();
        List<ParticipationDto> participations = new ArrayList<>();
        List<Event> events = currentState().events();
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (event.marketMaker() == user) {
                runs.add(event.name());
            }
            if (event.participants().contains(user)) {
                participations.add(participationOf(event, i + 1, user));
            }
        }
        return new UserDetailDto(userNumber, user.name(), user.account().balance(), user.isBlocked(),
                List.copyOf(runs), List.copyOf(participations));
    }

    @Override
    public EventInfoDto openEvent(int eventNumber, int userNumber) {
        Event event = eventAt(eventNumber);
        User actor = userAt(userNumber);
        asSelectionFailure(() -> event.open(actor));
        return infoOf(event, eventNumber);
    }

    @Override
    public PurchaseResultDto buyShares(int eventNumber, int userNumber, int optionNumber, long quantity) {
        LmsrEvent event = lmsrEventAt(eventNumber);
        User buyer = userAt(userNumber);
        int optionIndex = optionIndexIn(event, optionNumber);
        Trade trade = asSelectionFailure(() -> event.buy(buyer, optionIndex, quantity));
        return receiptFor(trade, event, eventNumber);
    }

    @Override
    public PurchaseResultDto sellShares(int eventNumber, int userNumber, int optionNumber, long quantity) {
        LmsrEvent event = lmsrEventAt(eventNumber);
        User seller = userAt(userNumber);
        int optionIndex = optionIndexIn(event, optionNumber);
        Trade trade = asSelectionFailure(() -> event.sell(seller, optionIndex, quantity));
        return receiptFor(trade, event, eventNumber);
    }

    @Override
    public OrderBookStateDto orderBookState(int eventNumber) {
        OrderBookEvent event = orderBookEventAt(eventNumber);
        List<OptionMarketDto> markets = new ArrayList<>();
        for (int i = 0; i < event.options().size(); i++) {
            markets.add(marketOf(event, i));
        }
        List<ParticipantDto> participants = new ArrayList<>();
        for (User user : event.participants()) {
            participants.add(participantOf(event, user));
        }
        return new OrderBookStateDto(infoOf(event, eventNumber), List.copyOf(markets),
                event.account().balance(), event.commissionCollected(), List.copyOf(participants),
                event.baseValue(), event.allowsMint(), event.highestAllowedPrice());
    }

    @Override
    public List<TradeDto> submitOrder(int eventNumber, int userNumber, int optionNumber, OrderSide side,
                                      long quantity, double price) {
        OrderBookEvent event = orderBookEventAt(eventNumber);
        User trader = userAt(userNumber);
        int optionIndex = optionIndexIn(event, optionNumber);
        List<Trade> trades = asSelectionFailure(
                () -> event.submitOrder(trader, optionIndex, side, quantity, price));
        return trades.stream().map(GuessMarketEngineImpl::asDto).toList();
    }

    @Override
    public MarketStateDto closeEvent(int eventNumber, int userNumber, int winningOptionNumber) {
        Event event = eventAt(eventNumber);
        User actor = userAt(userNumber);
        int optionIndex = optionIndexIn(event, winningOptionNumber);
        asSelectionFailure(() -> event.close(actor, optionIndex));
        return stateOf(event, eventNumber);
    }

    @Override
    public String saveState(String pathWithoutExtension) {
        return serializer.save(currentState(), pathWithoutExtension);
    }

    @Override
    public void loadState(String pathWithoutExtension) {
        state = serializer.load(pathWithoutExtension);
    }

    private SystemState currentState() {
        if (state == null) {
            throw new NoFileLoadedException();
        }
        return state;
    }

    /**
     * Turns a refusal from the model into one the caller was told to expect. The model throws plain
     * state and argument failures because it knows nothing of who is calling; this interface promises
     * a single family of failures, each already carrying a message fit to show.
     */
    private void asSelectionFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new InvalidSelectionException(e.getMessage());
        }
    }

    private <T> T asSelectionFailure(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new InvalidSelectionException(e.getMessage());
        }
    }

    private UserDto summaryOf(User user, int userNumber) {
        return new UserDto(userNumber, user.name(), user.account().balance(), user.isBlocked());
    }

    private ParticipationDto participationOf(Event event, int eventNumber, User user) {
        Holding holding = event.holdingOf(user);
        List<OptionHoldingDto> options = new ArrayList<>();
        for (int i = 0; i < event.options().size(); i++) {
            options.add(new OptionHoldingDto(i + 1, event.options().get(i).name(),
                    holding.shares(i), holding.paidFor(i)));
        }
        List<Trade> theirs = event.history().stream()
                .filter(trade -> trade.userName().equals(user.name()))
                .toList();
        return new ParticipationDto(infoOf(event, eventNumber), List.copyOf(options),
                holding.commissionPaid(), holding.netResult(), newestFirst(theirs));
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

    private ParticipantDto participantOf(Event event, User user) {
        Holding holding = event.holdingOf(user);
        List<OptionHoldingDto> options = new ArrayList<>();
        for (int i = 0; i < event.options().size(); i++) {
            options.add(new OptionHoldingDto(i + 1, event.options().get(i).name(),
                    holding.shares(i), holding.paidFor(i)));
        }
        return new ParticipantDto(user.name(), List.copyOf(options), user.isBlocked());
    }

    private static List<OrderDto> asDtos(List<Order> orders) {
        return orders.stream()
                .map(order -> new OrderDto(order.user().name(), order.side().displayName(),
                        order.remaining(), order.price()))
                .toList();
    }

    /** A price the book cannot supply is absent, not nought, and reaches the caller as null. */
    private static Double orNull(java.util.OptionalDouble value) {
        return value.isPresent() ? value.getAsDouble() : null;
    }

    private static TradeDto asDto(Trade trade) {
        return new TradeDto(trade.optionName(), trade.quantity(), trade.sharesCost(),
                trade.commission(), trade.totalPaid());
    }

    private OrderBookEvent orderBookEventAt(int eventNumber) {
        Event event = eventAt(eventNumber);
        if (!(event instanceof OrderBookEvent book)) {
            throw new InvalidSelectionException("\"" + event.name() + "\" is priced by a formula rather"
                    + " than by an order book, so it has no books to show.");
        }
        return book;
    }

    private User userAt(int userNumber) {
        List<User> users = currentState().users();
        if (userNumber < 1 || userNumber > users.size()) {
            throw new InvalidSelectionException("There is no user number " + userNumber + "."
                    + " Please choose a number between 1 and " + users.size() + ".");
        }
        return users.get(userNumber - 1);
    }

    private LmsrEvent lmsrEventAt(int eventNumber) {
        Event event = eventAt(eventNumber);
        if (!(event instanceof LmsrEvent lmsr)) {
            throw new InvalidSelectionException("\"" + event.name() + "\" is traded through an order book,"
                    + " where shares are bought from other people rather than from the event.");
        }
        return lmsr;
    }

    private int optionIndexIn(Event event, int optionNumber) {
        int optionCount = event.options().size();
        if (optionNumber < 1 || optionNumber > optionCount) {
            throw new InvalidSelectionException("The event \"" + event.name() + "\" has no option number "
                    + optionNumber + ". Please choose a number between 1 and " + optionCount + ".");
        }
        return optionNumber - 1;
    }

    private Event eventAt(int eventNumber) {
        List<Event> events = currentState().events();
        if (eventNumber < 1 || eventNumber > events.size()) {
            throw new InvalidSelectionException("There is no event number " + eventNumber + "."
                    + " Please choose a number between 1 and " + events.size() + ".");
        }
        return events.get(eventNumber - 1);
    }

    private List<EventInfoDto> eventsMatching(Predicate<Event> filter) {
        List<Event> events = currentState().events();
        List<EventInfoDto> matching = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            if (filter.test(events.get(i))) {
                matching.add(infoOf(events.get(i), i + 1));
            }
        }
        return List.copyOf(matching);
    }

    private EventInfoDto infoOf(Event event, int eventNumber) {
        Commission commission = event.commission();
        return new EventInfoDto(eventNumber, event.id(), event.name(), event.description(),
                commission.percent(), commission.type().fileValue(), commission.type().displayName(),
                event.options().stream().map(EventOption::name).toList(),
                event.status().displayName(), event.methodDescription(), event.methodKind(),
                event.marketMaker() == null ? null : event.marketMaker().name());
    }

    private MarketStateDto stateOf(Event event, int eventNumber) {
        if (!(event instanceof LmsrEvent lmsr)) {
            throw new InvalidSelectionException("\"" + event.name() + "\" is traded through an order book,"
                    + " which is described by its own view rather than by a single value per option.");
        }
        List<OptionStateDto> options = new ArrayList<>();
        for (int i = 0; i < event.options().size(); i++) {
            EventOption option = event.options().get(i);
            options.add(new OptionStateDto(i + 1, option.name(), lmsr.valueOf(i), option.sharesBought()));
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

    private List<TradeDto> newestFirst(List<Trade> history) {
        List<TradeDto> newestFirst = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            Trade trade = history.get(i);
            newestFirst.add(new TradeDto(trade.optionName(), trade.quantity(), trade.sharesCost(),
                    trade.commission(), trade.totalPaid()));
        }
        return List.copyOf(newestFirst);
    }
}
