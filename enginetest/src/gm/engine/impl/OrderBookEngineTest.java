package gm.engine.impl;

import gm.engine.TestFiles;
import gm.engine.api.GuessMarketEngine;
import gm.engine.api.InvalidSelectionException;
import gm.engine.api.dto.OptionMarketDto;
import gm.engine.api.dto.OrderBookStateDto;
import gm.engine.api.dto.ParticipantDto;
import gm.engine.api.dto.TradeDto;
import gm.engine.model.orderbook.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The order book as the screen will see it: both books, what they say about price, and where each
 * participant stands.
 * <p>
 * Event 2 of the official small.xml is the order book: base value 1, an initial stock of 100, minting
 * allowed, and Avrum running it.
 */
class OrderBookEngineTest {

    private static final double TOLERANCE = 0.0001;
    private static final int MUJTABA = 1;
    private static final int WORLD_CUP = 2;
    private static final int ARGENTINA = 1;
    private static final int SPAIN = 2;
    private static final int AVRUM = 1;
    private static final int TIKVA = 2;

    private final GuessMarketEngine engine = new GuessMarketEngineImpl();

    private GuessMarketEngine openMarket() {
        engine.loadEventsFile(TestFiles.path("ex2/small.xml"));
        engine.openEvent(WORLD_CUP, AVRUM);
        return engine;
    }

    @Test
    @DisplayName("A participant's holding is worth what the market last paid for it")
    void aHoldingIsWorthWhatTheMarketLastPaid() {
        openMarket();
        engine.submitOrder(WORLD_CUP, AVRUM, ARGENTINA, OrderSide.SELL, 20, 0.60);

        ParticipantDto avrumBefore = engine.orderBookState(WORLD_CUP).participants().get(0);
        assertNull(avrumBefore.options().get(0).currentValue(),
                "nothing has changed hands, so there is no price to value a holding at");

        engine.submitOrder(WORLD_CUP, TIKVA, ARGENTINA, OrderSide.BUY, 20, 0.60);

        ParticipantDto tikva = engine.orderBookState(WORLD_CUP).participants().stream()
                .filter(who -> who.userName().equals("Tikva")).findFirst().orElseThrow();
        assertEquals(12.0, tikva.options().get(0).currentValue(), TOLERANCE,
                "twenty shares last traded at 0.60 are worth twelve, which is not what she paid in fees");
    }

    @Test
    @DisplayName("A freshly opened book has the market maker's stock but no orders and no prices")
    void aFreshBookQuotesNothing() {
        OrderBookStateDto state = openMarket().orderBookState(WORLD_CUP);

        assertEquals(1, state.baseValue());
        assertTrue(state.mintAllowed());
        assertEquals(0.99, state.highestAllowedPrice(), TOLERANCE);

        OptionMarketDto argentina = state.options().get(0);
        assertEquals("Argentina", argentina.name());
        assertEquals(100, argentina.sharesInIssue(), "the market maker's opening stock exists");
        assertTrue(argentina.bids().isEmpty());
        assertNull(argentina.bestBid(), "an absent price is absent, not nought");
        assertNull(argentina.spread());
        assertNull(argentina.lastPrice());
    }

    @Test
    @DisplayName("An order that rests shows up in the book, named and priced")
    void arestingOrderIsVisible() {
        GuessMarketEngine market = openMarket();

        List<TradeDto> trades = market.submitOrder(WORLD_CUP, TIKVA, ARGENTINA, OrderSide.BUY, 20, 0.50);

        assertTrue(trades.isEmpty(), "nothing to match against yet");
        OptionMarketDto argentina = market.orderBookState(WORLD_CUP).options().get(0);
        assertEquals(1, argentina.bids().size());
        assertEquals("Tikva", argentina.bids().get(0).userName());
        assertEquals("Buy", argentina.bids().get(0).side());
        assertEquals(20, argentina.bids().get(0).quantity());
        assertEquals(0.50, argentina.bestBid(), TOLERANCE);
    }

    @Test
    @DisplayName("Both sides present give a spread and a mid-price")
    void bothSidesGiveASpread() {
        GuessMarketEngine market = openMarket();
        market.submitOrder(WORLD_CUP, TIKVA, ARGENTINA, OrderSide.BUY, 20, 0.50);
        market.submitOrder(WORLD_CUP, AVRUM, ARGENTINA, OrderSide.SELL, 25, 0.58);

        OptionMarketDto argentina = market.orderBookState(WORLD_CUP).options().get(0);

        assertEquals(0.08, argentina.spread(), TOLERANCE);
        assertEquals(0.54, argentina.midPrice(), TOLERANCE);
    }

    @Test
    @DisplayName("A crossing order trades, and the trade comes back to the caller")
    void acrossingOrderTrades() {
        GuessMarketEngine market = openMarket();
        market.submitOrder(WORLD_CUP, AVRUM, ARGENTINA, OrderSide.SELL, 25, 0.58);

        List<TradeDto> trades = market.submitOrder(WORLD_CUP, TIKVA, ARGENTINA, OrderSide.BUY, 25, 0.58);

        assertEquals(1, trades.size());
        assertEquals("Argentina", trades.get(0).optionName());
        assertEquals(25, trades.get(0).quantity());
        assertEquals(14.50, trades.get(0).sharesCost(), TOLERANCE);
        assertEquals(0.58, market.orderBookState(WORLD_CUP).options().get(0).lastPrice(), TOLERANCE);
    }

    @Test
    @DisplayName("A mint between opposing buyers comes back as both halves")
    void amintComesBackAsBothHalves() {
        GuessMarketEngine market = openMarket();
        market.submitOrder(WORLD_CUP, TIKVA, SPAIN, OrderSide.BUY, 35, 0.42);

        List<TradeDto> trades = market.submitOrder(WORLD_CUP, AVRUM, ARGENTINA, OrderSide.BUY, 40, 0.62);

        assertEquals(2, trades.size(), "a mint is two purchases at once");
        assertEquals("Spain", trades.get(0).optionName());
        assertEquals(35 * 0.42, trades.get(0).sharesCost(), TOLERANCE);
        assertEquals("Argentina", trades.get(1).optionName());
        assertEquals(35 * 0.58, trades.get(1).sharesCost(), TOLERANCE);
    }

    @Test
    @DisplayName("Everyone who has acted on the event appears with what they hold")
    void participantsAreListedWithTheirHoldings() {
        GuessMarketEngine market = openMarket();
        market.submitOrder(WORLD_CUP, AVRUM, ARGENTINA, OrderSide.SELL, 25, 0.58);
        market.submitOrder(WORLD_CUP, TIKVA, ARGENTINA, OrderSide.BUY, 25, 0.58);

        List<ParticipantDto> participants = market.orderBookState(WORLD_CUP).participants();

        assertEquals(List.of("Avrum", "Tikva"), participants.stream().map(ParticipantDto::userName).toList());
        assertEquals(75, participants.get(0).options().get(0).shares(), "Avrum sold 25 of his 100");
        assertEquals(25, participants.get(1).options().get(0).shares());
        assertNotNull(participants.get(0).options().get(1));
    }

    @Test
    @DisplayName("A price at or above the base value is refused before it reaches the book")
    void anImpossiblePriceIsRefused() {
        GuessMarketEngine market = openMarket();

        assertThrows(InvalidSelectionException.class,
                () -> market.submitOrder(WORLD_CUP, TIKVA, ARGENTINA, OrderSide.BUY, 10, 1.05));

        assertTrue(market.orderBookState(WORLD_CUP).options().get(0).bids().isEmpty());
    }

    @Test
    @DisplayName("Asking an LMSR event for its books says so plainly")
    void anLmsrEventHasNoBooks() {
        GuessMarketEngine market = openMarket();

        assertTrue(assertThrows(InvalidSelectionException.class, () -> market.orderBookState(MUJTABA))
                .getMessage().contains("formula"));
    }

    @Test
    @DisplayName("Trading on an order book event through the LMSR call says so plainly")
    void anOrderBookEventCannotBeBoughtFromDirectly() {
        GuessMarketEngine market = openMarket();

        assertTrue(assertThrows(InvalidSelectionException.class,
                () -> market.buyShares(WORLD_CUP, TIKVA, ARGENTINA, 10))
                .getMessage().contains("order book"));
    }
}
