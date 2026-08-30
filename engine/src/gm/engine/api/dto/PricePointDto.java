package gm.engine.api.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where every option stood after one trade.
 *
 * @param step           which point in the event's life this is, counting from nought
 * @param pricePerOption what each option was worth, in the order the options are listed, with null
 *                       for an option the market could not price at that moment
 */
public record PricePointDto(int step, List<Double> pricePerOption) {

    public PricePointDto {
        pricePerOption = Collections.unmodifiableList(new ArrayList<>(pricePerOption));
    }
}
