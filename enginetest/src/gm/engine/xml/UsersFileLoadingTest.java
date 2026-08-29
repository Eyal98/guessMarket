package gm.engine.xml;

import gm.engine.TestFiles;
import gm.engine.api.FileLoadException;
import gm.engine.model.Event;
import gm.engine.model.LmsrEvent;
import gm.engine.model.OrderBookEvent;
import gm.engine.model.SystemState;
import gm.engine.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading the exercise 2 file format: the users, their money, and which events each of them runs.
 * <p>
 * The official sample files are used directly, because they are what the work will be marked against.
 * Two of them are faulty on purpose and pin down exactly the checks this exercise adds.
 */
class UsersFileLoadingTest {

    private static final double TOLERANCE = 0.0001;

    private final EventsFileLoader loader = new EventsFileLoader();

    private static String official(String fileName) {
        return TestFiles.path("ex2/" + fileName);
    }

    @Test
    @DisplayName("small.xml brings in its users with the cash the file gave them")
    void usersArriveWithTheirCash() {
        SystemState state = loader.load(official("small.xml"));

        assertEquals(List.of("Avrum", "Tikva", "Menash"),
                state.users().stream().map(User::name).toList());
        assertEquals(1000.0, state.users().get(0).account().balance(), TOLERANCE);
        assertEquals(10000.0, state.users().get(1).account().balance(), TOLERANCE);
        assertEquals(100.0, state.users().get(2).account().balance(), TOLERANCE);
        assertTrue(state.users().stream().noneMatch(User::isBlocked));
    }

    @Test
    @DisplayName("Each event is handed to the user the file names as its market maker")
    void marketMakersAreWiredToTheirEvents() {
        SystemState state = loader.load(official("small.xml"));

        Event mujtaba = state.events().get(0);
        Event worldCup = state.events().get(1);
        assertEquals("Tikva", mujtaba.marketMaker().name());
        assertEquals("Avrum", worldCup.marketMaker().name());
        assertSame(state.users().get(1), mujtaba.marketMaker(),
                "the event must hold the very user the state holds, not a copy of them");
    }

    @Test
    @DisplayName("One user can run several events, and both trading methods load side by side")
    void oneUserCanRunSeveralEvents() {
        SystemState state = loader.load(official("multiple.xml"));

        assertEquals(4, state.events().size());
        assertEquals(List.of("Tikva", "Avrum", "Tikva", "Tikva"),
                state.events().stream().map(event -> event.marketMaker().name()).toList());
        assertInstanceOf(LmsrEvent.class, state.events().get(0));
        assertInstanceOf(OrderBookEvent.class, state.events().get(1));
        assertInstanceOf(OrderBookEvent.class, state.events().get(2));
        assertInstanceOf(LmsrEvent.class, state.events().get(3));
    }

    @Test
    @DisplayName("error-2.xml is refused because a user starts with no money at all")
    void aUserWithoutMoneyIsRefused() {
        FileLoadException failure = assertThrows(FileLoadException.class,
                () -> loader.load(official("error-2.xml")));

        String report = failure.getMessage();
        assertTrue(report.contains("Avrum"), report);
        assertTrue(report.contains("greater than 0"), report);
    }

    @Test
    @DisplayName("error-3.xml is refused twice over: a market maker for an event that does not exist, and an event left with none")
    void aMarketMakerPointingNowhereIsRefused() {
        FileLoadException failure = assertThrows(FileLoadException.class,
                () -> loader.load(official("error-3.xml")));

        assertEquals(2, failure.problems().size(), failure.getMessage());
        String report = failure.getMessage();
        assertTrue(report.contains("12"), report);
        assertTrue(report.contains("World Cap Winner"), report);
    }

    @Test
    @DisplayName("An exercise 1 file is refused for what it is, not for a list of missing pieces")
    void anExerciseOneFileIsRecognisedAsSuch() {
        FileLoadException failure = assertThrows(FileLoadException.class,
                () -> loader.load(TestFiles.path("ex1/single.xml")));

        assertTrue(failure.getMessage().contains("GM-users"), failure.getMessage());
    }
}
