package gm.engine.model;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * When an event collects its commission. Each constant implements both charging points, so the two
 * policies stay side by side instead of spreading through the code as conditionals, and adding a
 * third policy later means adding a constant here and nothing else.
 */
public enum CommissionType {

    /** Charged from the buyer on every purchase, on top of the price of the shares. */
    ON_PURCHASE("on-purchase", "charged on every purchase") {
        @Override
        public double purchaseFee(double sharesCost, int percent) {
            return sharesCost * percent / 100.0;
        }

        @Override
        public double closingFee(double grossPayout, int percent) {
            return 0.0;
        }
    },

    /** Charged from the winners when the event closes, out of what they are owed. */
    ON_CLOSE("on-close", "charged from the winners when the event closes") {
        @Override
        public double purchaseFee(double sharesCost, int percent) {
            return 0.0;
        }

        @Override
        public double closingFee(double grossPayout, int percent) {
            return grossPayout * percent / 100.0;
        }
    };

    private final String fileValue;
    private final String displayName;

    CommissionType(String fileValue, String displayName) {
        this.fileValue = fileValue;
        this.displayName = displayName;
    }

    /** What a buyer pays on top of {@code sharesCost}. */
    public abstract double purchaseFee(double sharesCost, int percent);

    /** What is taken out of the winners' payout when the event closes. */
    public abstract double closingFee(double grossPayout, int percent);

    /** The value that represents this type in an events file. */
    public String fileValue() {
        return fileValue;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Looks a type up by the value written in an events file, ignoring surrounding spaces and letter
     * case. Returns an empty optional for anything unrecognised, leaving the caller to decide how to
     * complain about it.
     */
    public static Optional<CommissionType> fromFileValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalised = value.trim();
        return Arrays.stream(values())
                .filter(type -> type.fileValue.equalsIgnoreCase(normalised))
                .findFirst();
    }

    /** The accepted file values, for use in error messages. */
    public static String allowedFileValues() {
        return Arrays.stream(values())
                .map(CommissionType::fileValue)
                .collect(Collectors.joining(" or "));
    }
}
