package gm.ui.console;

import gm.engine.api.GuessMarketEngine;
import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.MarketStateDto;

import java.util.List;
import java.util.OptionalInt;

/**
 * Menu command 5: decide an event and stop the trading on it.
 * <p>
 * The whole state of the event is shown before the outcome is chosen, since closing it pays out real
 * money and cannot be undone.
 */
final class CloseEventCommand extends MenuCommand {

    CloseEventCommand(GuessMarketEngine engine, ConsoleReader reader, ConsolePrinter printer) {
        super(engine, reader, printer);
    }

    @Override
    void execute() {
        List<EventInfoDto> openEvents = engine.listOpenEvents();
        if (openEvents.isEmpty()) {
            printer.printError("Every event has been closed already, so there is nothing left to close.");
            return;
        }
        OptionalInt chosenEvent = chooseEvent(openEvents, countOfEvents(openEvents.size()) + " are still open:",
                "Choose the event you want to close");
        if (chosenEvent.isEmpty()) {
            printer.printCancelled();
            return;
        }
        int eventNumber = chosenEvent.getAsInt();

        MarketStateDto before = engine.marketState(eventNumber);
        printer.printMarketState(before);
        OptionalInt winner = reader.readNumberInRange("Choose the option the event ended on",
                1, before.options().size());
        if (winner.isEmpty()) {
            printer.printCancelled();
            return;
        }

        MarketStateDto closed = engine.closeEvent(eventNumber, winner.getAsInt());
        printer.printMessage("");
        printer.printMessage("The event has been closed and the holders of the winning option have been paid.");
        printer.printMarketState(closed);
    }
}
