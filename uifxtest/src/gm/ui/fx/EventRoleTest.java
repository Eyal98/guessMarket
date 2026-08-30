package gm.ui.fx;

import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.ParticipationDto;
import gm.ui.fx.UsersController.EventRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a user's standing in one event is called.
 * <p>
 * The fourth case is the one that matters most: somebody who has not taken part yet. Without a name
 * for it the event never appears beside that user, and there is then no way for them to place a
 * first order — which would leave only market makers and existing holders able to trade at all.
 */
@DisplayName("How a user's standing in an event is described")
class EventRoleTest {

    private EventInfoDto event() {
        return new EventInfoDto(1, 1, "World Cap Winner", "Who wins?", 15, "on-close",
                "charged from the winners when the event closes", List.of("Argentina", "Spain"),
                "Active", "Order book", "Order book", "Avrum", 100.0, null);
    }

    private ParticipationDto someHolding() {
        return new ParticipationDto(event(), List.of(), 0.0, 0.0, List.of());
    }

    @Test
    @DisplayName("Somebody who has neither run it nor traded in it is named as such")
    void aStrangerToTheEventIsNamed() {
        assertEquals("Not taken part yet", new EventRole(event(), false, null).role(),
                "an event with no name for this state cannot be offered to somebody wanting to join it");
    }

    @Test
    @DisplayName("Somebody who has only traded is trading")
    void aTraderIsTrading() {
        assertEquals("Trading", new EventRole(event(), false, someHolding()).role());
    }

    @Test
    @DisplayName("The market maker who has not traded is only the market maker")
    void theMarketMakerAloneIsNamed() {
        assertEquals("Market maker", new EventRole(event(), true, null).role());
    }

    @Test
    @DisplayName("A market maker who also trades is both")
    void aMarketMakerWhoTradesIsBoth() {
        assertEquals("Market maker, trading", new EventRole(event(), true, someHolding()).role());
    }
}
