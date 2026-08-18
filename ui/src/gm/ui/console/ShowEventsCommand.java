package gm.ui.console;

import gm.engine.api.GuessMarketEngine;
import gm.engine.api.dto.EventInfoDto;

import java.util.List;

/** Menu command 2: show every loaded event. */
final class ShowEventsCommand extends MenuCommand {

    ShowEventsCommand(GuessMarketEngine engine, ConsoleReader reader, ConsolePrinter printer) {
        super(engine, reader, printer);
    }

    @Override
    void execute() {
        List<EventInfoDto> events = engine.listEvents();
        printer.printEvents(countOfEvents(events.size()) + " are loaded:", events);
    }
}
