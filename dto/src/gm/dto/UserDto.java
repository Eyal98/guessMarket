package gm.dto;

/**
 * One user as everybody else sees them: the exercise asks that each user can see every other one's
 * name, balance, and whether they run an event.
 *
 * @param name        their name, which is unique across the market
 * @param balance     what their account holds right now
 * @param blocked     whether they have spent past zero and can take no further part
 * @param marketMaker whether they run at least one event
 */
public record UserDto(String name, double balance, boolean blocked, boolean marketMaker) {
}
