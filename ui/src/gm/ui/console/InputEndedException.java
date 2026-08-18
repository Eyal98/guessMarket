package gm.ui.console;

/**
 * There is no more input to read.
 * <p>
 * It happens when the console is closed or when the program is fed a file of commands that runs out.
 * Every reader can then keep a straightforward return type, and the main loop has one place to notice
 * that it is time to stop rather than every prompt having to report it separately.
 */
final class InputEndedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InputEndedException() {
        super("There is no more input to read.");
    }
}
