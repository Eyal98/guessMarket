package gm.engine.api.dto;

/**
 * What somebody was worth after one particular movement of money.
 *
 * @param step    which movement this is, counting from nought
 * @param balance what was in the account once it had happened
 */
public record BalancePointDto(int step, double balance) {
}
