package gm.dto;

import java.util.List;

/**
 * The lines of a user's account that came after the ones the caller already has.
 * <p>
 * An account only ever grows at the end, so a client that remembers how many lines it holds can ask
 * for just the rest: the delta fetching the course describes, rather than the whole history every
 * half second.
 *
 * @param after the number of lines the caller said it already had
 * @param lines every line numbered above that, oldest first; empty when nothing has happened since
 */
public record LedgerDto(int after, List<LedgerLineDto> lines) {
}
