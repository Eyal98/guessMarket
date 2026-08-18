package gm.ui.console;

import java.util.Arrays;
import java.util.Optional;

/**
 * The commands on the main menu, in the order they are shown.
 * <p>
 * The number a user types is the position of the constant in this list, counted from 1, so the menu
 * that is printed and the numbers that are accepted can never drift apart: adding a command here adds
 * it to both.
 */
enum MenuOption {

    LOAD_EVENTS_FILE("Load an events file"),
    SHOW_EVENTS("Show all events"),
    SHOW_MARKET_STATE("Show the market state of an event"),
    PARTICIPATE("Participate in an event"),
    CLOSE_EVENT("Close an event"),
    SAVE_SYSTEM("Save the system to a file"),
    LOAD_SYSTEM("Load a saved system from a file"),
    EXIT("Exit");

    private final String description;

    MenuOption(String description) {
        this.description = description;
    }

    /** The number a user types to choose this command. */
    int number() {
        return ordinal() + 1;
    }

    String description() {
        return description;
    }

    static Optional<MenuOption> byNumber(int number) {
        return Arrays.stream(values())
                .filter(option -> option.number() == number)
                .findFirst();
    }

    static int highestNumber() {
        return values().length;
    }
}
