package gm.dto;

/**
 * One message in the chat room.
 *
 * @param number   counted from 1, in the order messages arrived
 * @param userName who wrote it
 * @param text     what they wrote
 * @param sentAt   when the server received it, in milliseconds since the epoch
 */
public record ChatLineDto(int number, String userName, String text, long sentAt) {
}
