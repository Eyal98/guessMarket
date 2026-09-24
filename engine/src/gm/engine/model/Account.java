package gm.engine.model;

/**
 * A balance of money. One class serves every account in the market: each person's and each event's.
 * <p>
 * Withdrawals are deliberately allowed to take the balance below zero. The exercise lets a person
 * overspend once, and blocks them for it rather than refusing the action; that rule belongs to
 * {@link User}, which is why the account itself only keeps the arithmetic.
 */
public final class Account {

    private double balance;

    public double balance() {
        return balance;
    }

    public void deposit(double amount) {
        requireNotNegative(amount, "deposited");
        balance += amount;
    }

    public void withdraw(double amount) {
        requireNotNegative(amount, "withdrawn");
        balance -= amount;
    }


    private static void requireNotNegative(double amount, String action) {
        if (amount < 0) {
            throw new IllegalArgumentException("The amount " + action + " cannot be negative, but it is " + amount + ".");
        }
    }
}
