package gm.ui.console;

import gm.engine.api.GuessMarketEngine;
import gm.engine.api.dto.EventInfoDto;

import java.util.List;
import java.util.OptionalInt;

/**
 * One command from the menu.
 * <p>
 * Each command owns a single job from start to finish: what to ask, in what order, and what to show
 * afterwards. Anything the command cannot carry out is reported by the engine as an exception and
 * shown by the main loop, so no command has to spend lines on that.
 */
abstract class MenuCommand {

    protected final GuessMarketEngine engine;
    protected final ConsoleReader reader;
    protected final ConsolePrinter printer;

    protected MenuCommand(GuessMarketEngine engine, ConsoleReader reader, ConsolePrinter printer) {
        this.engine = engine;
        this.reader = reader;
        this.printer = printer;
    }

    abstract void execute();

    /**
     * Whether the command needs an events file to have been loaded first. Loading a file, and
     * restoring a saved system, are the two that do not.
     */
    boolean needsLoadedFile() {
        return true;
    }

    /**
     * Shows the events in full and asks which one is wanted.
     *
     * @return the chosen event's number, or empty if the user went back
     */
    protected OptionalInt chooseEvent(List<EventInfoDto> events, String heading, String prompt) {
        printer.printEvents(heading, events);
        return reader.readNumberFrom(prompt, events.stream().map(EventInfoDto::number).toList());
    }

    /** "1 event" or "4 events", so no message has to read "1 event(s)". */
    protected static String countOfEvents(int count) {
        return count == 1 ? "1 event" : count + " events";
    }
}
