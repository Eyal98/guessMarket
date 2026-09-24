package gm.dto;

import java.util.List;

/**
 * The chat messages after the ones the caller already has, fetched by delta like the account ledger.
 *
 * @param after the number of messages the caller said it already had
 * @param lines every message numbered above that, oldest first
 */
public record ChatDto(int after, List<ChatLineDto> lines) {
}
