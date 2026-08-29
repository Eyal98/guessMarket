package gm.engine.model;

import java.io.Serializable;
import java.util.List;

/**
 * Everything the system currently holds: the events that were loaded and the people who trade on
 * them.
 * <p>
 * Nothing is funded here. In this version an event is paid for when its market maker opens it, not
 * when the file is read, so a freshly loaded state is a set of dormant events and users holding
 * exactly the cash their file gave them.
 */
public final class SystemState implements Serializable {

    private static final long serialVersionUID = 2L;

    /** Always immutable lists, which are serializable; the declared types simply cannot say so. */
    @SuppressWarnings("serial")
    private final List<Event> events;
    @SuppressWarnings("serial")
    private final List<User> users;

    public SystemState(List<Event> events, List<User> users) {
        this.events = List.copyOf(events);
        this.users = List.copyOf(users);
    }

    /** The events, in the order they appeared in the file. */
    public List<Event> events() {
        return events;
    }

    /** The users, in the order they appeared in the file. */
    public List<User> users() {
        return users;
    }

    public int eventCount() {
        return events.size();
    }

    public int userCount() {
        return users.size();
    }

    /**
     * What opening every event would cost its market maker, added up. Nothing has been paid yet; this
     * is what the whole market is waiting on.
     */
    public double costOfOpeningEverything() {
        return events.stream().mapToDouble(Event::openingCost).sum();
    }

    public List<Event> openEvents() {
        return events.stream().filter(Event::isOpen).toList();
    }
}
