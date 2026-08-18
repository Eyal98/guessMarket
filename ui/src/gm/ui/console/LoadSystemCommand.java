package gm.ui.console;

import gm.engine.api.GuessMarketEngine;

import java.util.Optional;

/**
 * Menu command 7: bring back a system saved earlier, in place of whatever is loaded now.
 * <p>
 * Like loading an events file, this is available at any time, including before anything at all has
 * been loaded.
 */
final class LoadSystemCommand extends MenuCommand {

    LoadSystemCommand(GuessMarketEngine engine, ConsoleReader reader, ConsolePrinter printer) {
        super(engine, reader, printer);
    }

    @Override
    boolean needsLoadedFile() {
        return false;
    }

    @Override
    void execute() {
        Optional<String> path = reader.readPath(
                "Type the full path and file name of the saved system, without an extension");
        if (path.isEmpty()) {
            printer.printCancelled();
            return;
        }
        engine.loadState(path.get());
        printer.printMessage("");
        printer.printMessage("The saved system was restored, with " + countOfEvents(engine.listEvents().size())
                + " and all of their trading history.");
    }
}
