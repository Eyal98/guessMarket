package gm.engine.api.dto;

import java.io.Serializable;

/**
 * The receipt for one purchase, together with the state of the event once it went through.
 *
 * @param optionName               the option that was bought
 * @param quantity                 how many shares were bought
 * @param sharesCost               what the shares themselves cost
 * @param commission               what was charged on top, zero unless the event charges on purchase
 * @param totalPaid                the sum of the two
 * @param commissionChargedAtClose whether this event takes its commission when it closes instead,
 *                                 which explains a commission of zero on a purchase
 * @param stateAfter               the event as it stands after the purchase
 */
public record PurchaseResultDto(String optionName, long quantity, double sharesCost, double commission,
                                double totalPaid, boolean commissionChargedAtClose,
                                MarketStateDto stateAfter) implements Serializable {
}
