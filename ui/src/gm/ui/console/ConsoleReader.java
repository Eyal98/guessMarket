package gm.ui.console;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Scanner;

/**
 * Collects input from the console and refuses to hand anything on until it makes sense.
 * <p>
 * Every prompt keeps asking until it is answered properly, and says what was wrong with the previous
 * answer, so a mistyped number is never able to end a command halfway through. Anywhere a choice is
 * being made, {@value #CANCEL} takes the user back to the menu instead, and a request for a file path
 * can be abandoned by pressing enter on an empty line.
 * <p>
 * A whole line is read every time. That is what lets a file path contain spaces without any quoting,
 * and it avoids the classic mixture of {@code nextInt} and {@code nextLine} that leaves a stray line
 * ending behind.
 */
final class ConsoleReader {

    /** Typed instead of a choice to go back to the menu. */
    static final int CANCEL = 0;

    private final Scanner scanner;
    private final ConsolePrinter printer;

    ConsoleReader(Scanner scanner, ConsolePrinter printer) {
        this.scanner = scanner;
        this.printer = printer;
    }

    /** Which command to run. Keeps asking until a command number is typed. */
    MenuOption readMenuOption() {
        while (true) {
            printer.printPrompt("Choose a command (1-" + MenuOption.highestNumber() + ")");
            String typed = readLine();
            if (typed.isEmpty()) {
                printer.printError("Please type the number of the command you want.");
                continue;
            }
            OptionalInt number = asWholeNumber(typed);
            if (number.isEmpty()) {
                continue;
            }
            Optional<MenuOption> option = MenuOption.byNumber(number.getAsInt());
            if (option.isPresent()) {
                return option.get();
            }
            printer.printError("There is no command number " + number.getAsInt() + "."
                    + " Please choose a number between 1 and " + MenuOption.highestNumber() + ".");
        }
    }

    /**
     * A number between {@code lowest} and {@code highest}.
     *
     * @return the chosen number, or empty if the user chose to go back
     */
    OptionalInt readNumberInRange(String prompt, int lowest, int highest) {
        while (true) {
            printer.printPrompt(prompt + " (" + lowest + "-" + highest + "), or " + CANCEL + " to go back");
            OptionalInt typed = readWholeNumber();
            if (typed.isEmpty()) {
                continue;
            }
            int number = typed.getAsInt();
            if (number == CANCEL) {
                return OptionalInt.empty();
            }
            if (number >= lowest && number <= highest) {
                return OptionalInt.of(number);
            }
            printer.printError(number + " is not one of the choices. Please type a number between "
                    + lowest + " and " + highest + ", or " + CANCEL + " to go back.");
        }
    }

    /**
     * One of a specific set of numbers, used where a list skips some of them.
     *
     * @return the chosen number, or empty if the user chose to go back
     */
    OptionalInt readNumberFrom(String prompt, List<Integer> allowed) {
        while (true) {
            printer.printPrompt(prompt + " (" + asChoiceList(allowed) + "), or " + CANCEL + " to go back");
            OptionalInt typed = readWholeNumber();
            if (typed.isEmpty()) {
                continue;
            }
            int number = typed.getAsInt();
            if (number == CANCEL) {
                return OptionalInt.empty();
            }
            if (allowed.contains(number)) {
                return OptionalInt.of(number);
            }
            printer.printError(number + " is not one of the choices. Please type one of: "
                    + asChoiceList(allowed) + ", or " + CANCEL + " to go back.");
        }
    }

    /**
     * How many shares to buy.
     *
     * @return the amount, or empty if the user chose to go back
     */
    OptionalLong readShareCount(String prompt) {
        while (true) {
            printer.printPrompt(prompt + ", or " + CANCEL + " to go back");
            String typed = readLine();
            if (typed.isEmpty()) {
                printer.printError("Please type how many shares you want to buy.");
                continue;
            }
            long amount;
            try {
                amount = Long.parseLong(typed);
            } catch (NumberFormatException e) {
                printer.printError("\"" + typed + "\" is not a whole number. Please type how many shares"
                        + " you want to buy, for example 100.");
                continue;
            }
            if (amount == CANCEL) {
                return OptionalLong.empty();
            }
            if (amount < 1) {
                printer.printError("You have to buy at least one share, so " + amount + " will not do.");
                continue;
            }
            return OptionalLong.of(amount);
        }
    }

    /**
     * A file path. It may contain spaces and needs no quotation marks, though any that are typed are
     * removed.
     *
     * @return the path, or empty if the user pressed enter without typing one
     */
    Optional<String> readPath(String prompt) {
        printer.printPrompt(prompt + ", or press enter to go back");
        String typed = withoutSurroundingQuotes(readLine());
        return typed.isEmpty() ? Optional.empty() : Optional.of(typed);
    }

    private OptionalInt readWholeNumber() {
        String typed = readLine();
        if (typed.isEmpty()) {
            printer.printError("Please type one of the numbers shown above.");
            return OptionalInt.empty();
        }
        return asWholeNumber(typed);
    }

    private OptionalInt asWholeNumber(String typed) {
        try {
            return OptionalInt.of(Integer.parseInt(typed));
        } catch (NumberFormatException e) {
            printer.printError("\"" + typed + "\" is not a number. Please type one of the numbers shown above.");
            return OptionalInt.empty();
        }
    }

    private String readLine() {
        try {
            return scanner.nextLine().trim();
        } catch (NoSuchElementException e) {
            throw new InputEndedException();
        }
    }

    private static String withoutSurroundingQuotes(String path) {
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            return path.substring(1, path.length() - 1).trim();
        }
        return path;
    }

    private static String asChoiceList(List<Integer> allowed) {
        StringBuilder choices = new StringBuilder();
        for (int i = 0; i < allowed.size(); i++) {
            if (i > 0) {
                choices.append(i == allowed.size() - 1 ? " or " : ", ");
            }
            choices.append(allowed.get(i));
        }
        return choices.toString();
    }
}
