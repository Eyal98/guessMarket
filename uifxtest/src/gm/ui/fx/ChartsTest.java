package gm.ui.fx;

import gm.engine.api.dto.PriceHistoryDto;
import gm.engine.api.dto.PricePointDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether there is anything worth drawing.
 * <p>
 * An order book that nobody has traded on has no price for either option, and an empty chart with a
 * plausible looking axis is worse than no chart: it invites the reader to believe the market has
 * been sitting at nought. The same reasoning as the dash shown instead of a nought elsewhere.
 */
@DisplayName("Deciding whether a chart has anything to say")
class ChartsTest {

    private PriceHistoryDto historyOf(List<Double>... points) {
        List<PricePointDto> steps = new java.util.ArrayList<>();
        for (int step = 0; step < points.length; step++) {
            steps.add(new PricePointDto(step, points[step]));
        }
        return new PriceHistoryDto("World Cap Winner", List.of("Argentina", "Spain"), steps);
    }

    @Test
    @DisplayName("A book nobody has traded on has nothing to draw")
    void anUntradedBookHasNothingToDraw() {
        assertFalse(Charts.hasAnythingToDraw(historyOf(Arrays.asList(null, null))));
    }

    @Test
    @DisplayName("One traded option is enough to be worth drawing")
    void oneTradedOptionIsEnough() {
        assertTrue(Charts.hasAnythingToDraw(
                historyOf(Arrays.asList(null, null), Arrays.asList(0.60, null))));
    }

    @Test
    @DisplayName("An event that never opened has nothing to draw")
    void anEventThatNeverOpenedHasNothingToDraw() {
        assertFalse(Charts.hasAnythingToDraw(
                new PriceHistoryDto("Mujtaba is Dead", List.of("Hell Yea !", "No way !"), List.of())));
    }

    @Test
    @DisplayName("An LMSR event always has something to draw, from the moment it opens")
    void anLmsrEventAlwaysHasSomethingToDraw() {
        assertTrue(Charts.hasAnythingToDraw(historyOf(Arrays.asList(0.5, 0.5))));
    }
}
