package gm.engine.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * One user's involvement in one event: what they hold, what it has cost them, and how it turned out.
 *
 * @param event          the event itself
 * @param options        what they hold of each option
 * @param commissionPaid everything they have paid in commission here
 * @param netResult      everything received back less everything paid, meaningful once it closes
 * @param trades         their own trades on this event, newest first
 */
public record ParticipationDto(EventInfoDto event, List<OptionHoldingDto> options, double commissionPaid,
                               double netResult, List<TradeDto> trades) implements Serializable {
}
