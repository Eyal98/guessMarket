package gm.engine.api.dto;

import java.io.Serializable;
import java.util.List;

/**
 * The market in one option: who is waiting to trade it, and what the book says it is worth.
 * <p>
 * Every price here may be {@code null}, and that is the point. A book with nobody selling has no ask
 * and therefore no spread, and saying so is the honest answer — a nought would be a lie. Callers are
 * expected to show a dash.
 *
 * @param number        the option's position within its event, counted from 1
 * @param name          the option's name
 * @param bids          everyone waiting to buy, best offer first
 * @param asks          everyone waiting to sell, cheapest first
 * @param lastPrice     what the most recent trade went through at, or null if nothing has traded
 * @param bestBid       the most anyone will pay, or null if nobody is bidding
 * @param bestAsk       the least anyone will accept, or null if nobody is selling
 * @param midPrice      halfway between the two, or null unless both sides exist
 * @param spread        the gap between the two, or null unless both sides exist
 * @param sharesInIssue how many shares of this option exist at all
 */
public record OptionMarketDto(int number, String name, List<OrderDto> bids, List<OrderDto> asks,
                              Double lastPrice, Double bestBid, Double bestAsk, Double midPrice,
                              Double spread, long sharesInIssue) implements Serializable {
}
