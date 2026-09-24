package gm.engine.chat;

import gm.dto.ChatDto;
import gm.dto.ChatLineDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chat room of the bonus: everybody sees everything anybody writes, and a client that already
 * holds some of the conversation asks only for what came after it.
 */
class ChatRoomTest {

    private static final Instant NOON = Instant.parse("2026-10-01T12:00:00Z");

    private final ChatRoom room = new ChatRoom(Clock.fixed(NOON, ZoneOffset.UTC));

    @Test
    @DisplayName("A message is numbered, signed, stamped, and trimmed of surrounding spaces")
    void aMessageIsRecorded() {
        ChatLineDto line = room.say("Avrum", "  Who is buying Spain?  ");

        assertEquals(new ChatLineDto(1, "Avrum", "Who is buying Spain?", NOON.toEpochMilli()), line);
    }

    @Test
    @DisplayName("Only the messages after the ones the caller holds come back")
    void theConversationIsFetchedByDelta() {
        room.say("Avrum", "one");
        room.say("Tikva", "two");
        room.say("Avrum", "three");

        ChatDto rest = room.after(1);

        assertEquals(1, rest.after());
        assertEquals(List.of("two", "three"), rest.lines().stream().map(ChatLineDto::text).toList());
        assertTrue(room.after(3).lines().isEmpty());
        assertTrue(room.after(50).lines().isEmpty());
    }

    @Test
    @DisplayName("An empty message, or one without an author, is refused")
    void emptyMessagesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> room.say("Avrum", "   "));
        assertThrows(IllegalArgumentException.class, () -> room.say("Avrum", null));
        assertThrows(IllegalArgumentException.class, () -> room.say(" ", "hello"));
        assertTrue(room.after(0).lines().isEmpty());
    }

    @Test
    @DisplayName("A message longer than the room allows is refused rather than cut")
    void aNovelIsRefused() {
        String tooLong = "x".repeat(ChatRoom.LONGEST_MESSAGE + 1);

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> room.say("Avrum", tooLong));

        assertTrue(refusal.getMessage().contains(String.valueOf(ChatRoom.LONGEST_MESSAGE)));
    }

    @Test
    @DisplayName("Asking from a negative position is a mistake, not a request for everything")
    void aNegativePositionIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> room.after(-1));
    }
}
