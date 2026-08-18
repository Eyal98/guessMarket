package gm.ui.console;

import gm.engine.api.FileLoadException;
import gm.engine.api.GuessMarketEngine;

import java.util.Optional;

/**
 * Menu command 1: read an events file.
 * <p>
 * A faulty file is reported here rather than by the main loop, because only this command can add the
 * one thing the user most wants to know afterwards, which is whether anything is still loaded.
 */
final class LoadEventsFileCommand extends MenuCommand {

    LoadEventsFileCommand(GuessMarketEngine engine, ConsoleReader reader, ConsolePrinter printer) {
        super(engine, reader, printer);
    }

    @Override
    boolean needsLoadedFile() {
        return false;
    }

    @Override
    void execute() {
        Optional<String> path = reader.readPath("Type the full path of the XML file to load");
        if (path.isEmpty()) {
            printer.printCancelled();
            return;
        }
        try {
            printer.printLoadResult(engine.loadEventsFile(path.get()));
        } catch (FileLoadException e) {
            printer.printError(e.getMessage());
            printer.printMessage(whatIsStillLoaded());
        }
    }

    private String whatIsStillLoaded() {
        return engine.isLoaded()
                ? "  Nothing has changed: the system is still using the " + countOfEvents(engine.listEvents().size())
                        + " loaded earlier."
                : "  No events are loaded, so please correct the file and try again.";
    }
}
