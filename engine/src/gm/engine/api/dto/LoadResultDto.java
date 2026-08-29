package gm.engine.api.dto;

import java.io.Serializable;

/**
 * What came of loading an events file.
 *
 * @param filePath      the file that was read
 * @param eventsLoaded  how many events it contained
 * @param costOfOpeningEverything what opening every event would cost its market maker.
 *                                Nothing has been paid yet: an event is funded when its
 *                                market maker opens it, not when the file is read.
 */
public record LoadResultDto(String filePath, int eventsLoaded, double costOfOpeningEverything) implements Serializable {
}
