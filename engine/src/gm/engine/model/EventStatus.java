package gm.engine.model;

/**
 * Where an event stands in its life, and what may be done to it there.
 * <p>
 * The life runs one way only — not started, trading, decided — and never backwards. Holding both the
 * permitted moves and the wording for the screen here means no caller has to reconstruct the rule,
 * and none of them can disagree about it.
 */
public enum EventStatus {

    /** Loaded from the file but not yet opened by its market maker. No trading. */
    NOT_STARTED("Not started"),

    /** Opened by its market maker and trading. */
    ACTIVE("Active"),

    /** Decided by its market maker. Finished for good. */
    CLOSED("Closed");

    private final String displayName;

    EventStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Whether an event in this state may be traded on. */
    public boolean allowsTrading() {
        return this == ACTIVE;
    }

    /** Whether an event may move from this state to {@code next}. */
    public boolean canMoveTo(EventStatus next) {
        return switch (this) {
            case NOT_STARTED -> next == ACTIVE;
            case ACTIVE -> next == CLOSED;
            case CLOSED -> false;
        };
    }
}
