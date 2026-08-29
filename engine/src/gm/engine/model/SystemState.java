package gm.engine.model;

import java.io.Serializable;
import java.util.List;

/**
 * Everything the system currently holds: the loaded events and the market maker account that funds
 * them.
 * <p>
 * Building a state also funds it. That keeps the rule "loading a file resets the market maker account
 * and pays the subsidy of every event" in one place, and makes it impossible to end up with a state
 * whose events were never paid for.
 */
public final class SystemState implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Always an immutable list, which is serializable; the declared type simply cannot say so. */
    @SuppressWarnings("serial")
    private final List<Event> events;
    private final Account marketMakerAccount = new Account();
    private final double totalSubsidy;

    public SystemState(List<Event> events) {
        this.events = List.copyOf(events);
        this.totalSubsidy = this.events.stream().mapToDouble(Event::openingCost).sum();
    }

    /**
     * What opening every event would cost its market maker, added up. Nothing has been paid yet: in
     * this version an event is funded when its market maker opens it, not when the file is read.
     */
    public double costOfOpeningEverything() {
        return totalSubsidy;
    }

    /** The events, in the order they appeared in the file. */
    public List<Event> events() {
        return events;
    }

    public Account marketMakerAccount() {
        return marketMakerAccount;
    }

    /** What funding every event cost the market maker at load time. */
    public double totalSubsidy() {
        return totalSubsidy;
    }

    public int eventCount() {
        return events.size();
    }

    public List<Event> openEvents() {
        return events.stream().filter(Event::isOpen).toList();
    }
}
