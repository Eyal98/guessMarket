package gm.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The life of an event runs one way only: it has not started, then it is trading, then it is decided.
 * Keeping the rule on the enum means every caller asks the same question rather than each inventing
 * its own idea of what is allowed.
 */
class EventStatusTest {

    @Test
    @DisplayName("An event that has not started can be opened")
    void aNewEventCanBeOpened() {
        assertTrue(EventStatus.NOT_STARTED.canMoveTo(EventStatus.ACTIVE));
    }

    @Test
    @DisplayName("An active event can be closed")
    void anActiveEventCanBeClosed() {
        assertTrue(EventStatus.ACTIVE.canMoveTo(EventStatus.CLOSED));
    }

    @Test
    @DisplayName("An event cannot be opened twice, nor closed before it opens")
    void theOrderCannotBeSkippedOrRepeated() {
        assertFalse(EventStatus.ACTIVE.canMoveTo(EventStatus.ACTIVE));
        assertFalse(EventStatus.NOT_STARTED.canMoveTo(EventStatus.CLOSED));
    }

    @Test
    @DisplayName("A closed event is finished for good")
    void aClosedEventNeverMovesAgain() {
        assertFalse(EventStatus.CLOSED.canMoveTo(EventStatus.ACTIVE));
        assertFalse(EventStatus.CLOSED.canMoveTo(EventStatus.NOT_STARTED));
        assertFalse(EventStatus.CLOSED.canMoveTo(EventStatus.CLOSED));
    }

    @Test
    @DisplayName("Only an active event allows trading")
    void tradingNeedsAnActiveEvent() {
        assertFalse(EventStatus.NOT_STARTED.allowsTrading());
        assertTrue(EventStatus.ACTIVE.allowsTrading());
        assertFalse(EventStatus.CLOSED.allowsTrading());
    }

    @Test
    @DisplayName("Each state says how it should be shown")
    void eachStateHasWordingForTheScreen() {
        assertEquals("Not started", EventStatus.NOT_STARTED.displayName());
        assertEquals("Active", EventStatus.ACTIVE.displayName());
        assertEquals("Closed", EventStatus.CLOSED.displayName());
    }
}
