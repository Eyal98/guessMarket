package gm.dto;

import java.util.List;

/**
 * Why the server refused a request, in words fit to show the person who made it.
 *
 * @param message  the whole explanation
 * @param problems for a refused events file, each fault on its own; otherwise empty
 */
public record ErrorDto(String message, List<String> problems) {
}
