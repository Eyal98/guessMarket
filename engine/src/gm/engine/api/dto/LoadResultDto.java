package gm.engine.api.dto;

import java.io.Serializable;

/**
 * What came of loading an events file.
 *
 * @param filePath      the file that was read
 * @param eventsLoaded  how many events it contained
 * @param totalSubsidy  what funding all of them cost the market maker
 */
public record LoadResultDto(String filePath, int eventsLoaded, double totalSubsidy) implements Serializable {
}
