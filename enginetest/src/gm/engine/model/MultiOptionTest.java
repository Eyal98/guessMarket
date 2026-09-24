package gm.engine.model;

import gm.engine.model.orderbook.OrderSide;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static gm.engine.TestUsers.funded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Events with more than two outcomes, which exercise 3 names as one of its two goals.
 * <p>
 * LMSR was written for any number of options from the start. The order book needs one real idea: a
 * mint must create a <em>complete set</em>, one new share of every option, because only a complete set
 * is guaranteed to be worth exactly the base value whichever option wins. Two buyers of two options out
 * of three cannot mint between them, however much they offer, since the third option's winners would
 * be owed money nobody paid in.
 */
class MultiOptionTest {

    private static final double TOLERANCE = 0.0001;

    private final User tikva = funded("Tikva", 1000);
    private final User menash = funded("Menash", 1000);
    private final User avrum = funded("Avrum", 1000);
    private final User zoe = funded("Zoe", 1000);

    private static final List<String> THREE = List.of("Argentina", "Spain", "Brazil");

    private OrderBookEvent cup(int initial) {
        OrderBookEvent cup = new OrderBookEvent("Cup", "Who wins?",
                new Commission(0, CommissionType.ON_PURCHASE), THREE, initial, 1, true);
        cup.assignMarketMaker(tikva);
        cup.open(tikva);
        return cup;
    }

    @Test
    @DisplayName("An LMSR event of three options is subsidised with b ln 3 and starts every option at a third")
    void lmsrWithThreeOptions() {
        LmsrEvent event = new LmsrEvent("Cup", "Who wins?", new Commission(0, CommissionType.ON_PURCHASE),
                THREE, 100);
        event.assignMarketMaker(tikva);

        event.open(tikva);

        assertEquals(100 * Math.log(3), event.account().balance(), TOLERANCE);
        for (int option = 0; option < 3; option++) {
            assertEquals(1.0 / 3, event.valueOf(option), TOLERANCE);
        }
        event.buy(menash, 2, 50);
        assertEquals(1.0, event.valueOf(0) + event.valueOf(1) + event.valueOf(2), TOLERANCE,
                "the three values still add up to a whole");
    }

    @Test
    @DisplayName("Opening an order book of three options hands the market maker a share of each")
    void openingStockOfThree() {
        OrderBookEvent cup = cup(100);

        for (int option = 0; option < 3; option++) {
            assertEquals(100, cup.holdingOf(tikva).shares(option));
            assertEquals(100, cup.options().get(option).sharesBought());
        }
    }

    @Test
    @DisplayName("Buyers of two options out of three cannot mint, however much they offer between them")
    void twoOfThreeCannotMint() {
        OrderBookEvent cup = cup(0);

        cup.submitOrder(menash, 0, OrderSide.BUY, 10, 0.60);
        List<Trade> trades = cup.submitOrder(avrum, 1, OrderSide.BUY, 10, 0.45);

        assertTrue(trades.isEmpty(), "Brazil has no buyer, so no complete set can be made");
        assertEquals(0, cup.options().get(0).sharesBought());
        assertEquals(1, cup.bookFor(0).bids().size());
        assertEquals(1, cup.bookFor(1).bids().size());
        assertEquals(0.0, cup.account().balance(), TOLERANCE);
    }

    @Test
    @DisplayName("A buyer for every option whose prices reach the base value mint a complete set")
    void aBuyerForEveryOptionMints() {
        OrderBookEvent cup = cup(0);
        cup.submitOrder(menash, 0, OrderSide.BUY, 10, 0.30);
        cup.submitOrder(avrum, 1, OrderSide.BUY, 10, 0.30);

        List<Trade> trades = cup.submitOrder(zoe, 2, OrderSide.BUY, 10, 0.45);

        assertEquals(3, trades.size(), "one new share of every option, for each of the three buyers");
        for (int option = 0; option < 3; option++) {
            assertEquals(10, cup.options().get(option).sharesBought());
            assertTrue(cup.bookFor(option).bids().isEmpty());
        }
        assertEquals(10.0, cup.account().balance(), TOLERANCE, "ten complete sets, each worth the base value");
        assertEquals(1000 - 3.0, menash.account().balance(), TOLERANCE, "resting orders keep their price");
        assertEquals(1000 - 3.0, avrum.account().balance(), TOLERANCE);
        assertEquals(1000 - 4.0, zoe.account().balance(), TOLERANCE,
                "the order that completed the set pays what completes the base value, 0.40 not 0.45");
    }

    @Test
    @DisplayName("A set is minted in the quantity every buyer can take, and the rest keeps waiting")
    void theSmallestOrderDecidesTheSet() {
        OrderBookEvent cup = cup(0);
        cup.submitOrder(menash, 0, OrderSide.BUY, 10, 0.30);
        cup.submitOrder(avrum, 1, OrderSide.BUY, 4, 0.30);

        cup.submitOrder(zoe, 2, OrderSide.BUY, 7, 0.40);

        assertEquals(4, cup.options().get(2).sharesBought());
        assertEquals(6, cup.bookFor(0).bids().get(0).remaining(), "Menash still wants six more");
        assertEquals(3, cup.bookFor(2).bids().get(0).remaining(), "and Zoe three more, now resting");
        assertTrue(cup.bookFor(1).bids().isEmpty(), "Avrum's four were all used");
    }

    @Test
    @DisplayName("A minted set is always paid out in full, whichever of the three options wins")
    void aMintedSetIsAlwaysCovered() {
        OrderBookEvent cup = cup(0);
        cup.submitOrder(menash, 0, OrderSide.BUY, 10, 0.30);
        cup.submitOrder(avrum, 1, OrderSide.BUY, 10, 0.30);
        cup.submitOrder(zoe, 2, OrderSide.BUY, 10, 0.45);

        cup.close(tikva, 2);

        assertEquals(1000 - 4.0 + 10.0, zoe.account().balance(), TOLERANCE, "Brazil's holder is paid in full");
        assertEquals(0.0, cup.account().balance(), TOLERANCE, "and the event account is exactly emptied");
    }

    @Test
    @DisplayName("With two options a mint still needs only the one opposing buyer, exactly as before")
    void twoOptionsAreUnchanged() {
        OrderBookEvent pair = new OrderBookEvent("Pair", "Yes or no?",
                new Commission(0, CommissionType.ON_PURCHASE), List.of("Yes", "No"), 0, 1, true);
        pair.assignMarketMaker(tikva);
        pair.open(tikva);

        pair.submitOrder(menash, 0, OrderSide.BUY, 10, 0.58);
        List<Trade> trades = pair.submitOrder(avrum, 1, OrderSide.BUY, 10, 0.42);

        assertEquals(2, trades.size());
        assertEquals(10.0, pair.account().balance(), TOLERANCE);
    }
}
