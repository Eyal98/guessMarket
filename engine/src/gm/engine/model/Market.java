package gm.engine.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the server currently holds: every event anybody has uploaded, and everybody who has ever
 * logged in.
 * <p>
 * Both only ever grow. A file adds its events to the ones already here rather than replacing them,
 * and a user who logs out keeps their account, holdings and ledger for when they come back. Nothing
 * is ever taken away, which is also what lets an event's position in the list serve as its number
 * for as long as the server runs.
 * <p>
 * Names are compared the way the exercise asks for all English input, without regard to case, so
 * "Avrum" and "avrum" are the same person and "Rain" and "RAIN" the same event.
 */
public final class Market {

    private final List<Event> events = new ArrayList<>();
    private final Map<String, User> usersByName = new LinkedHashMap<>();

    /** The events, in the order they arrived. */
    public List<Event> events() {
        return Collections.unmodifiableList(events);
    }

    /** Everybody who has ever logged in, in the order they first did. */
    public Collection<User> users() {
        return Collections.unmodifiableCollection(usersByName.values());
    }

    /** The user of that name, if anybody by that name has ever logged in. */
    public Optional<User> user(String name) {
        return Optional.ofNullable(usersByName.get(key(name)));
    }

    /**
     * The user of that name, brought into being with an empty account if this is the first time the
     * name has been seen.
     */
    public User enter(String name) {
        return usersByName.computeIfAbsent(key(name), ignored -> new User(name.trim()));
    }

    /** Whether an event by that name is already here. */
    public boolean hasEventNamed(String name) {
        String wanted = key(name);
        return events.stream().anyMatch(event -> key(event.name()).equals(wanted));
    }

    /** Adds events after the ones already here, keeping their order. */
    public void addEvents(List<? extends Event> arriving) {
        events.addAll(arriving);
    }

    /** How a name is compared: trimmed, and without regard to case. */
    public static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
