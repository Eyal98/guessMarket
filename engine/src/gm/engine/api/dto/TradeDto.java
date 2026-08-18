package gm.engine.api.dto;

import java.io.Serializable;

/**
 * One completed purchase, as it is handed out of the engine.
 * <p>
 * The total is carried as a value rather than worked out here, so this record stays what it is meant
 * to be: a container that is only ever read from.
 *
 * @param optionName the option that was bought
 * @param quantity   how many shares were bought
 * @param sharesCost what the shares themselves cost
 * @param commission what was charged on top, zero unless the event charges on purchase
 * @param totalPaid  the sum of the two
 */
public record TradeDto(String optionName, long quantity, double sharesCost, double commission,
                       double totalPaid) implements Serializable {
}
