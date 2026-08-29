package gm.ui.fx;

/**
 * The program's entry point.
 * <p>
 * It exists only to call {@link GuessMarketApp}, and deliberately does not extend
 * {@code Application} itself. When the main class of a jar extends {@code Application}, the Java
 * launcher insists on finding JavaFX as a named module and refuses to start with "JavaFX runtime
 * components are missing" — a failure that depends on how the program was launched rather than on
 * anything being wrong. Going through an ordinary class sidesteps that entirely.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        GuessMarketApp.main(args);
    }
}
