package gm.engine.api.dto;

import java.io.Serializable;

/**
 * One user as they appear in a list.
 *
 * @param number  their position among all users, counted from 1
 * @param name    their name, which is unique across the market
 * @param balance what their account holds right now
 * @param blocked whether they have spent past zero and can take no further part
 */
public record UserDto(int number, String name, double balance, boolean blocked) implements Serializable {
}
