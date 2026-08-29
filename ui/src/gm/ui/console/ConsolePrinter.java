package gm.ui.console;

import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.LoadResultDto;
import gm.engine.api.dto.MarketStateDto;
import gm.engine.api.dto.OptionStateDto;
import gm.engine.api.dto.PurchaseResultDto;
import gm.engine.api.dto.TradeDto;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Everything the program shows on screen. It is the only class that prints, which keeps the layout of
 * the whole application in one place and leaves the engine free of any idea of how it is displayed.
 * <p>
 * Numbers are always formatted with {@link Locale#US} on purpose. Left to the machine's own locale, a
 * value of nought point seven three would come out as "0,73" on a great many computers, and money and
 * probabilities in this program are always written with a full stop.
 */
final class ConsolePrinter {

    private static final String THICK_RULE = "=".repeat(70);
    private static final String THIN_RULE = "-".repeat(70);
    /** Wide enough for the longest label in the program, so every block lines up with every other. */
    private static final String LABEL_FORMAT = "  %-23s: %s%n";
    /** Anything smaller than half a penny is shown as zero, so no total ever reads "-0.00". */
    private static final double ROUNDING_NOISE = 0.005;

    void printWelcome() {
        System.out.println();
        System.out.println(THICK_RULE);
        System.out.println(" Guess Market");
        System.out.println(THICK_RULE);
        System.out.println("Trade on events, one guess at a time.");
    }

    void printMenu() {
        System.out.println();
        System.out.println(THIN_RULE);
        for (MenuOption option : MenuOption.values()) {
            System.out.printf("  %d. %s%n", option.number(), option.description());
        }
        System.out.println(THIN_RULE);
    }

    void printPrompt(String prompt) {
        System.out.print(prompt + ": ");
    }

    void printError(String message) {
        System.out.println();
        System.out.println("[!] " + message);
    }

    void printMessage(String message) {
        System.out.println(message);
    }

    void printCancelled() {
        System.out.println();
        System.out.println("Nothing was done. Back to the menu.");
    }

    void printGoodbye() {
        System.out.println();
        System.out.println("Thank you for using Guess Market. Goodbye.");
    }

    void printLoadResult(LoadResultDto result) {
        System.out.println();
        System.out.println("The file was read and every event in it is sound.");
        System.out.printf(LABEL_FORMAT, "File", result.filePath());
        System.out.printf(LABEL_FORMAT, "Events loaded", result.eventsLoaded());
        System.out.printf(LABEL_FORMAT, "Subsidy paid in total", twoDecimals(result.totalSubsidy()));
        System.out.println();
        System.out.println("  The market maker account was reset and has now funded every event above.");
    }

    /** The events in full, which is how they are shown wherever one has to be chosen. */
    void printEvents(String heading, List<EventInfoDto> events) {
        System.out.println();
        System.out.println(heading);
        for (EventInfoDto event : events) {
            System.out.println();
            System.out.println("Event " + event.number());
            System.out.printf(LABEL_FORMAT, "Id in the file", event.id());
            System.out.printf(LABEL_FORMAT, "Name", event.name());
            System.out.printf(LABEL_FORMAT, "Description", event.description());
            System.out.printf(LABEL_FORMAT, "Commission", commissionText(event));
            System.out.printf(LABEL_FORMAT, "Options", numberedOptions(event.optionNames()));
            System.out.printf(LABEL_FORMAT, "Trading method", event.tradingMethod());
            System.out.printf(LABEL_FORMAT, "Status", event.status());
        }
    }

    void printMarketState(MarketStateDto state) {
        System.out.println();
        System.out.println(THICK_RULE);
        System.out.println(" Market state - event " + state.event().number() + ": " + state.event().name());
        System.out.println(THICK_RULE);
        printCurrentStanding(state);
        printAccounts(state);
        printHistory(state.history());
        printOutcome(state);
    }

    /** Just where the options stand, shown on its own before a purchase. */
    void printCurrentStanding(MarketStateDto state) {
        System.out.println();
        System.out.println("Current standing:");
        int nameWidth = widthFor("Option", state.options().stream().map(OptionStateDto::name).toList());
        String rowFormat = "  %-3s %-" + nameWidth + "s %8s %15s%n";
        System.out.printf(rowFormat, "#", "Option", "Value", "Shares held");
        System.out.printf(rowFormat, "-".repeat(3), "-".repeat(nameWidth), "-".repeat(8), "-".repeat(15));
        for (OptionStateDto option : state.options()) {
            System.out.printf(rowFormat, option.number(), option.name(),
                    twoDecimals(option.value()), option.sharesBought());
        }
        printFavourite(state);
        System.out.printf(LABEL_FORMAT, "Each share is worth",
                twoDecimals(state.payoutPerWinningShare()) + " if its option wins, "
                        + twoDecimals(0) + " if it loses");
    }

    /**
     * Names the option the market currently believes in. An untouched event, and any event whose
     * options have drawn level, has no favourite, and saying so is more honest than picking whichever
     * of two equal values happened to come first.
     */
    private void printFavourite(MarketStateDto state) {
        OptionStateDto leader = state.options().stream()
                .max(Comparator.comparingDouble(OptionStateDto::value))
                .orElse(null);
        if (leader == null) {
            return;
        }
        long sharingTheLead = state.options().stream()
                .filter(option -> Math.abs(option.value() - leader.value()) <= ROUNDING_NOISE)
                .count();
        System.out.printf(LABEL_FORMAT, "Most likely outcome", sharingTheLead > 1
                ? "no favourite, the options are level"
                : leader.name() + ", at " + twoDecimals(leader.value()));
    }

    void printPurchase(PurchaseResultDto purchase) {
        System.out.println();
        System.out.println("The purchase went through.");
        System.out.printf(LABEL_FORMAT, "Option bought", purchase.optionName());
        System.out.printf(LABEL_FORMAT, "Shares bought", purchase.quantity());
        System.out.printf(LABEL_FORMAT, "Cost of the shares", twoDecimals(purchase.sharesCost()));
        System.out.printf(LABEL_FORMAT, "Commission", twoDecimals(purchase.commission()));
        System.out.printf(LABEL_FORMAT, "Total paid", twoDecimals(purchase.totalPaid()));
        if (purchase.commissionChargedAtClose()) {
            System.out.println("  This event takes its commission from the winners when it closes,"
                    + " so nothing was added here.");
        }
    }

    private void printAccounts(MarketStateDto state) {
        System.out.println();
        System.out.println("Accounts:");
        System.out.printf(LABEL_FORMAT, "This event's MM account", twoDecimals(state.eventAccountBalance()));
        System.out.printf(LABEL_FORMAT, "Commission collected", twoDecimals(state.commissionCollected()));
        System.out.printf(LABEL_FORMAT, "Market maker overall", twoDecimals(state.marketMakerBalance()));
        if (state.marketMakerBalance() < 0) {
            System.out.println("  A negative overall balance is subsidy the market maker still has"
                    + " invested in events that have not closed.");
        }
    }

    private void printHistory(List<TradeDto> history) {
        System.out.println();
        if (history.isEmpty()) {
            System.out.println("Trading history: nothing has been bought on this event yet.");
            return;
        }
        System.out.println("Trading history, newest first:");
        int nameWidth = widthFor("Option", history.stream().map(TradeDto::optionName).toList());
        String rowFormat = "  %-3s %-" + nameWidth + "s %10s %13s %12s %12s%n";
        System.out.printf(rowFormat, "#", "Option", "Shares", "Cost", "Commission", "Total paid");
        System.out.printf(rowFormat, "-".repeat(3), "-".repeat(nameWidth), "-".repeat(10),
                "-".repeat(13), "-".repeat(12), "-".repeat(12));
        for (int i = 0; i < history.size(); i++) {
            TradeDto trade = history.get(i);
            System.out.printf(rowFormat, i + 1, trade.optionName(), trade.quantity(),
                    twoDecimals(trade.sharesCost()), twoDecimals(trade.commission()), twoDecimals(trade.totalPaid()));
        }
    }

    private void printOutcome(MarketStateDto state) {
        System.out.println();
        if (!state.closed()) {
            System.out.println("This event is still open for trading.");
            return;
        }
        System.out.println("This event is closed.");
        System.out.printf(LABEL_FORMAT, "Winning option", state.winningOptionName());
        System.out.printf(LABEL_FORMAT, "Winning shares held", state.winningShares());
        System.out.printf(LABEL_FORMAT, "Paid out to the holders", twoDecimals(state.totalPaidOut()));
        System.out.println("  The shares bought for every option are listed in the table above.");
    }

    private static String commissionText(EventInfoDto event) {
        return event.commissionPercent() + "% (" + event.commissionType()
                + " - " + event.commissionTiming() + ")";
    }

    private static String numberedOptions(List<String> optionNames) {
        StringBuilder numbered = new StringBuilder();
        for (int i = 0; i < optionNames.size(); i++) {
            if (i > 0) {
                numbered.append(", ");
            }
            numbered.append(i + 1).append(". ").append(optionNames.get(i));
        }
        return numbered.toString();
    }

    /** Wide enough for the heading and for every value under it. */
    private static int widthFor(String heading, List<String> values) {
        int widest = heading.length();
        for (String value : values) {
            widest = Math.max(widest, value.length());
        }
        return widest;
    }

    /**
     * Money and option values alike, to two decimal places. A single formatter for both keeps them
     * from drifting apart, and the guard below stops a balance that is a hair below zero from being
     * shown as "-0.00".
     */
    private static String twoDecimals(double value) {
        double rounded = Math.abs(value) < ROUNDING_NOISE ? 0.0 : value;
        return String.format(Locale.US, "%.2f", rounded);
    }
}
