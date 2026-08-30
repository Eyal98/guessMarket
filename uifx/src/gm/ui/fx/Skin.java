package gm.ui.fx;

import javafx.scene.Scene;

import java.util.Locale;

/**
 * One of the looks the program can wear.
 * <p>
 * The split is deliberate: {@code guess-market.css} holds the things that make the layout work —
 * spacing, weights, alignment — and never changes, while a skin holds only colour and lettering. That
 * way a new look is one small file rather than a copy of the whole stylesheet, and no skin can
 * accidentally break the layout by forgetting a rule.
 */
public enum Skin {

    CLASSIC("Classic"),
    NIGHT("Night"),
    PARCHMENT("Parchment");

    /** The look the program starts in. The bonus must arrive switched off, so this is the plain one. */
    public static final Skin DEFAULT = CLASSIC;

    /** The rules every skin builds on, applied underneath whichever skin is chosen. */
    private static final String STRUCTURE = "guess-market.css";

    private final String displayName;

    Skin(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** The file holding this skin's colours and lettering. */
    public String stylesheet() {
        return "skin-" + name().toLowerCase(Locale.ROOT) + ".css";
    }

    /** Dresses a screen in this skin, replacing whatever it was wearing. */
    public void applyTo(Scene scene) {
        scene.getStylesheets().setAll(locate(STRUCTURE), locate(stylesheet()));
    }

    private static String locate(String fileName) {
        var found = Skin.class.getResource(fileName);
        if (found == null) {
            throw new IllegalStateException("The stylesheet \"" + fileName + "\" is missing from the jar.");
        }
        return found.toExternalForm();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
