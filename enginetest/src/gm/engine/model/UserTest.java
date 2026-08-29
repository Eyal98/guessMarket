package gm.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A user and the one rule that governs their account: spending more than they hold is allowed to
 * happen, but it is the last thing they ever do.
 */
class UserTest {

    private static final double TOLERANCE = 0.0001;

    @Test
    @DisplayName("A new user holds the cash the file gave them and is free to act")
    void aNewUserStartsWithTheirCash() {
        User user = new User("Menash", 100);

        assertEquals("Menash", user.name());
        assertEquals(100.0, user.account().balance(), TOLERANCE);
        assertFalse(user.isBlocked());
    }

    @Test
    @DisplayName("Spending within the balance leaves the user free to act")
    void spendingWhatTheyHaveKeepsThemActive() {
        User user = new User("Menash", 100);

        user.pay(60);

        assertEquals(40.0, user.account().balance(), TOLERANCE);
        assertFalse(user.isBlocked());
    }

    @Test
    @DisplayName("Spending everything down to exactly zero is not yet a block")
    void spendingDownToZeroIsStillAllowed() {
        User user = new User("Menash", 100);

        user.pay(100);

        assertEquals(0.0, user.account().balance(), TOLERANCE);
        assertFalse(user.isBlocked(), "a balance of exactly zero is not a negative balance");
    }

    @Test
    @DisplayName("Spending more than they hold goes through, and blocks them from then on")
    void overspendingIsAllowedButBlocks() {
        User user = new User("Menash", 100);

        user.pay(150);

        assertEquals(-50.0, user.account().balance(), TOLERANCE);
        assertTrue(user.isBlocked());
    }

    @Test
    @DisplayName("Money arriving later does not unblock a blocked user")
    void beingPaidDoesNotUndoABlock() {
        User user = new User("Menash", 100);
        user.pay(150);

        user.receive(500);

        assertEquals(450.0, user.account().balance(), TOLERANCE);
        assertTrue(user.isBlocked(), "the exercise says a blocked user is finished, whatever the balance");
    }
}
