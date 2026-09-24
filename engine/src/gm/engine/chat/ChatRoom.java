package gm.engine.chat;

import gm.dto.ChatDto;
import gm.dto.ChatLineDto;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * The one chat room every logged-in user shares.
 * <p>
 * It has nothing to do with the market, which is why it stands apart from the engine interface: it is
 * the bonus of exercise 3, modelled on the course's own chat example. Messages are only ever appended,
 * so a client that remembers how many it holds asks for the rest and nothing more.
 * <p>
 * Safe to use from many threads, since every request to the server arrives on a thread of its own.
 */
public final class ChatRoom {

    /** Enough for a thought, not enough for an essay. */
    public static final int LONGEST_MESSAGE = 500;

    private final Clock clock;
    private final List<ChatLineDto> lines = new ArrayList<>();

    public ChatRoom() {
        this(Clock.systemUTC());
    }

    /** @param clock where the time stamps come from, which a test fixes so it can check them */
    public ChatRoom(Clock clock) {
        this.clock = clock;
    }

    /**
     * Adds a message to the room.
     *
     * @return the message as everybody will see it
     * @throws IllegalArgumentException if there is no author, no text, or too much of it
     */
    public synchronized ChatLineDto say(String userName, String text) {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("A message must say who wrote it.");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("There is nothing to send: the message is empty.");
        }
        String message = text.trim();
        if (message.length() > LONGEST_MESSAGE) {
            throw new IllegalArgumentException("A message can be at most " + LONGEST_MESSAGE
                    + " characters long, and this one is " + message.length() + ".");
        }
        ChatLineDto line = new ChatLineDto(lines.size() + 1, userName.trim(), message, clock.millis());
        lines.add(line);
        return line;
    }

    /**
     * Every message after the first {@code after}, oldest first.
     *
     * @throws IllegalArgumentException if {@code after} is negative
     */
    public synchronized ChatDto after(int after) {
        if (after < 0) {
            throw new IllegalArgumentException("The number of messages already held cannot be negative,"
                    + " but it is " + after + ".");
        }
        List<ChatLineDto> fresh = after >= lines.size() ? List.of() : lines.subList(after, lines.size());
        return new ChatDto(after, List.copyOf(fresh));
    }
}
