package gm.engine.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Somebody who trades on the market: a name, an account, and whether they are still allowed to act.
 * <p>
 * The exercise is precise about running out of money. An action that would take the balance below
 * zero is not refused — it goes through, the balance really does go negative, and from that moment
 * the user can do nothing more. Money arriving afterwards does not undo it. Keeping that rule inside
 * {@link #pay(double)} means no caller can spend on a user's behalf and forget to apply it.
 */
public final class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final Account account = new Account();
    /** Always an ArrayList, which is serializable; the declared type simply cannot say so. */
    @SuppressWarnings("serial")
    private final List<BalanceSample> balanceHistory = new ArrayList<>();
    private boolean blocked;

    /** What this user was worth after one particular movement of money. */
    public record BalanceSample(int step, double balance) implements Serializable {
    }

    public User(String name, double initialCash) {
        this.name = Objects.requireNonNull(name, "name");
        account.deposit(initialCash);
        rememberBalance();
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

    /** Takes money out, blocking the user if that leaves them owing. */
    public void pay(double amount) {
        account.withdraw(amount);
        if (account.balance() < 0) {
            blocked = true;
        }
        rememberBalance();
    }

    /** Puts money in. Never unblocks a user who has already spent past zero. */
    public void receive(double amount) {
        account.deposit(amount);
        rememberBalance();
    }

    /**
     * Every balance this user has ever had, oldest first, starting with the cash they were given.
     * A chart of their fortunes is drawn straight from this.
     */
    public List<BalanceSample> balanceHistory() {
        return List.copyOf(balanceHistory);
    }

    private void rememberBalance() {
        balanceHistory.add(new BalanceSample(balanceHistory.size(), account.balance()));
    }
}
