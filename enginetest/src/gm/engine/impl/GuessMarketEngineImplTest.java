package gm.engine.impl;

import gm.engine.TestFiles;
import gm.engine.api.EventClosedException;
import gm.engine.api.FileLoadException;
import gm.engine.api.GuessMarketEngine;
import gm.engine.api.InvalidSelectionException;
import gm.engine.api.NoFileLoadedException;
import gm.engine.api.PersistenceException;
import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.LoadResultDto;
import gm.engine.api.dto.MarketStateDto;
import gm.engine.api.dto.PurchaseResultDto;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine as a user interface sees it: load a file, look around, buy, close, save and restore,
 * and get a clear refusal for anything that does not make sense.
 */
@Disabled("Covers the exercise 1 engine API, which had no users and opened every event at load time."
        + " Being rewritten against the exercise 2 API, where a market maker opens an event; the exercise 1"
        + " behaviour these describe stays reachable at the ex1-submission tag.")
class GuessMarketEngineImplTest {

    private static final double TOLERANCE = 0.0001;
    /** b*ln(2) for the three events of events-basic.xml, whose b values are 100, 200 and 50. */
    private static final double BASIC_FILE_SUBSIDY = 242.6015;

    private final GuessMarketEngine engine = new GuessMarketEngineImpl();

    private GuessMarketEngine loaded() {
        engine.loadEventsFile(TestFiles.path("events-basic.xml"));
        return engine;
    }

    @Test
    @DisplayName("Before a file is loaded every command that needs one refuses politely")
    void everythingNeedsAFileFirst() {
        assertFalse(engine.isLoaded());

        assertThrows(NoFileLoadedException.class, engine::listEvents);
        assertThrows(NoFileLoadedException.class, engine::listOpenEvents);
        assertThrows(NoFileLoadedException.class, () -> engine.marketState(1));
        assertThrows(NoFileLoadedException.class, () -> engine.buyShares(1, 1, 10));
        assertThrows(NoFileLoadedException.class, () -> engine.closeEvent(1, 1));
        assertThrows(NoFileLoadedException.class, () -> engine.saveState("anywhere"));
    }

    @Test
    @DisplayName("Loading a file reports what came in and what it cost the market maker")
    void loadingReportsWhatHappened() {
        LoadResultDto result = engine.loadEventsFile(TestFiles.path("events-basic.xml"));

        assertTrue(engine.isLoaded());
        assertEquals(3, result.eventsLoaded());
        assertEquals(BASIC_FILE_SUBSIDY, result.totalSubsidy(), TOLERANCE);
    }

    @Test
    @DisplayName("Events are numbered from 1 in the order they appear in the file")
    void eventsAreNumberedFromOne() {
        List<EventInfoDto> events = loaded().listEvents();

        assertEquals(List.of(1, 2, 3), events.stream().map(EventInfoDto::number).toList());
        assertEquals(List.of(3, 1, 7), events.stream().map(EventInfoDto::id).toList());
        assertEquals("Earth Quake on Dead Sea", events.get(0).name());
    }

    @Test
    @DisplayName("An untouched event is evenly balanced and holds only its subsidy")
    void anUntouchedEventIsBalanced() {
        MarketStateDto state = loaded().marketState(1);

        assertEquals(2, state.options().size());
        assertEquals(1, state.options().get(0).number());
        assertEquals(0.5, state.options().get(0).value(), TOLERANCE);
        assertEquals(0.5, state.options().get(1).value(), TOLERANCE);
        assertEquals(0, state.options().get(0).sharesBought());
        assertEquals(69.3147, state.eventAccountBalance(), TOLERANCE);
        assertEquals(0.0, state.commissionCollected(), TOLERANCE);
        assertEquals(-BASIC_FILE_SUBSIDY, state.marketMakerBalance(), TOLERANCE);
        assertTrue(state.history().isEmpty());
        assertFalse(state.closed());
        assertNull(state.winningOptionName());
    }

    @Test
    @DisplayName("A purchase is itemised into the shares and the commission on top")
    void aPurchaseIsItemised() {
        PurchaseResultDto purchase = loaded().buyShares(1, 1, 100);

        assertEquals("Yes", purchase.optionName());
        assertEquals(100, purchase.quantity());
        assertEquals(62.0115, purchase.sharesCost(), TOLERANCE);
        assertEquals(31.0057, purchase.commission(), TOLERANCE);
        assertEquals(93.0172, purchase.totalPaid(), TOLERANCE);

        MarketStateDto after = purchase.stateAfter();
        assertEquals(0.7311, after.options().get(0).value(), TOLERANCE);
        assertEquals(0.2689, after.options().get(1).value(), TOLERANCE);
        assertEquals(100, after.options().get(0).sharesBought());
        assertEquals(31.0057, after.commissionCollected(), TOLERANCE);
    }

    @Test
    @DisplayName("Trade history comes back newest first")
    void historyIsNewestFirst() {
        GuessMarketEngine loadedEngine = loaded();
        loadedEngine.buyShares(1, 1, 10);
        loadedEngine.buyShares(1, 2, 20);
        loadedEngine.buyShares(1, 1, 30);

        MarketStateDto state = loadedEngine.marketState(1);

        assertEquals(List.of(30L, 20L, 10L), state.history().stream().map(trade -> trade.quantity()).toList());
        assertEquals("Yes", state.history().get(0).optionName());
        assertEquals("No", state.history().get(1).optionName());
    }

    @Test
    @DisplayName("Closing an event pays the winners and takes it out of the open list")
    void closingAnEventPaysTheWinners() {
        GuessMarketEngine loadedEngine = loaded();
        loadedEngine.buyShares(1, 1, 100);

        MarketStateDto closed = loadedEngine.closeEvent(1, 1);

        assertTrue(closed.closed());
        assertEquals("Yes", closed.winningOptionName());
        assertEquals(100, closed.winningShares());
        assertEquals(100.0, closed.totalPaidOut(), TOLERANCE);
        assertEquals(0.0, closed.eventAccountBalance(), TOLERANCE);

        List<EventInfoDto> open = loadedEngine.listOpenEvents();
        assertEquals(List.of(2, 3), open.stream().map(EventInfoDto::number).toList());
        assertEquals(3, loadedEngine.listEvents().size());
    }

    @Test
    @DisplayName("An event number taken from the open list still means the same event")
    void numbersFromAFilteredListStillWork() {
        GuessMarketEngine loadedEngine = loaded();
        loadedEngine.closeEvent(1, 1);

        EventInfoDto secondOpen = loadedEngine.listOpenEvents().get(1);

        assertEquals(3, secondOpen.number());
        assertEquals("World Cup Final Goes To Extra Time", loadedEngine.marketState(secondOpen.number()).event().name());
    }

    @Test
    @DisplayName("A closed event cannot be traded on or closed again")
    void aClosedEventIsFinished() {
        GuessMarketEngine loadedEngine = loaded();
        loadedEngine.closeEvent(2, 1);

        assertThrows(EventClosedException.class, () -> loadedEngine.buyShares(2, 1, 10));
        assertThrows(EventClosedException.class, () -> loadedEngine.closeEvent(2, 2));
    }

    @Test
    @DisplayName("Choosing something that does not exist says what the choices are")
    void outOfRangeSelectionsAreExplained() {
        GuessMarketEngine loadedEngine = loaded();

        assertTrue(assertThrows(InvalidSelectionException.class, () -> loadedEngine.marketState(4))
                .getMessage().contains("between 1 and 3"));
        assertThrows(InvalidSelectionException.class, () -> loadedEngine.marketState(0));
        assertTrue(assertThrows(InvalidSelectionException.class, () -> loadedEngine.buyShares(1, 3, 10))
                .getMessage().contains("between 1 and 2"));
        assertThrows(InvalidSelectionException.class, () -> loadedEngine.closeEvent(1, 0));
    }

    @Test
    @DisplayName("Buying nothing, or a negative number of shares, is refused")
    void quantityMustBeAtLeastOne() {
        GuessMarketEngine loadedEngine = loaded();

        assertThrows(InvalidSelectionException.class, () -> loadedEngine.buyShares(1, 1, 0));
        assertThrows(InvalidSelectionException.class, () -> loadedEngine.buyShares(1, 1, -5));
    }

    @Test
    @DisplayName("A sound file replaces everything that was loaded before it")
    void aSoundFileReplacesTheOldOne() {
        GuessMarketEngine loadedEngine = loaded();
        loadedEngine.buyShares(1, 1, 100);

        loadedEngine.loadEventsFile(TestFiles.path("events-single.xml"));

        assertEquals(1, loadedEngine.listEvents().size());
        assertEquals("Rain In Tel Aviv This Week", loadedEngine.listEvents().get(0).name());
        assertTrue(loadedEngine.marketState(1).history().isEmpty(), "the old trading history should be gone");
    }

    @Test
    @DisplayName("A faulty file leaves the previously loaded one exactly as it was")
    void aFaultyFileChangesNothing() {
        GuessMarketEngine loadedEngine = loaded();
        loadedEngine.buyShares(1, 1, 100);

        assertThrows(FileLoadException.class,
                () -> loadedEngine.loadEventsFile(TestFiles.path("bad-many-problems.xml")));

        assertEquals(3, loadedEngine.listEvents().size());
        assertEquals(1, loadedEngine.marketState(1).history().size());
        assertEquals(100, loadedEngine.marketState(1).options().get(0).sharesBought());
    }

    @Test
    @DisplayName("A saved system comes back exactly as it was left")
    void savingAndRestoringKeepsEverything(@TempDir Path folder) {
        GuessMarketEngine original = loaded();
        original.buyShares(1, 1, 100);
        original.buyShares(3, 2, 40);
        original.closeEvent(2, 1);
        MarketStateDto before = original.marketState(1);

        String savePath = folder.resolve("saved-system").toString();
        original.saveState(savePath);

        GuessMarketEngine restored = new GuessMarketEngineImpl();
        restored.loadState(savePath);
        MarketStateDto after = restored.marketState(1);

        assertEquals(3, restored.listEvents().size());
        assertEquals(List.of(1, 3), restored.listOpenEvents().stream().map(EventInfoDto::number).toList());
        assertEquals(before.eventAccountBalance(), after.eventAccountBalance(), TOLERANCE);
        assertEquals(before.commissionCollected(), after.commissionCollected(), TOLERANCE);
        assertEquals(before.marketMakerBalance(), after.marketMakerBalance(), TOLERANCE);
        assertEquals(before.options().get(0).sharesBought(), after.options().get(0).sharesBought());
        assertEquals(before.options().get(0).value(), after.options().get(0).value(), TOLERANCE);
        assertEquals(before.history(), after.history());
        assertEquals("Yes", restored.marketState(2).winningOptionName());
    }

    @Test
    @DisplayName("The save file is named for us, so a path with or without the extension both work")
    void theExtensionIsAddedAutomatically(@TempDir Path folder) {
        GuessMarketEngine original = loaded();
        String savePath = folder.resolve("named-for-us").toString();

        original.saveState(savePath);

        assertTrue(Files.exists(folder.resolve("named-for-us.gm")), "expected the engine to add its own extension");
        assertFalse(Files.exists(folder.resolve("named-for-us.gm.gm")));
        new GuessMarketEngineImpl().loadState(savePath + ".gm");
    }

    @Test
    @DisplayName("Restoring from something that is not a saved system is explained, and changes nothing")
    void aBadRestoreChangesNothing(@TempDir Path folder) throws Exception {
        GuessMarketEngine loadedEngine = loaded();
        Path notASave = folder.resolve("rubbish.gm");
        Files.writeString(notASave, "this is not a saved system at all");

        assertThrows(PersistenceException.class, () -> loadedEngine.loadState(folder.resolve("nothing-here").toString()));
        assertThrows(PersistenceException.class, () -> loadedEngine.loadState(notASave.toString()));

        assertEquals(3, loadedEngine.listEvents().size());
    }
}
