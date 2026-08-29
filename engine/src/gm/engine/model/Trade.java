package gm.engine.model;

import java.io.Serializable;

/**
 * One completed purchase, kept so the event can show its trading history.
 *
 * @param userName   who traded
 * @param optionName the option that was bought
 * @param quantity   how many shares were bought
 * @param sharesCost what the shares themselves cost
 * @param commission what was charged on top as commission, zero unless the event charges on purchase
 */
public record Trade(String userName, String optionName, long quantity, double sharesCost,
                    double commission)
        implements Serializable {

    public double totalPaid() {
        return sharesCost + commission;
    }
}
