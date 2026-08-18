package gm.engine.api;

/** The caller chose something that does not exist, such as an event or option number out of range. */
public class InvalidSelectionException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    public InvalidSelectionException(String message) {
        super(message);
    }
}
