package gm.ui.console;

import gm.engine.api.GuessMarketEngine;

import java.util.Optional;

/** Menu command 6: write the whole system, trading history and all, to a file. */
final class SaveSystemCommand extends MenuCommand {

    SaveSystemCommand(GuessMarketEngine engine, ConsoleReader reader, ConsolePrinter printer) {
        super(engine, reader, printer);
    }

    @Override
    void execute() {
        Optional<String> path = reader.readPath(
                "Type the full path and file name to save to, without an extension");
        if (path.isEmpty()) {
            printer.printCancelled();
            return;
        }
        String savedTo = engine.saveState(path.get());
        printer.printMessage("");
        printer.printMessage("The system was saved to \"" + savedTo + "\".");
        printer.printMessage("To bring it back later, use command " + MenuOption.LOAD_SYSTEM.number()
                + " and type the same path without the extension.");
    }
}
