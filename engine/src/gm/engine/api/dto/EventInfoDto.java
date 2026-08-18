package gm.engine.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * The headline details of one event, as shown in a list.
 * <p>
 * The commission and the status arrive already broken down into plain values. Handing out the
 * engine's own commission and status types would have carried the fee arithmetic along with them,
 * putting engine logic within reach of whoever displays this.
 *
 * @param number            the position of the event among all loaded events, counted from 1. This is
 *                          the number the engine expects back when the caller selects this event, and
 *                          it stays the same whether the event appeared in a full list or a filtered
 *                          one.
 * @param id                the identifier the event carries in the events file
 * @param name              the name of the event
 * @param description       the free text description, including how the event is decided
 * @param commissionPercent how much commission the event charges, as a whole percentage
 * @param commissionType    when it is charged, in the wording the events file uses, such as
 *                          "on-purchase"
 * @param commissionTiming  the same thing said in words, such as "charged on every purchase"
 * @param optionNames       the possible outcomes, in the order they were declared
 * @param status            whether the event is still trading, ready to be displayed
 * @param tradingMethod     a short description of the pricing method, for example "LMSR (b=100)"
 */
public record EventInfoDto(int number, int id, String name, String description, int commissionPercent,
                           String commissionType, String commissionTiming, List<String> optionNames,
                           String status, String tradingMethod) implements Serializable {
}
