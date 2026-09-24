package gm.client.trade;

import gm.client.Format;
import gm.client.Views;
import gm.client.http.MarketServer;
import gm.client.main.MarketFeed;
import gm.client.main.Messenger;
import gm.dto.EventInfoDto;
import gm.dto.PurchaseResultDto;
import gm.dto.TradeDto;
import gm.dto.UserDetailDto;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Everything the logged-in user can do to one event: open it if they run it and it is waiting, trade
 * in it while it runs, and close it if they run it.
 * <p>
 * Written once and used twice, under the event on the events tab and under the chosen event on the
 * account tab, which is the reuse the exercise asks to see. It only ever acts for whoever is logged
 * in: the server takes the user from the session, so there is nobody else it could act for.
 * <p>
 * The panel is rebuilt only when what can be done changes — the event opens or closes, or the user
 * stops being allowed to act. The market around it moves twice a second, and rebuilding with it would
 * wipe out a quantity the user was half way through typing.
 */
public final class TradePanel {

    private static final String ORDER_BOOK = "Order book";

    private final MarketServer server;
    private final MarketFeed feed;
    private final Messenger messenger;
    private final VBox root = new VBox(8);

    private EventInfoDto event;
    private String builtFor;

    public TradePanel(MarketServer server, MarketFeed feed, Messenger messenger) {
        this.server = server;
        this.feed = feed;
        this.messenger = messenger;
        root.getStyleClass().add("trade-panel");
        feed.me().addListener((ignored, was, now) -> rebuildIfNeeded());
        rebuildIfNeeded();
    }

    public Node view() {
        return root;
    }

    /** Offers whatever can be done to this event, or nothing if there is no event. */
    public void show(EventInfoDto chosen) {
        this.event = chosen;
        rebuildIfNeeded();
    }

    private void rebuildIfNeeded() {
        UserDetailDto me = feed.me().get();
        String situation = event == null || me == null ? null
                : event.number() + "|" + event.status() + "|" + runsIt(me) + "|" + me.blocked();
        if (situation != null && situation.equals(builtFor)) {
            return;
        }
        builtFor = situation;
        root.getChildren().setAll(controlsFor(me));
    }

    private boolean runsIt(UserDetailDto me) {
        return Objects.equals(event.marketMakerName(), me.name());
    }

    private List<Node> controlsFor(UserDetailDto me) {
        if (event == null) {
            return List.of(Views.wrapping("Choose an event to act on it."));
        }
        if (me == null) {
            return List.of(Views.wrapping("Fetching your account..."));
        }
        if (me.blocked()) {
            return List.of(Views.wrapping("You have spent past zero, and can take no further part in the"
                    + " market."));
        }
        switch (event.status()) {
            case "Not started" -> {
                return List.of(runsIt(me) ? openControls()
                        : Views.wrapping("This event is waiting for its market maker, "
                        + event.marketMakerName() + ", to open it."));
            }
            case "Closed" -> {
                return List.of(Views.wrapping("This event is closed. It was decided on \""
                        + event.winningOptionName() + "\"."));
            }
            default -> {
                Node trading = ORDER_BOOK.equals(event.methodKind()) ? orderControls() : lmsrControls();
                return runsIt(me) ? List.of(trading, closeControls()) : List.of(trading);
            }
        }
    }

    private Node openControls() {
        Button open = new Button("Open this event");
        open.getStyleClass().add("primary-button");
        open.setOnAction(ignored -> act(open, (done, failed) -> server.open(event.number(), done, failed),
                this::describeOpening));
        return new VBox(6, Views.wrapping("You run this event. Opening it costs "
                + Format.money(event.openingCost()) + ", paid from your own account."), open);
    }

    private Node lmsrControls() {
        ChoiceBox<String> option = optionChoice();
        option.setId("tradeOption");
        Spinner<Integer> quantity = quantitySpinner();
        quantity.setId("tradeQuantity");

        Button buy = new Button("Buy");
        buy.getStyleClass().add("primary-button");
        buy.setOnAction(ignored -> act(buy, (done, failed) -> server.buy(event.number(), chosen(option),
                quantityIn(quantity), done, failed), this::describePurchase));

        Button sell = new Button("Sell");
        sell.setOnAction(ignored -> act(sell, (done, failed) -> server.sell(event.number(), chosen(option),
                quantityIn(quantity), done, failed), this::describeSale));

        return new VBox(6, Views.wrapping("Shares are bought from, and sold back to, the event itself."),
                row(new Label("Option"), option, new Label("Shares"), quantity, buy, sell));
    }

    private Node orderControls() {
        ChoiceBox<String> option = optionChoice();
        option.setId("tradeOption");
        ChoiceBox<String> side = new ChoiceBox<>(FXCollections.observableArrayList("Buy", "Sell"));
        side.setId("tradeSide");
        side.getSelectionModel().selectFirst();
        Spinner<Integer> quantity = quantitySpinner();
        quantity.setId("tradeQuantity");
        TextField price = new TextField("0.50");
        price.setId("tradePrice");
        price.setPrefWidth(70);

        Button place = new Button("Place order");
        place.getStyleClass().add("primary-button");
        place.setOnAction(ignored -> {
            Double asked = priceIn(price);
            if (asked == null) {
                return;
            }
            act(place, (done, failed) -> server.order(event.number(), chosen(option),
                    side.getValue().toLowerCase(Locale.ROOT), quantityIn(quantity), asked, done, failed),
                    this::describeOrder);
        });

        return new VBox(6, Views.wrapping("Orders meet other people's. Whatever finds no match waits in"
                        + " the book."),
                row(new Label("Option"), option, new Label("Side"), side, new Label("Shares"), quantity,
                        new Label("Price"), price, place));
    }

    private Node closeControls() {
        ChoiceBox<String> winner = optionChoice();
        winner.setId("closeOption");
        Button close = new Button("Close on this outcome");
        close.setOnAction(ignored -> act(close, (done, failed) -> server.close(event.number(),
                chosen(winner), done, failed), this::describeClosing));
        return new VBox(6, new Separator(),
                Views.wrapping("You run this event, so you decide it. This cannot be undone."),
                row(new Label("Winning option"), winner, close));
    }

    /** One request to the server, waiting to be told where to send its answer. */
    @FunctionalInterface
    private interface Request<T> {
        void send(Consumer<T> done, Consumer<MarketServer.Failure> failed);
    }

    /**
     * Sends one action and reports what came of it. The button that sent it stays disabled until the
     * answer arrives, so an impatient second click cannot buy twice.
     */
    private <T> void act(Button button, Request<T> request, Function<T, String> describe) {
        button.setDisable(true);
        request.send(answer -> {
            button.setDisable(false);
            messenger.status(describe.apply(answer));
            feed.pullEverything();
        }, failure -> {
            button.setDisable(false);
            messenger.refused(failure);
        });
    }

    private String describeOpening(EventInfoDto opened) {
        return "You opened \"" + opened.name() + "\". It is trading now.";
    }

    private String describeClosing(EventInfoDto closed) {
        return "\"" + closed.name() + "\" is closed on \"" + closed.winningOptionName()
                + "\". Its winners have been paid.";
    }

    private String describePurchase(PurchaseResultDto bought) {
        String commission = bought.commission() > 0
                ? " plus " + Format.money(bought.commission()) + " commission"
                : "";
        return "You bought " + Format.shares(bought.quantity()) + " of \"" + bought.optionName() + "\" for "
                + Format.money(bought.sharesCost()) + commission + ".";
    }

    private String describeSale(PurchaseResultDto sold) {
        return "You sold " + Format.shares(-sold.quantity()) + " of \"" + sold.optionName() + "\" for "
                + Format.money(-sold.sharesCost()) + ".";
    }

    private String describeOrder(List<TradeDto> trades) {
        if (trades.isEmpty()) {
            return "Your order is waiting in the book.";
        }
        long shares = trades.stream().mapToLong(TradeDto::quantity).sum();
        return "Your order went through: " + trades.size() + (trades.size() == 1 ? " trade" : " trades")
                + ", " + Format.shares(shares) + " shares changing hands or newly minted.";
    }

    private ChoiceBox<String> optionChoice() {
        ChoiceBox<String> choice = new ChoiceBox<>(FXCollections.observableArrayList(event.optionNames()));
        choice.getSelectionModel().selectFirst();
        return choice;
    }

    private static int chosen(ChoiceBox<String> option) {
        return option.getSelectionModel().getSelectedIndex() + 1;
    }

    private static Spinner<Integer> quantitySpinner() {
        Spinner<Integer> spinner = new Spinner<>(1, 1_000_000, 10);
        spinner.setEditable(true);
        spinner.setPrefWidth(95);
        return spinner;
    }

    /**
     * The number in the box, including one that has been typed but not entered.
     * <p>
     * A JavaFX spinner keeps its value and its text apart: typing over the number changes only the
     * text, and the value stays behind until Enter is pressed or the box loses the focus. Somebody who
     * types 100 and goes straight to Buy would otherwise buy the ten that were there before. Stepping
     * by nothing is the documented way to make a spinner take what it has been given.
     */
    private static long quantityIn(Spinner<Integer> spinner) {
        spinner.increment(0);
        return spinner.getValue();
    }

    /** The price typed, or null after saying why it is not one. */
    private Double priceIn(TextField field) {
        try {
            return Double.parseDouble(field.getText().trim());
        } catch (NumberFormatException e) {
            messenger.refused(new MarketServer.Failure(400, "\"" + field.getText().trim()
                    + "\" is not a price. Please type a number, such as 0.50.", List.of()));
            return null;
        }
    }

    private static FlowPane row(Node... children) {
        FlowPane row = new FlowPane(8, 6, children);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
