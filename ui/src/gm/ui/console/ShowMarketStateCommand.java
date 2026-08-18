package gm.ui.console;

import gm.engine.api.GuessMarketEngine;
import gm.engine.api.dto.EventInfoDto;

import java.util.List;
import java.util.OptionalInt;

/** Menu command 3: show how the trading on one event stands. */
final class ShowMarketStateCommand extends MenuCommand {

    ShowMarketStateCommand(GuessMarketEngine engine, ConsoleReader reader, ConsolePrinter printer) {
        super(engine, reader, printer);
    }

    @Override
    void execute() {
        List<EventInfoDto> events = engine.listEvents();
        OptionalInt chosen = chooseEvent(events, countOfEvents(events.size()) + " are loaded:",
                "Choose the event you want to look at");
        if (chosen.isEmpty()) {
            printer.printCancelled();
            return;
        }
        printer.printMarketState(engine.marketState(chosen.getAsInt()));
    }
}
