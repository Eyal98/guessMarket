package gm.ui.console;

import gm.engine.api.GuessMarketEngine;
import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.MarketStateDto;
import gm.engine.api.dto.PurchaseResultDto;

import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Menu command 4: take part in an event by buying shares of the option you believe in.
 * <p>
 * Only events that are still open are offered, and the standing of the chosen event is shown before
 * any commitment is asked for, so nobody has to buy blind.
 */
final class ParticipateCommand extends MenuCommand {

    ParticipateCommand(GuessMarketEngine engine, ConsoleReader reader, ConsolePrinter printer) {
        super(engine, reader, printer);
    }

    @Override
    void execute() {
        List<EventInfoDto> openEvents = engine.listOpenEvents();
        if (openEvents.isEmpty()) {
            printer.printError("Every event has been closed already, so there is nothing left to trade on.");
            return;
        }
        OptionalInt chosenEvent = chooseEvent(openEvents, countOfEvents(openEvents.size()) + " are still open:",
                "Choose the event you want to take part in");
        if (chosenEvent.isEmpty()) {
            printer.printCancelled();
            return;
        }
        int eventNumber = chosenEvent.getAsInt();

        MarketStateDto before = engine.marketState(eventNumber);
        printer.printCurrentStanding(before);
        OptionalInt option = reader.readNumberInRange("Choose the option you believe in",
                1, before.options().size());
        if (option.isEmpty()) {
            printer.printCancelled();
            return;
        }
        OptionalLong shares = reader.readShareCount("How many shares of it do you want to buy");
        if (shares.isEmpty()) {
            printer.printCancelled();
            return;
        }

        PurchaseResultDto purchase = engine.buyShares(eventNumber, option.getAsInt(), shares.getAsLong());
        printer.printPurchase(purchase);
        printer.printMarketState(purchase.stateAfter());
    }
}
