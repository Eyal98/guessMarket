package gm.engine.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Everything worth showing about one user.
 *
 * @param number         their position among all users, counted from 1
 * @param name           their name
 * @param balance        what their account holds right now
 * @param blocked        whether they have spent past zero and can take no further part
 * @param marketMakerOf  the names of the events they run
 * @param participations the events they have acted on, and what they hold in each
 */
public record UserDetailDto(int number, String name, double balance, boolean blocked,
                            List<String> marketMakerOf, List<ParticipationDto> participations)
        implements Serializable {
}
