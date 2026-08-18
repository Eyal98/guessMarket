package gm.engine.api;

/** Saving or loading a saved system state failed. */
public class PersistenceException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
