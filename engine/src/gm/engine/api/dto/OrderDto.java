package gm.engine.api.dto;

import java.io.Serializable;

/**
 * One order waiting in a book.
 *
 * @param userName who placed it
 * @param side     "Buy" or "Sell"
 * @param quantity how many shares are still waiting to be filled
 * @param price    what they are willing to pay, or want to be paid, for each share
 */
public record OrderDto(String userName, String side, long quantity, double price) implements Serializable {
}
