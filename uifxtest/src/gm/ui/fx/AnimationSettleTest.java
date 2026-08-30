package gm.ui.fx;

import javafx.scene.layout.VBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A node left half way through a movement has to be put back.
 * <p>
 * A transition only runs its "finished" handler when it finishes. One that is cut short — because
 * the panel was rebuilt and a second movement started on the same node — never runs it, and leaves
 * the node wherever it had got to. A panel stranded at a fifth of its opacity looks exactly like a
 * panel that failed to load, which is how this was found.
 */
@DisplayName("Putting a node back after a movement")
class AnimationSettleTest {

    private static final double TOLERANCE = 0.0001;

    @Test
    @DisplayName("A node stranded part way through is restored completely")
    void aStrandedNodeIsRestored() {
        VBox stranded = new VBox();
        stranded.setOpacity(0.17);
        stranded.setScaleX(1.12);
        stranded.setScaleY(1.12);
        stranded.setTranslateX(-5);

        new Animations().settle(stranded);

        assertEquals(1.0, stranded.getOpacity(), TOLERANCE, "a half faded panel reads as a broken one");
        assertEquals(1.0, stranded.getScaleX(), TOLERANCE);
        assertEquals(1.0, stranded.getScaleY(), TOLERANCE);
        assertEquals(0.0, stranded.getTranslateX(), TOLERANCE);
    }

    @Test
    @DisplayName("Settling a node nothing has touched leaves it alone")
    void anUntouchedNodeIsLeftAlone() {
        VBox untouched = new VBox();

        new Animations().settle(untouched);

        assertEquals(1.0, untouched.getOpacity(), TOLERANCE);
        assertEquals(0.0, untouched.getTranslateX(), TOLERANCE);
    }
}
