package gm.engine.impl;

import gm.engine.TestFiles;
import gm.engine.api.GuessMarketEngine;
import gm.engine.api.NoFileLoadedException;
import gm.engine.api.PersistenceException;
import gm.engine.api.dto.BalanceHistoryDto;
import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.NewEventDto;
import gm.engine.api.dto.NewLmsrDto;
import gm.engine.api.dto.OrderBookStateDto;
import gm.engine.api.dto.PriceHistoryDto;
import gm.engine.api.dto.UserDetailDto;
import gm.engine.model.orderbook.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Putting the whole market away and getting it back.
 * <p>
 * Everything in the system has to survive the trip, not merely the parts that are easy: who is
 * blocked, what everyone holds, the orders still waiting in a book, and the two histories the charts
 * are drawn from. A save that quietly loses the past would leave a restored market looking perfectly
 * healthy while every chart on the screen began from nowhere.
 * <p>
 * These are run against a fresh engine rather than the one that saved, because an engine that still
 * holds the state in memory would pass without proving anything about the file.
 */
@DisplayName("Saving the market and getting it back")
class SavedStateTest {

    private static final double TOLERANCE = 0.0001;
    private static final int MUJTABA = 1;
    private static final int WORLD_CUP = 2;
    private static final int HELL_YEA = 1;
    private static final int ARGENTINA = 1;
    private static final int AVRUM = 1;
    private static final int TIKVA = 2;
    private static final int MENASH = 3;

    private final GuessMarketEngine engine = new GuessMarketEngineImpl();

    /** A market with something of everything in it, so the round trip has something to lose. */
    private void aBusyMarket() {
        engine.loadEventsFile(TestFiles.path("ex2/small.xml"));
        engine.openEvent(MUJTABA, TIKVA);
        engine.openEvent(WORLD_CUP, AVRUM);
        engine.buyShares(MUJTABA, MENASH, HELL_YEA, 30);
        engine.submitOrder(WORLD_CUP, AVRUM, ARGENTINA, OrderSide.SELL, 25, 0.58);
        engine.submitOrder(WORLD_CUP, TIKVA, ARGENTINA, OrderSide.BUY, 10, 0.58);
        engine.submitOrder(WORLD_CUP, TIKVA, ARGENTINA, OrderSide.BUY, 20, 0.40);
        engine.createEvent(TIKVA, new NewEventDto("Rain on Tuesday", "Will it rain?", 5,
                "on-purchase", List.of("It will", "It will not"), new NewLmsrDto(50)));
    }

    private GuessMarketEngine restored(Path folder) {
        aBusyMarket();
        String written = engine.saveState(folder.resolve("market").toString());
        assertTrue(Files.exists(Path.of(written)), "saveState claimed to write " + written);

        GuessMarketEngine reopened = new GuessMarketEngineImpl();
        reopened.loadState(folder.resolve("market").toString());
        return reopened;
    }

    @Test
    @DisplayName("Every event comes back, including one that was made rather than loaded")
    void everyEventComesBack(@TempDir Path folder) {
        List<EventInfoDto> events = restored(folder).listEvents();

        assertEquals(3, events.size());
        assertEquals("Mujtaba is Dead", events.get(0).name());
        assertEquals("Active", events.get(0).status());
        assertEquals("Rain on Tuesday", events.get(2).name());
        assertEquals("Not started", events.get(2).status());
        assertEquals("Tikva", events.get(2).marketMakerName(),
                "a created event has to remember who created it");
    }

    @Test
    @DisplayName("Money comes back to the penny")
    void moneyComesBackToThePenny(@TempDir Path folder) {
        aBusyMarket();
        double tikvaWas = engine.userDetail(TIKVA).balance();
        double menashWas = engine.userDetail(MENASH).balance();

        String written = engine.saveState(folder.resolve("market").toString());
        GuessMarketEngine after = new GuessMarketEngineImpl();
        after.loadState(written.substring(0, written.lastIndexOf('.')));

        assertEquals(tikvaWas, after.userDetail(TIKVA).balance(), TOLERANCE);
        assertEquals(menashWas, after.userDetail(MENASH).balance(), TOLERANCE);
    }

    @Test
    @DisplayName("What people hold comes back, and so does who runs what")
    void holdingsComeBack(@TempDir Path folder) {
        UserDetailDto tikva = restored(folder).userDetail(TIKVA);

        assertTrue(tikva.marketMakerOf().contains("Mujtaba is Dead"));
        assertTrue(tikva.marketMakerOf().contains("Rain on Tuesday"));
        assertTrue(tikva.participations().stream()
                        .anyMatch(part -> part.event().name().equals("World Cap Winner")),
                "Tikva traded on the World Cup, so she must come back as a participant in it");
    }

    @Test
    @DisplayName("Orders still waiting in the book are still waiting after the trip")
    void restingOrdersComeBack(@TempDir Path folder) {
        OrderBookStateDto book = restored(folder).orderBookState(WORLD_CUP);

        assertEquals(0.58, book.options().get(0).lastPrice(), TOLERANCE,
                "the price the two of them agreed on has to survive");
        assertEquals(1, book.options().get(0).bids().size(),
                "Tikva's unfilled bid at 0.40 was still resting when the market was put away");
        assertEquals(0.40, book.options().get(0).bids().get(0).price(), TOLERANCE);
        assertEquals(15, book.options().get(0).asks().get(0).quantity(),
                "Avrum offered 25 and 10 were taken, so 15 should still be on offer");
    }

    @Test
    @DisplayName("The past the charts are drawn from survives, which is the easiest thing to lose")
    void theChartsKeepTheirPast(@TempDir Path folder) {
        GuessMarketEngine reopened = restored(folder);

        PriceHistoryDto prices = reopened.priceHistory(MUJTABA);
        assertEquals(2, prices.points().size(), "where it opened, and after Menash bought");
        assertTrue(prices.points().get(1).pricePerOption().get(0) > 0.5);

        BalanceHistoryDto money = reopened.balanceHistory(TIKVA);
        assertTrue(money.points().size() > 1, "Tikva funded an event and traded, so her line moved");
        assertEquals(10_000.0, money.points().get(0).balance(), TOLERANCE,
                "and it still begins at the cash the file gave her");
    }

    @Test
    @DisplayName("A market can be traded on again once it has been brought back")
    void aRestoredMarketStillWorks(@TempDir Path folder) {
        GuessMarketEngine reopened = restored(folder);

        reopened.buyShares(MUJTABA, MENASH, HELL_YEA, 5);

        assertEquals(3, reopened.priceHistory(MUJTABA).points().size(),
                "the new trade should carry on from the history that came back, not start a fresh one");
    }

    @Test
    @DisplayName("Saving nothing is refused, and so is reading a file that is not one of ours")
    void nonsenseIsRefused(@TempDir Path folder) {
        assertThrows(NoFileLoadedException.class,
                () -> engine.saveState(folder.resolve("nothing").toString()));

        assertThrows(PersistenceException.class,
                () -> engine.loadState(folder.resolve("was-never-written").toString()));
    }

    @Test
    @DisplayName("A failed restore leaves what was already loaded untouched")
    void aFailedRestoreChangesNothing(@TempDir Path folder) {
        aBusyMarket();
        double was = engine.userDetail(TIKVA).balance();

        assertThrows(PersistenceException.class,
                () -> engine.loadState(folder.resolve("was-never-written").toString()));

        assertEquals(3, engine.listEvents().size(), "the market that was loaded is still loaded");
        assertEquals(was, engine.userDetail(TIKVA).balance(), TOLERANCE);
    }
}
