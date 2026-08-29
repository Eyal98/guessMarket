package gm.engine.impl;

import gm.engine.api.EventClosedException;
import gm.engine.api.GuessMarketEngine;
import gm.engine.api.InvalidSelectionException;
import gm.engine.api.NoFileLoadedException;
import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.LoadResultDto;
import gm.engine.api.dto.MarketStateDto;
import gm.engine.api.dto.OptionStateDto;
import gm.engine.api.dto.PurchaseResultDto;
import gm.engine.api.dto.TradeDto;
import gm.engine.model.Commission;
import gm.engine.model.CommissionType;
import gm.engine.model.Event;
import gm.engine.model.EventOption;
import gm.engine.model.SystemState;
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
        SystemState loaded = new SystemState(fileLoader.load(path));
        state = loaded;
        return new LoadResultDto(path == null ? "" : path.trim(), loaded.eventCount(), loaded.totalSubsidy());
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
    public PurchaseResultDto buyShares(int eventNumber, int optionNumber, long quantity) {
        Event event = eventAt(eventNumber);
        int optionIndex = optionIndexIn(event, optionNumber);
        requireOpen(event);
        if (quantity < 1) {
            throw new InvalidSelectionException("The number of shares to buy must be at least 1, but it is "
                    + quantity + ".");
        }
        Trade trade = event.buy(optionIndex, quantity);
        return new PurchaseResultDto(trade.optionName(), trade.quantity(), trade.sharesCost(),
                trade.commission(), trade.totalPaid(),
                event.commission().type() == CommissionType.ON_CLOSE, stateOf(event, eventNumber));
    }

    @Override
    public MarketStateDto closeEvent(int eventNumber, int winningOptionNumber) {
        Event event = eventAt(eventNumber);
        int optionIndex = optionIndexIn(event, winningOptionNumber);
        requireOpen(event);
        event.close(event.marketMaker(), optionIndex);
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

    private Event eventAt(int eventNumber) {
        List<Event> events = currentState().events();
        if (eventNumber < 1 || eventNumber > events.size()) {
            throw new InvalidSelectionException("There is no event number " + eventNumber + "."
                    + " Please choose a number between 1 and " + events.size() + ".");
        }
        return events.get(eventNumber - 1);
    }

    private int optionIndexIn(Event event, int optionNumber) {
        int optionCount = event.options().size();
        if (optionNumber < 1 || optionNumber > optionCount) {
            throw new InvalidSelectionException("The event \"" + event.name() + "\" has no option number "
                    + optionNumber + ". Please choose a number between 1 and " + optionCount + ".");
        }
        return optionNumber - 1;
    }

    private void requireOpen(Event event) {
        if (!event.isOpen()) {
            throw new EventClosedException(event.name());
        }
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
                event.status().displayName(), event.methodDescription());
    }

    private MarketStateDto stateOf(Event event, int eventNumber) {
        List<OptionStateDto> options = new ArrayList<>();
        for (int i = 0; i < event.options().size(); i++) {
            EventOption option = event.options().get(i);
            options.add(new OptionStateDto(i + 1, option.name(), event.valueOf(i), option.sharesBought()));
        }
        EventOption winner = event.winningOption();
        return new MarketStateDto(infoOf(event, eventNumber), List.copyOf(options),
                event.account().balance(), event.commissionCollected(),
                currentState().marketMakerAccount().balance(), newestFirst(event.history()),
                winner != null,
                winner == null ? null : winner.name(),
                winner == null ? 0L : winner.sharesBought(),
                event.totalPaidOut(), Event.PAYOUT_PER_WINNING_SHARE);
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
