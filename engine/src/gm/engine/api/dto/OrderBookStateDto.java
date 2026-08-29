package gm.engine.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Everything worth showing about an order book event: both books, what they say about price, and
 * where each participant stands.
 *
 * @param event               the headline details of the event
 * @param options             the market in each option
 * @param eventAccountBalance what the event account holds, which is the money minting put there
 * @param commissionCollected what the market maker has earned from it so far
 * @param participants        everyone who has acted on this event
 * @param baseValue           what a winning share pays, and what a whole pair is worth
 * @param mintAllowed         whether two opposing buyers may create new shares between them
 * @param highestAllowedPrice the most a single share may be offered at
 */
public record OrderBookStateDto(EventInfoDto event, List<OptionMarketDto> options,
                                double eventAccountBalance, double commissionCollected,
                                List<ParticipantDto> participants, int baseValue, boolean mintAllowed,
                                double highestAllowedPrice) implements Serializable {
}
