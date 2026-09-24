package gm.dto;

import java.util.List;

/**
 * What an uploaded events file added to the market.
 *
 * @param fileName   the name the file had on the uploader's computer
 * @param eventNames the events it added, in file order, every one of them run by the uploader
 */
public record UploadResultDto(String fileName, List<String> eventNames) {
}
