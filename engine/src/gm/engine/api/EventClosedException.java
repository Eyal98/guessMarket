package gm.engine.api;

/** Trading on, or closing, an event that has already been decided. */
public class EventClosedException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    public EventClosedException(String eventName) {
        super("The event \"" + eventName + "\" is already closed, so it cannot be traded on or closed again.");
    }
}
