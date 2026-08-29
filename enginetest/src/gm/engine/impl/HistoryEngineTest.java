package gm.engine.impl;

import gm.engine.TestFiles;
import gm.engine.api.GuessMarketEngine;
import gm.engine.api.InvalidSelectionException;
import gm.engine.api.dto.BalanceHistoryDto;
import gm.engine.api.dto.PriceHistoryDto;
import gm.engine.model.orderbook.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The past as the screen will see it, which is what the two charts are drawn from.
 * <p>
 * Both come across as plain numbers and names. Nothing of the model reaches the screen, so the chart
 * cannot reach back through a sample and change the market it is drawing.
 */
@DisplayName("The history the charts are drawn from")
class HistoryEngineTest {

    private static final double TOLERANCE = 0.0001;
    private static final int MUJTABA = 1;
    private static final int WORLD_CUP = 2;
    private static final int HELL_YEA = 1;
    private static final int ARGENTINA = 1;
    private static final int AVRUM = 1;
    private static final int TIKVA = 2;

    private final GuessMarketEngine engine = new GuessMarketEngineImpl();

    private GuessMarketEngine loaded() {
        engine.loadEventsFile(TestFiles.path("ex2/small.xml"));
        return engine;
    }

    @Test
    @DisplayName("An event's chart names its options and starts where the market opened")
    void anEventsChartStartsWhereTheMarketOpened() {
        loaded().openEvent(MUJTABA, TIKVA);

        PriceHistoryDto history = engine.priceHistory(MUJTABA);

        assertEquals("Mujtaba is Dead", history.eventName());
        assertEquals(java.util.List.of("Hell Yea !", "No way !"), history.optionNames());
        assertEquals(1, history.points().size());
        assertEquals(0.5, history.points().get(0).pricePerOption().get(0), TOLERANCE);
    }

    @Test
    @DisplayName("Buying moves the line, and the chart has a point for every trade")
    void buyingMovesTheLine() {
        loaded().openEvent(MUJTABA, TIKVA);
        engine.buyShares(MUJTABA, TIKVA, HELL_YEA, 40);
        engine.buyShares(MUJTABA, TIKVA, HELL_YEA, 40);

        PriceHistoryDto history = engine.priceHistory(MUJTABA);

        assertEquals(3, history.points().size(), "where it opened, and after each of the two buys");
        double first = history.points().get(1).pricePerOption().get(0);
        double second = history.points().get(2).pricePerOption().get(0);
        assertTrue(second > first, "the second purchase should have pushed it higher still");
    }

    @Test
    @DisplayName("An untraded order book option leaves a gap rather than claiming a price of nought")
    void anUntradedOptionLeavesAGap() {
        loaded().openEvent(WORLD_CUP, AVRUM);

        PriceHistoryDto history = engine.priceHistory(WORLD_CUP);

        assertNull(history.points().get(0).pricePerOption().get(0));
    }

    @Test
    @DisplayName("An order book draws the price two people actually agreed on")
    void anOrderBookDrawsTheAgreedPrice() {
        loaded().openEvent(WORLD_CUP, AVRUM);
        engine.submitOrder(WORLD_CUP, AVRUM, ARGENTINA, OrderSide.SELL, 20, 0.60);
        engine.submitOrder(WORLD_CUP, TIKVA, ARGENTINA, OrderSide.BUY, 20, 0.60);

        PriceHistoryDto history = engine.priceHistory(WORLD_CUP);

        var latest = history.points().get(history.points().size() - 1);
        assertEquals(0.60, latest.pricePerOption().get(0), TOLERANCE);
    }

    @Test
    @DisplayName("A user's chart starts at the cash the file gave them")
    void aUsersChartStartsAtTheirOpeningCash() {
        BalanceHistoryDto history = loaded().balanceHistory(TIKVA);

        assertEquals("Tikva", history.userName());
        assertEquals(10_000.0, history.points().get(0).balance(), TOLERANCE);
    }

    @Test
    @DisplayName("Opening an event and trading both show up on the payer's chart, going down")
    void spendingShowsUpOnTheChart() {
        loaded().openEvent(MUJTABA, TIKVA);
        engine.buyShares(MUJTABA, TIKVA, HELL_YEA, 40);

        BalanceHistoryDto history = engine.balanceHistory(TIKVA);

        double opening = history.points().get(0).balance();
        double now = history.points().get(history.points().size() - 1).balance();
        assertTrue(now < opening,
                "Tikva funded the event and then bought shares, so she must be poorer than she began");
        assertTrue(history.points().size() >= 3, "the opening cash, funding the event, and the purchase");
    }

    @Test
    @DisplayName("Asking about an event or a user that is not there is refused politely")
    void nonsenseIsRefusedPolitely() {
        loaded();
        assertThrows(InvalidSelectionException.class, () -> engine.priceHistory(99));
        assertThrows(InvalidSelectionException.class, () -> engine.balanceHistory(99));
    }
}
