package gm.engine.model;

import gm.engine.model.orderbook.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Matching, walking the book and minting, worked through the scenario in the supplied CLOB
 * simulation. Its numbers are deterministic, so they are used as given rather than invented.
 * <p>
 * YES is option 1 and NO is option 2 throughout, and the base value is 1.
 */
class OrderMatchingTest {

    private static final double TOLERANCE = 0.0001;
    private static final int YES = 0;
    private static final int NO = 1;

    private final User zoe = new User("Zoe", 10000);
    private final User bob = new User("Bob", 10000);
    private final User carol = new User("Carol", 10000);
    private final User alice = new User("Alice", 10000);

    private OrderBookEvent openMarket(int commissionPercent, boolean allowMint) {
        OrderBookEvent event = new OrderBookEvent(1, "Will it rain?", "Tomorrow, in town.",
                new Commission(commissionPercent, CommissionType.ON_PURCHASE),
                List.of("Yes", "No"), 100, 1, allowMint);
        event.assignMarketMaker(zoe);
        event.open(zoe);
        return event;
    }

    @Test
    @DisplayName("An order that crosses nothing simply rests")
    void anUnmatchedOrderRests() {
        OrderBookEvent event = openMarket(0, true);

        event.submitOrder(bob, YES, OrderSide.BUY, 20, 0.50);

        assertEquals(1, event.bookFor(YES).bids().size());
        assertEquals(0.50, event.bookFor(YES).bestBid().getAsDouble(), TOLERANCE);
        assertTrue(event.bookFor(YES).lastTradedPrice().isEmpty(), "nothing has traded yet");
    }

    @Test
    @DisplayName("A buyer meeting a resting ask trades at the ask's price, and the seller is paid directly")
    void aResaleGoesThroughAtTheRestingPrice() {
        OrderBookEvent event = openMarket(0, true);
        double zoeAfterOpening = zoe.account().balance();
        event.submitOrder(zoe, YES, OrderSide.SELL, 25, 0.58);

        event.submitOrder(alice, YES, OrderSide.BUY, 25, 0.58);

        assertEquals(zoeAfterOpening + 14.50, zoe.account().balance(), TOLERANCE);
        assertEquals(10000.0 - 14.50, alice.account().balance(), TOLERANCE);
        assertEquals(25, event.holdingOf(alice).shares(YES));
        assertEquals(75, event.holdingOf(zoe).shares(YES));
        assertEquals(0.58, event.bookFor(YES).lastTradedPrice().getAsDouble(), TOLERANCE);
        assertTrue(event.bookFor(YES).asks().isEmpty(), "the ask was used up");
    }

    @Test
    @DisplayName("A resale moves money between people, never through the event account")
    void aResaleDoesNotTouchTheEventAccount() {
        OrderBookEvent event = openMarket(0, true);
        double eventHeld = event.account().balance();
        event.submitOrder(zoe, YES, OrderSide.SELL, 25, 0.58);

        event.submitOrder(alice, YES, OrderSide.BUY, 25, 0.58);

        assertEquals(eventHeld, event.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("A seller walks the bids best first, and is paid better than the price they asked")
    void sellingWalksTheBookAndImprovesOnTheAsk() {
        OrderBookEvent event = openMarket(0, true);
        event.submitOrder(bob, YES, OrderSide.BUY, 20, 0.50);
        event.submitOrder(carol, YES, OrderSide.BUY, 15, 0.48);
        double zoeBeforeSelling = zoe.account().balance();

        event.submitOrder(zoe, YES, OrderSide.SELL, 30, 0.45);

        // 20 clear at Bob's 0.50 and 10 at Carol's 0.48: 10.00 + 4.80, better than the 0.45 floor.
        assertEquals(zoeBeforeSelling + 14.80, zoe.account().balance(), TOLERANCE);
        assertEquals(20, event.holdingOf(bob).shares(YES));
        assertEquals(10, event.holdingOf(carol).shares(YES));
        assertEquals(1, event.bookFor(YES).bids().size());
        assertEquals(5, event.bookFor(YES).bids().get(0).remaining(), "Carol keeps what was not filled");
    }

    @Test
    @DisplayName("Two opposing buyers whose prices reach the base value mint new shares between them")
    void opposingBuyersMintNewShares() {
        OrderBookEvent event = openMarket(0, true);
        event.submitOrder(carol, NO, OrderSide.BUY, 35, 0.42);
        double eventBeforeMint = event.account().balance();
        double carolBefore = carol.account().balance();
        double aliceBefore = alice.account().balance();

        event.submitOrder(alice, YES, OrderSide.BUY, 40, 0.62);

        // min(40, 35) = 35 pairs. Carol was resting, so her 0.42 stands; Alice completes the dollar
        // at 0.58 rather than the 0.62 she was willing to pay.
        assertEquals(35, event.holdingOf(alice).shares(YES));
        assertEquals(35, event.holdingOf(carol).shares(NO));
        assertEquals(carolBefore - 35 * 0.42, carol.account().balance(), TOLERANCE);
        assertEquals(aliceBefore - 35 * 0.58, alice.account().balance(), TOLERANCE);
        assertEquals(eventBeforeMint + 35.0, event.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("Minting creates shares, so the market holds more of both than before")
    void mintingBringsNewSharesIntoExistence() {
        OrderBookEvent event = openMarket(0, true);
        long yesBefore = event.options().get(YES).sharesBought();
        long noBefore = event.options().get(NO).sharesBought();
        event.submitOrder(carol, NO, OrderSide.BUY, 35, 0.42);

        event.submitOrder(alice, YES, OrderSide.BUY, 40, 0.62);

        assertEquals(yesBefore + 35, event.options().get(YES).sharesBought());
        assertEquals(noBefore + 35, event.options().get(NO).sharesBought());
    }

    @Test
    @DisplayName("Whatever the mint could not use goes on resting")
    void theUnmintedRemainderRests() {
        OrderBookEvent event = openMarket(0, true);
        event.submitOrder(carol, NO, OrderSide.BUY, 35, 0.42);

        event.submitOrder(alice, YES, OrderSide.BUY, 40, 0.62);

        assertEquals(1, event.bookFor(YES).bids().size());
        assertEquals(5, event.bookFor(YES).bids().get(0).remaining());
        assertTrue(event.bookFor(NO).bids().isEmpty(), "Carol's order was used up entirely");
    }

    @Test
    @DisplayName("With minting switched off, opposing buyers simply wait")
    void mintingCanBeSwitchedOff() {
        OrderBookEvent event = openMarket(0, false);
        event.submitOrder(carol, NO, OrderSide.BUY, 35, 0.42);

        event.submitOrder(alice, YES, OrderSide.BUY, 40, 0.62);

        assertEquals(0, event.holdingOf(alice).shares(YES));
        assertEquals(40, event.bookFor(YES).bids().get(0).remaining());
        assertEquals(35, event.bookFor(NO).bids().get(0).remaining());
    }

    @Test
    @DisplayName("No share may be priced at the whole base value or more")
    void thePriceIsCappedBelowTheBaseValue() {
        OrderBookEvent event = openMarket(0, true);

        assertThrows(IllegalArgumentException.class,
                () -> event.submitOrder(bob, YES, OrderSide.BUY, 10, 1.05));
        assertThrows(IllegalArgumentException.class,
                () -> event.submitOrder(bob, YES, OrderSide.BUY, 10, 1.00));

        assertTrue(event.bookFor(YES).bids().isEmpty(), "a rejected order never reaches the book");
    }

    @Test
    @DisplayName("Nobody can offer shares they do not hold")
    void sellingNeedsTheShares() {
        OrderBookEvent event = openMarket(0, true);

        assertThrows(IllegalArgumentException.class,
                () -> event.submitOrder(bob, YES, OrderSide.SELL, 10, 0.50));
    }

    @Test
    @DisplayName("The buyer pays the commission on top, and it is the market maker's")
    void theBuyerPaysCommissionOnTop() {
        OrderBookEvent event = openMarket(10, true);
        event.submitOrder(zoe, YES, OrderSide.SELL, 25, 0.58);
        double zoeBefore = zoe.account().balance();

        event.submitOrder(alice, YES, OrderSide.BUY, 25, 0.58);

        // 14.50 for the shares, and a tenth of that again as commission to Zoe, who runs the event.
        assertEquals(10000.0 - 14.50 - 1.45, alice.account().balance(), TOLERANCE);
        assertEquals(zoeBefore + 14.50 + 1.45, zoe.account().balance(), TOLERANCE);
        assertEquals(1.45, event.holdingOf(alice).commissionPaid(), TOLERANCE);
    }

    @Test
    @DisplayName("Closing the event cancels every order still waiting")
    void closingCancelsWhatNeverMatched() {
        OrderBookEvent event = openMarket(0, true);
        event.submitOrder(bob, YES, OrderSide.BUY, 20, 0.50);
        event.submitOrder(carol, NO, OrderSide.BUY, 10, 0.30);

        event.close(zoe, YES);

        assertTrue(event.bookFor(YES).bids().isEmpty());
        assertTrue(event.bookFor(NO).bids().isEmpty());
    }

    @Test
    @DisplayName("Each winning share pays the base value when the event closes")
    void winningSharesPayTheBaseValue() {
        OrderBookEvent event = openMarket(0, true);
        event.submitOrder(zoe, YES, OrderSide.SELL, 25, 0.58);
        event.submitOrder(alice, YES, OrderSide.BUY, 25, 0.58);
        double aliceBeforeClosing = alice.account().balance();

        event.close(zoe, YES);

        assertEquals(aliceBeforeClosing + 25.0, alice.account().balance(), TOLERANCE);
    }
}
