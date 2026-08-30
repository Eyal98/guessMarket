package gm.engine.api.dto;

import java.io.Serializable;

/**
 * What one user holds of one option, and what it cost them.
 *
 * @param number     the option's position within its event, counted from 1
 * @param optionName the option's name
 * @param shares     how many shares of it they hold
 * @param paidFor    what they have paid for those shares, net of anything sold back
 * @param currentValue what the shares are worth at the market's present reckoning, or null
 *                     where it cannot say - an option nobody has traded has no price, and
 *                     valuing it at nought would call the holding worthless
 */
public record OptionHoldingDto(int number, String optionName, long shares, double paidFor,
                               Double currentValue) implements Serializable {
}
