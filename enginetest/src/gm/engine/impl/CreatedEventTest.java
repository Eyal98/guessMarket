package gm.engine.impl;

import gm.engine.TestFiles;
import gm.engine.api.GuessMarketEngine;
import gm.engine.api.InvalidSelectionException;
import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.NewEventDto;
import gm.engine.api.dto.NewLmsrDto;
import gm.engine.api.dto.NewOrderBookDto;
import gm.engine.api.dto.OrderBookStateDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Events made from the screen rather than read from a file.
 * <p>
 * A created event is not a lesser kind of event. It goes into the same list, is numbered the same
 * way, and is opened, traded and closed through the same calls — the only thing that distinguishes
 * it is that its market maker is whoever filled in the form.
 */
@DisplayName("Creating an event from the screen")
class CreatedEventTest {

    private static final double TOLERANCE = 0.0001;
    private static final int AVRUM = 1;
    private static final int TIKVA = 2;

    private final GuessMarketEngine engine = new GuessMarketEngineImpl();

    private GuessMarketEngine loaded() {
        engine.loadEventsFile(TestFiles.path("ex2/small.xml"));
        return engine;
    }

    private NewEventDto lmsrForm() {
        return new NewEventDto("Rain on Tuesday", "Will it rain?", 5, "on-purchase",
                List.of("It will", "It will not"), new NewLmsrDto(50));
    }

    private NewEventDto bookForm() {
        return new NewEventDto("Who bakes best", "The office contest", 10, "on-close",
                List.of("Dana", "Yossi"), new NewOrderBookDto(60, 1, true));
    }

    @Test
    @DisplayName("A created event joins the list, run by whoever created it, waiting to be opened")
    void aCreatedEventJoinsTheList() {
        int number = loaded().createEvent(TIKVA, lmsrForm());

        assertEquals(3, number, "it goes on the end, after the two the file brought");
        List<EventInfoDto> events = engine.listEvents();
        assertEquals(3, events.size());

        EventInfoDto created = events.get(2);
        assertEquals("Rain on Tuesday", created.name());
        assertEquals("Tikva", created.marketMakerName());
        assertEquals("Not started", created.status());
        assertEquals("LMSR", created.methodKind());
        assertEquals(List.of("It will", "It will not"), created.optionNames());
        assertEquals("on-purchase", created.commissionType());
    }

    @Test
    @DisplayName("A created event opens, trades and closes like any other")
    void aCreatedEventBehavesLikeAnyOther() {
        int number = loaded().createEvent(TIKVA, lmsrForm());

        engine.openEvent(number, TIKVA);
        engine.buyShares(number, AVRUM, 1, 10);

        assertEquals("Active", engine.listEvents().get(number - 1).status());
        assertTrue(engine.marketState(number).options().get(0).value() > 0.5,
                "buying should have moved the price on a created event exactly as on a loaded one");
    }

    @Test
    @DisplayName("An order book made from the form keeps the numbers it was given")
    void anOrderBookKeepsItsNumbers() {
        int number = loaded().createEvent(AVRUM, bookForm());
        engine.openEvent(number, AVRUM);

        OrderBookStateDto state = engine.orderBookState(number);

        assertEquals(1, state.baseValue());
        assertTrue(state.mintAllowed());
        assertEquals(60, state.options().get(0).sharesInIssue(),
                "sixty pounds at a base value of one buys sixty pairs");
        assertEquals(940.0, engine.userDetail(AVRUM).balance(), TOLERANCE,
                "Avrum began with 1000 and has just spent 60 stocking his own market");
    }

    @Test
    @DisplayName("An event with no name is refused")
    void anEventWithNoNameIsRefused() {
        loaded();
        NewEventDto blank = new NewEventDto("   ", "Something", 5, "on-purchase",
                List.of("Yes", "No"), new NewLmsrDto(50));

        assertThrows(InvalidSelectionException.class, () -> engine.createEvent(TIKVA, blank));
    }

    @Test
    @DisplayName("An option with no name is refused, since nobody could choose it")
    void anOptionWithNoNameIsRefused() {
        loaded();
        NewEventDto blankOption = new NewEventDto("Rain", "Will it?", 5, "on-purchase",
                Arrays.asList("It will", "  "), new NewLmsrDto(50));

        assertThrows(InvalidSelectionException.class, () -> engine.createEvent(TIKVA, blankOption));
    }

    @Test
    @DisplayName("An event with fewer than two outcomes is refused")
    void oneOutcomeIsNotAnEvent() {
        loaded();
        NewEventDto onlyOne = new NewEventDto("Rain", "Will it?", 5, "on-purchase",
                List.of("It will"), new NewLmsrDto(50));

        assertThrows(InvalidSelectionException.class, () -> engine.createEvent(TIKVA, onlyOne));
    }

    @Test
    @DisplayName("An impossible commission is refused rather than quietly clamped")
    void anImpossibleCommissionIsRefused() {
        loaded();
        NewEventDto tooMuch = new NewEventDto("Rain", "Will it?", 200, "on-purchase",
                List.of("Yes", "No"), new NewLmsrDto(50));

        assertThrows(InvalidSelectionException.class, () -> engine.createEvent(TIKVA, tooMuch));
    }

    @Test
    @DisplayName("A commission nobody has heard of is refused by name")
    void anUnknownCommissionTimingIsRefused() {
        loaded();
        NewEventDto odd = new NewEventDto("Rain", "Will it?", 5, "on-tuesdays",
                List.of("Yes", "No"), new NewLmsrDto(50));

        InvalidSelectionException refused =
                assertThrows(InvalidSelectionException.class, () -> engine.createEvent(TIKVA, odd));
        assertTrue(refused.getMessage().contains("on-tuesdays"),
                "the message should quote what was actually asked for, but it said: "
                        + refused.getMessage());
    }

    @Test
    @DisplayName("A liquidity of nothing is refused, since the formula cannot divide by it")
    void aLiquidityOfNothingIsRefused() {
        loaded();
        NewEventDto noLiquidity = new NewEventDto("Rain", "Will it?", 5, "on-purchase",
                List.of("Yes", "No"), new NewLmsrDto(0));

        assertThrows(InvalidSelectionException.class, () -> engine.createEvent(TIKVA, noLiquidity));
    }

    @Test
    @DisplayName("Somebody who is not there cannot create anything")
    void aStrangerCannotCreateAnything() {
        loaded();
        assertThrows(InvalidSelectionException.class, () -> engine.createEvent(99, lmsrForm()));
    }

    @Test
    @DisplayName("Creating an event leaves everything already loaded exactly as it was")
    void creatingDisturbsNothing() {
        loaded();
        engine.openEvent(1, TIKVA);
        double before = engine.userDetail(TIKVA).balance();

        engine.createEvent(TIKVA, lmsrForm());

        assertEquals(before, engine.userDetail(TIKVA).balance(), TOLERANCE,
                "creating an event costs nothing; it is opening one that costs");
        assertEquals("Mujtaba is Dead", engine.listEvents().get(0).name());
        assertEquals("Active", engine.listEvents().get(0).status());
    }
}
