package gm.engine.xml;

import gm.engine.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Turns one {@code GM-user} element into a {@link User}, and remembers which events they claim to run.
 * <p>
 * Like the event reader, everything is examined even after something has gone wrong, so one attempt
 * at loading reports every fault in the file rather than revealing them one reload at a time. The
 * events a user runs are only ids at this stage; matching them to real events is the loader's job,
 * because only the loader can see whether such an event exists.
 */
final class UserNodeReader {

    private static final String NAME_ATTRIBUTE = "name";
    private static final String INITIAL_CASH_ELEMENT = "initial-cash";
    private static final String MARKET_MAKER_ELEMENT = "GM-market-maker";
    private static final String EVENT_ELEMENT = "event";
    private static final String EVENT_ID_ATTRIBUTE = "id";

    private final XmlNode node;
    private final List<String> problems;
    private final String name;
    private final String label;

    private final List<Integer> runsEventIds = new ArrayList<>();

    UserNodeReader(XmlNode node, int position, List<String> problems) {
        this.node = node;
        this.problems = problems;
        this.name = node.attribute(NAME_ATTRIBUTE).orElse(null);
        this.label = name == null || name.isBlank()
                ? "User #" + position
                : "User #" + position + " (\"" + name + "\")";
    }

    String label() {
        return label;
    }

    /** The event ids this user claims to run, available after {@link #read()}. */
    List<Integer> runsEventIds() {
        return List.copyOf(runsEventIds);
    }

    /** The user, or nothing at all if any part of them was faulty. */
    Optional<User> read() {
        Optional<String> userName = requiredName();
        OptionalInt cash = requiredInitialCash();
        readMarketMakerIds();

        if (userName.isEmpty() || cash.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new User(userName.get(), cash.getAsInt()));
    }

    private Optional<String> requiredName() {
        if (name == null) {
            problems.add(label + ": it has no " + NAME_ATTRIBUTE + " attribute.");
            return Optional.empty();
        }
        if (name.isBlank()) {
            problems.add(label + ": its " + NAME_ATTRIBUTE + " attribute is empty.");
            return Optional.empty();
        }
        return Optional.of(name);
    }

    private OptionalInt requiredInitialCash() {
        Optional<String> text = node.childText(INITIAL_CASH_ELEMENT);
        if (text.isEmpty() || text.get().isBlank()) {
            problems.add(label + ": it has no <" + INITIAL_CASH_ELEMENT + "> element.");
            return OptionalInt.empty();
        }
        int cash;
        try {
            cash = Integer.parseInt(text.get());
        } catch (NumberFormatException e) {
            problems.add(label + ": its starting cash is \"" + text.get()
                    + "\", which is not a whole number.");
            return OptionalInt.empty();
        }
        if (cash <= 0) {
            problems.add(label + ": its starting cash is " + cash
                    + ", but every user must start with an amount greater than 0.");
            return OptionalInt.empty();
        }
        return OptionalInt.of(cash);
    }

    private void readMarketMakerIds() {
        Optional<XmlNode> marketMakerNode = node.child(MARKET_MAKER_ELEMENT);
        if (marketMakerNode.isEmpty()) {
            return;
        }
        for (XmlNode eventNode : marketMakerNode.get().children(EVENT_ELEMENT)) {
            Optional<String> raw = eventNode.attribute(EVENT_ID_ATTRIBUTE);
            if (raw.isEmpty() || raw.get().isBlank()) {
                problems.add(label + ": one of the events it runs has no " + EVENT_ID_ATTRIBUTE
                        + " attribute.");
                continue;
            }
            try {
                runsEventIds.add(Integer.valueOf(raw.get()));
            } catch (NumberFormatException e) {
                problems.add(label + ": it claims to run event \"" + raw.get()
                        + "\", which is not a whole number.");
            }
        }
    }
}
