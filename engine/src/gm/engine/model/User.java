package gm.engine.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Somebody who trades on the market: a name, an account, the ledger of everything that ever happened
 * to that account, and whether they are still allowed to act.
 * <p>
 * Nobody is handed money any more. A user arrives with an empty account and loads funds into it
 * themselves, and from then on every payment in or out, whether they made it or somebody else's
 * action caused it, is written down as a line of its own with the balance it left behind.
 * <p>
 * The exercise is precise about running out of money. An action that would take the balance below
 * zero is not refused — it goes through, the balance really does go negative, and from that moment
 * the user can do nothing more. Money arriving afterwards does not undo it. Keeping that rule inside
 * {@link #pay(double, String)} means no caller can spend on a user's behalf and forget to apply it.
 */
public final class User {

    /** What {@link #deposit(double)} writes in the ledger. */
    public static final String DEPOSIT = "Deposit";

    private final String name;
    private final Account account = new Account();
    private final List<LedgerLine> ledger = new ArrayList<>();
    private boolean blocked;

    /**
     * One movement of this user's money.
     *
     * @param number       counted from 1, in the order the movements happened
     * @param description  what the money was for, written for the user to read
     * @param amount       positive when money came in, negative when it went out
     * @param balanceAfter the balance this movement left behind
     */
    public record LedgerLine(int number, String description, double amount, double balanceAfter) {
    }

    public User(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String name() {
        return name;
    }

    public Account account() {
        return account;
    }

    /** Whether this user has spent past zero and is therefore finished with the market. */
    public boolean isBlocked() {
        return blocked;
    }

    /**
     * Loads money into the account from outside the market. It is an action like any other, so a
     * blocked user, who may take no action at all, cannot do it.
     */
    public void deposit(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("An amount to load must be a positive number, but it is "
                    + amount + ".");
        }
        if (blocked) {
            throw new IllegalStateException(name + " has already spent past zero and can take no further"
                    + " part in the market, loading funds included.");
        }
        receive(amount, DEPOSIT);
    }

    /** Takes money out, blocking the user if that leaves them owing. */
    public void pay(double amount, String description) {
        account.withdraw(amount);
        if (account.balance() < 0) {
            blocked = true;
        }
        write(description, -amount);
    }

    /** Puts money in. Never unblocks a user who has already spent past zero. */
    public void receive(double amount, String description) {
        account.deposit(amount);
        write(description, amount);
    }

    /** Every movement of this user's money, oldest first. */
    public List<LedgerLine> ledger() {
        return Collections.unmodifiableList(ledger);
    }

    private void write(String description, double amount) {
        ledger.add(new LedgerLine(ledger.size() + 1, Objects.requireNonNull(description, "description"),
                amount, account.balance()));
    }
}
