package gm.dto;

import java.util.List;

/**
 * One person's standing in an event, as shown on the event's own screen.
 *
 * @param userName their name
 * @param options  what they hold of each option, and what it cost them
 * @param blocked  whether they have spent past zero and can take no further part
 */
public record ParticipantDto(String userName, List<OptionHoldingDto> options, boolean blocked) {
}
