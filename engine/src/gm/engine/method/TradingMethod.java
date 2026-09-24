package gm.engine.method;

/**
 * The pricing mechanism of a single event: it decides what an option is currently worth and what a
 * purchase costs.
 * <p>
 * It keeps the LMSR formula apart from the event that uses it, so the arithmetic can be tested on its
 * own against the numbers in the course material. The order book never became a second method: it
 * has no formula at all, only people meeting people, so it lives in {@link gm.engine.model.OrderBookEvent}
 * instead, and this interface stays sealed over the one formula the system has.
 */
public sealed interface TradingMethod permits LmsrMethod {

    /**
     * The amount the market maker has to place in the event account before any trading happens.
     *
     * @param optionCount how many options the event offers
     */
    double initialPot(int optionCount);

    /**
     * The current value of one option, between 0 and 1, where 1 means "certain to win".
     *
     * @param shares      how many shares of every option have been bought so far
     * @param optionIndex the zero based index of the option being valued
     */
    double optionValue(long[] shares, int optionIndex);

    /**
     * What it costs, before commission, to buy shares of one option.
     *
     * @param shares      how many shares of every option have been bought so far
     * @param optionIndex the zero based index of the option being bought
     * @param quantity    how many shares to buy, always positive
     */
    double buyCost(long[] shares, int optionIndex, long quantity);

    /**
     * What selling shares of one option back to the event returns, before commission.
     *
     * @param shares      how many shares of every option have been bought so far
     * @param optionIndex the zero based index of the option being sold
     * @param quantity    how many shares to sell, always positive and never more than the market holds
     */
    double sellProceeds(long[] shares, int optionIndex, long quantity);

    /**
     * A short human readable description of the method and its parameters, for display by a user
     * interface.
     */
    String describe();
}
