package gm.engine.api.dto;

import java.io.Serializable;

/**
 * The current standing of one option of an event.
 *
 * @param number       the position of the option within its event, counted from 1
 * @param name         the name of the option
 * @param value        what the option is currently worth, between 0 and 1
 * @param sharesBought how many shares of it have been bought so far
 */
public record OptionStateDto(int number, String name, double value, long sharesBought)
        implements Serializable {
}
