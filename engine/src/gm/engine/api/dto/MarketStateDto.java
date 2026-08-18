package gm.engine.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * The full picture of one event: where its options stand, what its account holds, and everything that
 * has been traded on it.
 * <p>
 * Nothing in here is worked out on demand. Whether the event is closed is carried as a value rather
 * than derived from the winning option, so this stays a container that is only ever read from.
 *
 * @param event               the headline details of the event
 * @param options             the standing of every option, in declaration order
 * @param eventAccountBalance what the event account holds right now
 * @param commissionCollected how much commission the event has taken so far
 * @param marketMakerBalance  what the market maker account holds; it is negative while the market
 *                            maker is still out of pocket for the subsidies it paid
 * @param history             the trades of this event, newest first
 * @param closed              whether the event has been decided
 * @param winningOptionName   the option the event ended on, or {@code null} while it is open
 * @param winningShares       how many shares of the winning option were held, zero while open
 * @param totalPaidOut        what the winners received, zero while the event is open
 */
public record MarketStateDto(EventInfoDto event, List<OptionStateDto> options, double eventAccountBalance,
                             double commissionCollected, double marketMakerBalance, List<TradeDto> history,
                             boolean closed, String winningOptionName, long winningShares,
                             double totalPaidOut) implements Serializable {
}
