package gm.engine;

import gm.engine.api.FileLoadException;
import gm.engine.api.GuessMarketEngine;
import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.LoadResultDto;
import gm.engine.impl.GuessMarketEngineImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The exercise 1 sample files published with the course, exactly as they were handed out.
 * <p>
 * These are the files the work will actually be marked against, so they are pinned here rather than
 * only tried by hand: the two sound ones must load with the right contents, and each faulty one must
 * be refused for the right reason and leave whatever was already loaded untouched.
 */
class OfficialFilesTest {

    private static final double TOLERANCE = 0.005;

    private final GuessMarketEngine engine = new GuessMarketEngineImpl();

    private static String official(String fileName) {
        return TestFiles.path("ex1/" + fileName);
    }

    @Test
    @DisplayName("single.xml loads its one event with every detail intact")
    void singleLoads() {
        LoadResultDto result = engine.loadEventsFile(official("single.xml"));

        assertEquals(1, result.eventsLoaded());
        assertEquals(69.31, result.totalSubsidy(), TOLERANCE);

        EventInfoDto event = engine.listEvents().get(0);
        assertEquals(1, event.number());
        assertEquals(3, event.id());
        assertEquals("Earth Quake on Dead Sea", event.name());
        assertEquals(50, event.commissionPercent());
        assertEquals("on-purchase", event.commissionType());
        assertEquals(List.of("Yes", "No"), event.optionNames());
        assertEquals("LMSR (b=100)", event.tradingMethod());
        assertEquals("Not started", event.status());
    }

    @Test
    @DisplayName("multiple.xml loads all three events and their subsidies add up")
    void multipleLoads() {
        LoadResultDto result = engine.loadEventsFile(official("multiple.xml"));

        assertEquals(3, result.eventsLoaded());
        // b of 100, 50 and 400, each subsidised by b * ln(2).
        assertEquals(381.23, result.totalSubsidy(), TOLERANCE);

        List<EventInfoDto> events = engine.listEvents();
        assertEquals(List.of(1, 2, 3), events.stream().map(EventInfoDto::id).toList());
        assertEquals("Mujtaba is Dead", events.get(0).name());
        assertEquals(List.of("Argentina", "Spain"), events.get(1).optionNames());
        assertEquals("on-close", events.get(1).commissionType());
        assertEquals(15, events.get(1).commissionPercent());
    }

    @Test
    @DisplayName("error-2.xml is refused for the repeated id, naming both events")
    void errorTwoIsRefusedForARepeatedId() {
        FileLoadException failure = assertThrows(FileLoadException.class,
                () -> engine.loadEventsFile(official("error-2.xml")));

        String report = failure.getMessage();
        assertTrue(report.contains("Earth Quake on Dead Sea"), report);
        assertTrue(report.contains("Mujtaba is Dead"), report);
        assertTrue(report.contains("its id is 1"), report);
    }

    @Test
    @DisplayName("error-3.xml is refused for the commission of 115")
    void errorThreeIsRefusedForAnImpossibleCommission() {
        FileLoadException failure = assertThrows(FileLoadException.class,
                () -> engine.loadEventsFile(official("error-3.xml")));

        String report = failure.getMessage();
        assertTrue(report.contains("World Cap Winner"), report);
        assertTrue(report.contains("115"), report);
        assertTrue(report.contains("between 0 and 90"), report);
    }
}
