package gm.engine.api.dto;

import java.util.List;

/**
 * Everything one person's money has done, for drawing as a chart.
 *
 * @param userName whose money this is
 * @param points   oldest first, beginning with the cash the file gave them
 */
public record BalanceHistoryDto(String userName, List<BalancePointDto> points) {

    public BalanceHistoryDto {
        points = List.copyOf(points);
    }
}
