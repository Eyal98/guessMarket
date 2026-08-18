package gm.ui.console;

import gm.engine.api.GuessMarketEngine;
import gm.engine.api.GuessMarketException;
import gm.engine.impl.GuessMarketEngineImpl;

import java.util.EnumMap;
import java.util.Map;
import java.util.Scanner;

/**
 * The console front end of Guess Market, and the program's starting point.
 * <p>
 * It shows the menu, hands each choice to the command that carries it out, and shows whatever comes
 * back. Nothing here knows how an event is priced or how a file is read; that all belongs to the
 * engine, which is asked and never told how to do its job.
 * <p>
 * Every failure ends up here. Anything the engine refuses is shown as the message it came with, and
 * even a fault nobody anticipated only ends the command in hand, never the program.
 */
public final class ConsoleApp {

    private final GuessMarketEngine engine;
    private final ConsoleReader reader;
    private final ConsolePrinter printer;
    private final Map<MenuOption, MenuCommand> commands;

    public static void main(String[] args) {
        new ConsoleApp(new GuessMarketEngineImpl(), new Scanner(System.in)).run();
    }

    private ConsoleApp(GuessMarketEngine engine, Scanner scanner) {
        this.engine = engine;
        this.printer = new ConsolePrinter();
        this.reader = new ConsoleReader(scanner, printer);
        this.commands = createCommands();
    }

    private void run() {
        printer.printWelcome();
        try {
            showMenuUntilAskedToStop();
        } catch (InputEndedException e) {
            printer.printMessage("");
            printer.printMessage("There is no more input to read, so Guess Market is closing.");
        }
        printer.printGoodbye();
    }

    private void showMenuUntilAskedToStop() {
        while (true) {
            printer.printMenu();
            MenuOption chosen = reader.readMenuOption();
            if (chosen == MenuOption.EXIT) {
                return;
            }
            carryOut(chosen);
        }
    }

    private void carryOut(MenuOption chosen) {
        MenuCommand command = commands.get(chosen);
        if (command.needsLoadedFile() && !engine.isLoaded()) {
            printer.printError("No events file is loaded yet, so \"" + chosen.description()
                    + "\" has nothing to work with. Please use command "
                    + MenuOption.LOAD_EVENTS_FILE.number() + " to load an XML file first.");
            return;
        }
        try {
            command.execute();
        } catch (InputEndedException e) {
            throw e;
        } catch (GuessMarketException e) {
            printer.printError(e.getMessage());
        } catch (RuntimeException e) {
            printer.printError("Something unexpected went wrong while running that command: " + e
                    + ". The command was stopped, and everything else is as it was.");
        }
    }

    private Map<MenuOption, MenuCommand> createCommands() {
        Map<MenuOption, MenuCommand> byOption = new EnumMap<>(MenuOption.class);
        byOption.put(MenuOption.LOAD_EVENTS_FILE, new LoadEventsFileCommand(engine, reader, printer));
        byOption.put(MenuOption.SHOW_EVENTS, new ShowEventsCommand(engine, reader, printer));
        byOption.put(MenuOption.SHOW_MARKET_STATE, new ShowMarketStateCommand(engine, reader, printer));
        byOption.put(MenuOption.PARTICIPATE, new ParticipateCommand(engine, reader, printer));
        byOption.put(MenuOption.CLOSE_EVENT, new CloseEventCommand(engine, reader, printer));
        byOption.put(MenuOption.SAVE_SYSTEM, new SaveSystemCommand(engine, reader, printer));
        byOption.put(MenuOption.LOAD_SYSTEM, new LoadSystemCommand(engine, reader, printer));
        return byOption;
    }
}
