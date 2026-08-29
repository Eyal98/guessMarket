package gm.engine.api.dto;

import java.util.List;

/**
 * Everything one event's prices have done, for drawing as a chart.
 *
 * @param eventName   the event this belongs to
 * @param optionNames the options, in the order every point lists their prices
 * @param points      oldest first, beginning with the moment the event opened
 */
public record PriceHistoryDto(String eventName, List<String> optionNames, List<PricePointDto> points) {

    public PriceHistoryDto {
        optionNames = List.copyOf(optionNames);
        points = List.copyOf(points);
    }
}
