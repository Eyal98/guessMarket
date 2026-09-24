package gm.client.account;

import gm.dto.EventInfoDto;
import gm.dto.ParticipationDto;

/**
 * One line of the "events participation \ owner" table: an event and how the logged-in user stands in
 * it.
 *
 * @param event   the event
 * @param runsIt  whether the user is its market maker
 * @param holding what the user holds in it, or null if they have not taken part
 */
public record EventRole(EventInfoDto event, boolean runsIt, ParticipationDto holding) {

    String role() {
        if (runsIt) {
            return holding == null ? "Market maker" : "Market maker, trading";
        }
        return holding == null ? "Not taken part yet" : "Trading";
    }
}
