package gm.engine.model.orderbook;

/** Which way round an order goes. */
public enum OrderSide {

    BUY("Buy"),
    SELL("Sell");

    private final String displayName;

    OrderSide(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
