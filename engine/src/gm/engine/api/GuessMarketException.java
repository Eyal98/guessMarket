package gm.engine.api;

/**
 * Something the caller asked for could not be done. The message is already written for a person to
 * read, so a user interface can show it as it is without knowing anything about the cause.
 */
public class GuessMarketException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GuessMarketException(String message) {
        super(message);
    }

    public GuessMarketException(String message, Throwable cause) {
        super(message, cause);
    }
}
