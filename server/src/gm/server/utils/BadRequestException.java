package gm.server.utils;

import java.io.Serial;

/**
 * A request that is malformed before it ever reaches the market: a parameter missing, or not a
 * number. The message says which, so whoever sent it, a client or a person in Postman, can put it
 * right.
 */
public final class BadRequestException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(message);
    }
}
