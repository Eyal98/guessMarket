package gm.dto;

/**
 * One movement of a user's money, as a line of their account.
 *
 * @param number       counted from 1, in the order the movements happened
 * @param description  what the money was for
 * @param amount       positive when money came in, negative when it went out
 * @param balanceAfter the balance this movement left behind
 */
public record LedgerLineDto(int number, String description, double amount, double balanceAfter) {
}
