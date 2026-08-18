package gm.engine.model;

import java.io.Serializable;

/**
 * A balance of money. One class serves the event accounts and the market maker account, and will
 * serve user accounts once the system supports more than one user.
 * <p>
 * Withdrawals are deliberately allowed to take the balance below zero: the market maker pays the
 * subsidy of every event out of an account that starts empty, so a negative balance is the normal
 * way of saying "this is what the market maker has invested so far".
 */
public final class Account implements Serializable {

    private static final long serialVersionUID = 1L;

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

    /** Moves everything this account holds into {@code target}, leaving this one empty. */
    public void drainInto(Account target) {
        target.deposit(balance);
        balance = 0;
    }

    private static void requireNotNegative(double amount, String action) {
        if (amount < 0) {
            throw new IllegalArgumentException("The amount " + action + " cannot be negative, but it is " + amount + ".");
        }
    }
}
