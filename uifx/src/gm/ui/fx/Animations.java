package gm.ui.fx;

import javafx.animation.FadeTransition;
import javafx.animation.Transition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * The small movements the screen can make, and the one switch that allows them.
 * <p>
 * They arrive switched off. A bonus is meant to be turned on deliberately by whoever is marking,
 * not discovered already running, and there is a second reason: movement that cannot be stopped is
 * an irritation rather than a feature. When the switch is off nothing here touches a node at all,
 * so the screen behaves exactly as it would if this class did not exist.
 */
public final class Animations {

    /** The exercise allows a single animation two seconds. Nothing here comes close. */
    public static final Duration LONGEST_ALLOWED = Duration.seconds(2);

    /** Every movement the screen can make, and how long the whole of it takes. */
    public enum Motion {

        /** A panel that has just been rebuilt fades up, so a change of subject is visible. */
        APPEARING(Duration.millis(260)),
        /** A number that has just changed swells and settles, so money moving is noticed. */
        PULSING(Duration.millis(320)),
        /** A panel whose action was refused shakes its head. */
        REFUSING(Duration.millis(280));

        private final Duration length;

        Motion(Duration length) {
            this.length = length;
        }

        /** How long the whole movement lasts, start to finish. */
        public Duration length() {
            return length;
        }
    }

    /** How far the shake travels either side of where the panel really sits. */
    private static final double SHAKE_DISTANCE = 5;
    /** How much bigger a pulsing number grows before settling back. */
    private static final double PULSE_GROWTH = 1.12;

    /** Where a node remembers the movement currently running on it, so a second one can stop it. */
    private static final String MOVING = "gm.animations.moving";

    private boolean on;

    /** Whether movement is allowed at all. False until somebody asks for it. */
    public boolean isOn() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    /**
     * Moves a node, if movement is switched on. Every transition puts the node back exactly as it
     * found it, so a screen that has been animated is indistinguishable from one that has not.
     */
    public void play(Motion motion, Node node) {
        if (!on || node == null) {
            return;
        }
        settle(node);
        Transition movement = switch (motion) {
            case APPEARING -> fadeUp(motion, node);
            case PULSING -> swell(motion, node);
            case REFUSING -> shake(motion, node);
        };
        node.getProperties().put(MOVING, movement);
        movement.setOnFinished(ignored -> settle(node));
        movement.play();
    }

    /**
     * Puts a node back exactly as it was found, stopping anything still moving it.
     * <p>
     * This is what makes an interrupted movement harmless. A transition runs its finished handler
     * only when it finishes: one cut short — because the panel was rebuilt and a second movement
     * started on the same node — never runs it, and abandons the node wherever it had got to. A
     * panel left at a fifth of its opacity is indistinguishable from one that failed to load.
     */
    public void settle(Node node) {
        if (node == null) {
            return;
        }
        if (node.getProperties().remove(MOVING) instanceof Transition running) {
            running.stop();
        }
        node.setOpacity(1);
        node.setScaleX(1);
        node.setScaleY(1);
        node.setTranslateX(0);
    }

    private Transition fadeUp(Motion motion, Node node) {
        FadeTransition fade = new FadeTransition(motion.length(), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        return fade;
    }

    private Transition swell(Motion motion, Node node) {
        ScaleTransition swell = new ScaleTransition(motion.length().divide(2), node);
        swell.setFromX(1);
        swell.setFromY(1);
        swell.setToX(PULSE_GROWTH);
        swell.setToY(PULSE_GROWTH);
        swell.setCycleCount(2);
        swell.setAutoReverse(true);
        return swell;
    }

    private Transition shake(Motion motion, Node node) {
        TranslateTransition shake = new TranslateTransition(motion.length().divide(4), node);
        shake.setFromX(-SHAKE_DISTANCE);
        shake.setToX(SHAKE_DISTANCE);
        shake.setCycleCount(4);
        shake.setAutoReverse(true);
        return shake;
    }
}
