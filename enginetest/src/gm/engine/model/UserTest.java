package gm.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A user, their account, and the one rule that governs it: spending more than they hold is allowed to
 * happen, but it is the last thing they ever do.
 * <p>
 * In this version nobody arrives with money. A user starts at nothing and puts money in themselves,
 * and every movement of their money, whoever caused it, becomes a line of its own in their ledger.
 */
class UserTest {

    private static final double TOLERANCE = 0.0001;

    @Test
    @DisplayName("A new user starts with nothing, owes nothing, and is free to act")
    void aNewUserStartsEmpty() {
        User user = new User("Menash");

        assertEquals("Menash", user.name());
        assertEquals(0.0, user.account().balance(), TOLERANCE);
        assertFalse(user.isBlocked());
        assertTrue(user.ledger().isEmpty(), "nothing has happened to their money yet");
    }

    @Test
    @DisplayName("Loading funds raises the balance and is written down as a deposit")
    void aDepositIsALedgerLine() {
        User user = new User("Menash");

        user.deposit(250);

        assertEquals(250.0, user.account().balance(), TOLERANCE);
        User.LedgerLine line = user.ledger().get(0);
        assertEquals(1, line.number());
        assertEquals("Deposit", line.description());
        assertEquals(250.0, line.amount(), TOLERANCE);
        assertEquals(250.0, line.balanceAfter(), TOLERANCE);
    }

    @Test
    @DisplayName("Only a real, positive amount can be loaded")
    void nonsenseDepositsAreRefused() {
        User user = new User("Menash");

        assertThrows(IllegalArgumentException.class, () -> user.deposit(0));
        assertThrows(IllegalArgumentException.class, () -> user.deposit(-5));
        assertThrows(IllegalArgumentException.class, () -> user.deposit(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> user.deposit(Double.POSITIVE_INFINITY));
        assertTrue(user.ledger().isEmpty(), "a refused deposit leaves no trace");
    }

    @Test
    @DisplayName("Every payment in and out gets its own line, numbered, signed, with the balance after it")
    void everyMovementIsALine() {
        User user = new User("Menash");
        user.deposit(100);

        user.pay(30, "Bought 10 of \"Yes\"");
        user.receive(12.5, "Sold 5 of \"Yes\"");

        List<User.LedgerLine> ledger = user.ledger();
        assertEquals(3, ledger.size());
        assertEquals(new User.LedgerLine(2, "Bought 10 of \"Yes\"", -30.0, 70.0), ledger.get(1));
        assertEquals(new User.LedgerLine(3, "Sold 5 of \"Yes\"", 12.5, 82.5), ledger.get(2));
    }

    @Test
    @DisplayName("Spending everything down to exactly zero is not yet a block")
    void spendingDownToZeroIsStillAllowed() {
        User user = new User("Menash");
        user.deposit(100);

        user.pay(100, "Everything");

        assertEquals(0.0, user.account().balance(), TOLERANCE);
        assertFalse(user.isBlocked(), "a balance of exactly zero is not a negative balance");
    }

    @Test
    @DisplayName("Spending more than they hold goes through, and blocks them from then on")
    void overspendingIsAllowedButBlocks() {
        User user = new User("Menash");
        user.deposit(100);

        user.pay(150, "Too much");

        assertEquals(-50.0, user.account().balance(), TOLERANCE);
        assertTrue(user.isBlocked());
        assertEquals(-50.0, user.ledger().get(1).balanceAfter(), TOLERANCE,
                "the ledger shows the overdraft exactly as it happened");
    }

    @Test
    @DisplayName("Money arriving later does not unblock a blocked user")
    void beingPaidDoesNotUndoABlock() {
        User user = new User("Menash");
        user.deposit(100);
        user.pay(150, "Too much");

        user.receive(500, "A win");

        assertEquals(450.0, user.account().balance(), TOLERANCE);
        assertTrue(user.isBlocked(), "the exercise says a blocked user is finished, whatever the balance");
    }

    @Test
    @DisplayName("A blocked user cannot load funds either, because a blocked user can do nothing at all")
    void aBlockedUserCannotDeposit() {
        User user = new User("Menash");
        user.deposit(10);
        user.pay(20, "Too much");

        IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> user.deposit(100));

        assertTrue(refusal.getMessage().contains("Menash"));
        assertEquals(-10.0, user.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("The ledger handed out cannot be written to")
    void theLedgerIsReadOnly() {
        User user = new User("Menash");
        user.deposit(1);

        assertThrows(UnsupportedOperationException.class,
                () -> user.ledger().add(new User.LedgerLine(9, "Forged", 1e6, 1e6)));
    }
}
