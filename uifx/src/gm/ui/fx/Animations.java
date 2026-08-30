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
        REFUSING(Duration.millis(280)),
        /** A tab arriving slides in from the side the reader moved towards. */
        SLIDING(Duration.millis(280));

        private final Duration length;

        Motion(Duration length) {
            this.length = length;
        }

        /** How long the whole movement lasts, start to finish. */
        public Duration length() {
            return length;
        }
    }

    /**
     * Slides a panel in from one side, as though the reader had moved along to it.
     * <p>
     * The direction carries meaning: moving to the tab on the right brings the new panel in from the
     * right, and moving back brings it from the left, so the movement agrees with the direction of
     * travel instead of contradicting it.
     *
     * @param fromTheRight which side the panel comes from
     */
    public void slideIn(Node node, boolean fromTheRight) {
        if (!on || node == null) {
            return;
        }
        settle(node);
        // A tab's panel has no width of its own until it has been laid out, and the switch is what
        // puts it there - so on the first showing the window's width is what it will become.
        double distance = node.getLayoutBounds().getWidth();
        if (distance <= 0) {
            distance = node.getScene() == null ? 0 : node.getScene().getWidth();
        }
        if (distance <= 0) {
            return;
        }
        TranslateTransition slide = new TranslateTransition(Motion.SLIDING.length(), node);
        slide.setFromX(fromTheRight ? distance : -distance);
        slide.setToX(0);
        node.getProperties().put(MOVING, slide);
        slide.setOnFinished(ignored -> settle(node));
        slide.play();
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
            // Sliding needs to know which way, so it has its own way in: slideIn.
            case SLIDING -> throw new IllegalArgumentException("Use slideIn to say which side.");
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
