package gm.ui.fx;

import gm.ui.fx.Animations.Motion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Animations")
class AnimationsTest {

    @Test
    @DisplayName("They arrive switched off")
    void theyArriveSwitchedOff() {
        assertFalse(new Animations().isOn(),
                "the bonus has to be turned on by whoever is marking, not found already running");
    }

    @Test
    @DisplayName("The switch works both ways")
    void theSwitchWorksBothWays() {
        Animations animations = new Animations();
        animations.setOn(true);
        assertTrue(animations.isOn());
        animations.setOn(false);
        assertFalse(animations.isOn(), "turning movement off again has to actually stop it");
    }

    @Test
    @DisplayName("No single movement outstays the two seconds allowed")
    void nothingOutstaysItsWelcome() {
        for (Motion motion : Motion.values()) {
            assertTrue(motion.length().lessThanOrEqualTo(Animations.LONGEST_ALLOWED),
                    motion + " runs for " + motion.length().toMillis() + "ms, over the "
                            + Animations.LONGEST_ALLOWED.toMillis() + "ms allowed");
        }
    }
}
