package gm.dto;

import java.util.List;

/**
 * The headline details of one event, as shown in a list.
 * <p>
 * The commission and the status arrive already broken down into plain values. Handing out the
 * engine's own commission and status types would have carried the fee arithmetic along with them,
 * putting engine logic within reach of whoever displays this.
 *
 * @param number            the position of the event among every event on the server, counted from 1.
 *                          Events are only ever added, never removed, so this stays the event's number
 *                          for as long as the server runs, and it is what the server expects back when
 *                          the caller selects this event.
 * @param name              the name of the event
 * @param description       the free text description, including how the event is decided
 * @param commissionPercent how much commission the event charges, as a whole percentage
 * @param commissionType    when it is charged, in the wording the events file uses, such as
 *                          "on-purchase"
 * @param commissionTiming  the same thing said in words, such as "charged on every purchase"
 * @param optionNames       the possible outcomes, in the order they were declared
 * @param status            whether the event is still trading, ready to be displayed
 * @param tradingMethod     a short description of the pricing method, for example "LMSR (b=100)"
 * @param methodKind        which market this is in one word, for filtering: LMSR or Order book
 * @param marketMakerName   the user who runs this event: whoever uploaded the file it came in
 * @param accountBalance    what the event's own account holds, which the overview list shows beside it
 * @param winningOptionName which option the event was decided on, or null while it is still undecided
 */
public record EventInfoDto(int number, String name, String description, int commissionPercent,
                           String commissionType, String commissionTiming, List<String> optionNames,
                           String status, String tradingMethod, String methodKind,
                           String marketMakerName, double accountBalance,
                           String winningOptionName) {
}
