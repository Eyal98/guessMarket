package gm.engine.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * One possible outcome of an event, together with the number of shares bought of it so far.
 * <p>
 * The share count can only be changed from inside this package, so an event is the single place
 * where trading changes anything.
 */
public final class EventOption implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private long sharesBought;

    EventOption(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String name() {
        return name;
    }

    public long sharesBought() {
        return sharesBought;
    }

    void addShares(long quantity) {
        sharesBought += quantity;
    }

    void removeShares(long quantity) {
        if (quantity > sharesBought) {
            throw new IllegalArgumentException("The market holds only " + sharesBought
                    + " shares of \"" + name + "\", so " + quantity + " cannot be given back.");
        }
        sharesBought -= quantity;
    }
}
