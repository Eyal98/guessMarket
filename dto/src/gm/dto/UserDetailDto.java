package gm.dto;

import java.util.List;

/**
 * Everything worth showing a user about themselves.
 *
 * @param name           their name
 * @param balance        what their account holds right now
 * @param blocked        whether they have spent past zero and can take no further part
 * @param marketMakerOf  the names of the events they run
 * @param participations the events they have acted on, and what they hold in each
 */
public record UserDetailDto(String name, double balance, boolean blocked, List<String> marketMakerOf,
                            List<ParticipationDto> participations) {
}
