package gm.ui.fx;

import gm.engine.api.GuessMarketEngine;
import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.MarketStateDto;
import gm.engine.api.dto.OptionMarketDto;
import gm.engine.api.dto.OptionStateDto;
import gm.engine.api.dto.OrderBookStateDto;
import gm.engine.api.dto.OrderDto;
import gm.engine.api.dto.ParticipantDto;
import gm.engine.api.dto.TradeDto;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Function;

/**
 * The right-hand side of the events tab: one event laid out in full.
 * <p>
 * The two kinds of event look nothing alike and are not forced into one shape. An LMSR event has a
 * value per option and a history of purchases; an order book has two books of waiting orders, the
 * prices they imply, and a table of who holds what. Each gets the layout that suits it.
 */
public final class EventDetailView {

    private final GuessMarketEngine engine;
    private final VBox content = new VBox(12);
    private final ScrollPane root = new ScrollPane(content);

    public EventDetailView(GuessMarketEngine engine) {
        this.engine = engine;
        content.setPadding(new Insets(12));
        root.setFitToWidth(true);
        root.setPannable(true);
    }

    public Node view() {
        return root;
    }

    public void showNothing() {
        showMessage("Choose an event on the left to see it in full.");
    }

    public void showMessage(String message) {
        Label label = new Label(message);
        label.setWrapText(true);
        content.getChildren().setAll(label);
    }

    /** Lays out whichever kind of event this is. */
    public void show(EventInfoDto event) {
        content.getChildren().setAll(heading(event));
        if ("Order book".equals(event.methodKind())) {
            showOrderBook(engine.orderBookState(event.number()));
        } else {
            showLmsr(engine.marketState(event.number()));
        }
    }

    private Node heading(EventInfoDto event) {
        GridPane details = labelled(
                "Status", event.status(),
                "Type", event.tradingMethod(),
                "Commission", Format.percent(event.commissionPercent()) + " — " + event.commissionTiming(),
                "Market maker", String.valueOf(event.marketMakerName()));
        Label title = new Label(event.name());
        title.getStyleClass().add("detail-title");
        Label description = new Label(event.description());
        description.setWrapText(true);
        return new VBox(6, title, description, details, new Separator());
    }

    private void showLmsr(MarketStateDto state) {
        TableView<OptionStateDto> options = new TableView<>();
        options.getColumns().addAll(List.of(
                textColumn("#", option -> String.valueOf(option.number()), 40),
                textColumn("Option", OptionStateDto::name, 160),
                textColumn("Value", option -> Format.money(option.value()), 80),
                textColumn("Shares held", option -> Format.shares(option.sharesBought()), 110)));
        options.getItems().setAll(state.options());
        options.setPrefHeight(110);

        content.getChildren().addAll(
                section("Current standing"), options,
                section("Accounts"),
                labelled("Event account", Format.money(state.eventAccountBalance()),
                        "Commission collected", Format.money(state.commissionCollected()),
                        "Market maker holds", Format.money(state.marketMakerBalance()),
                        "A winning share pays", Format.money(state.payoutPerWinningShare())),
                section("Trading history, newest first"), tradeTable(state.history()));

        if (state.closed()) {
            content.getChildren().addAll(section("Result"),
                    labelled("Winning option", state.winningOptionName(),
                            "Winning shares", Format.shares(state.winningShares()),
                            "Paid out", Format.money(state.totalPaidOut())));
        }
    }

    private void showOrderBook(OrderBookStateDto state) {
        HBox books = new HBox(12);
        for (OptionMarketDto option : state.options()) {
            VBox pane = optionPane(option);
            HBox.setHgrow(pane, Priority.ALWAYS);
            pane.setMinWidth(260);
            books.getChildren().add(pane);
        }

        TableView<ParticipantDto> participants = new TableView<>();
        participants.setPlaceholder(new Label("Nobody has traded here yet."));
        participants.getColumns().add(textColumn("Trader", ParticipantDto::userName, 140));
        for (int i = 0; i < state.options().size(); i++) {
            int optionIndex = i;
            String name = state.options().get(i).name();
            participants.getColumns().add(textColumn(name,
                    who -> Format.shares(who.options().get(optionIndex).shares()), 110));
            participants.getColumns().add(textColumn(name + " paid",
                    who -> Format.money(who.options().get(optionIndex).paidFor()), 110));
        }
        participants.getItems().setAll(state.participants());
        participants.setPrefHeight(180);

        content.getChildren().addAll(
                section("Accounts"),
                labelled("Event account", Format.money(state.eventAccountBalance()),
                        "Commission collected", Format.money(state.commissionCollected()),
                        "A winning share pays", Format.money(state.baseValue()),
                        "Minting", state.mintAllowed() ? "allowed" : "not allowed"),
                section("Order books"), books,
                section("Participants"), participants);
    }

    private VBox optionPane(OptionMarketDto option) {
        Label name = new Label(option.name());
        name.getStyleClass().add("option-title");

        GridPane stats = labelled(
                "Last", Format.price(option.lastPrice()),
                "Bid", Format.price(option.bestBid()),
                "Ask", Format.price(option.bestAsk()),
                "Mid", Format.price(option.midPrice()),
                "Spread", Format.price(option.spread()),
                "Shares in issue", Format.shares(option.sharesInIssue()));

        return new VBox(6, name, stats,
                new Label("Buyers"), orderTable(option.bids(), "Nobody is bidding."),
                new Label("Sellers"), orderTable(option.asks(), "Nobody is selling."));
    }

    private TableView<OrderDto> orderTable(List<OrderDto> orders, String whenEmpty) {
        TableView<OrderDto> table = new TableView<>();
        table.setPlaceholder(new Label(whenEmpty));
        table.getColumns().addAll(List.of(
                textColumn("Trader", OrderDto::userName, 110),
                textColumn("Shares", order -> Format.shares(order.quantity()), 70),
                textColumn("Price", order -> Format.money(order.price()), 70)));
        table.getItems().setAll(orders);
        table.setPrefHeight(130);
        return table;
    }

    private TableView<TradeDto> tradeTable(List<TradeDto> trades) {
        TableView<TradeDto> table = new TableView<>();
        table.setPlaceholder(new Label("Nothing has been traded here yet."));
        table.getColumns().addAll(List.of(
                textColumn("Option", TradeDto::optionName, 150),
                textColumn("Shares", trade -> Format.shares(trade.quantity()), 80),
                textColumn("Cost", trade -> Format.money(trade.sharesCost()), 90),
                textColumn("Commission", trade -> Format.money(trade.commission()), 100),
                textColumn("Total", trade -> Format.money(trade.totalPaid()), 90)));
        table.getItems().setAll(trades);
        table.setPrefHeight(160);
        return table;
    }

    static Label section(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("section-title");
        return label;
    }

    /** Pairs of caption and value, laid out in two columns so they line up down the screen. */
    static GridPane labelled(String... captionsAndValues) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);
        ColumnConstraints captions = new ColumnConstraints();
        captions.setMinWidth(150);
        grid.getColumnConstraints().add(captions);
        for (int i = 0; i + 1 < captionsAndValues.length; i += 2) {
            Label caption = new Label(captionsAndValues[i]);
            caption.getStyleClass().add("caption");
            grid.add(caption, 0, i / 2);
            grid.add(new Label(captionsAndValues[i + 1]), 1, i / 2);
        }
        return grid;
    }

    static <T> TableColumn<T, String> textColumn(String title, Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(value.apply(row.getValue())));
        column.setPrefWidth(width);
        return column;
    }
}
