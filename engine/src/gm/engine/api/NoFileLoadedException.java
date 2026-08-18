package gm.engine.api;

/** A command was asked for that only makes sense once an events file has been loaded. */
public class NoFileLoadedException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    public NoFileLoadedException() {
        super("No events file is loaded yet. Please load an XML file first.");
    }
}
