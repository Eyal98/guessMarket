package gm.client.events;

import gm.client.Format;
import gm.client.Views;
import gm.client.http.MarketServer;
import gm.client.main.MarketFeed;
import gm.dto.EventInfoDto;
import gm.dto.MarketStateDto;
import gm.dto.OptionMarketDto;
import gm.dto.OptionStateDto;
import gm.dto.OrderBookStateDto;
import gm.dto.OrderDto;
import gm.dto.ParticipantDto;
import gm.dto.TradeDto;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;

/**
 * The right-hand side of the events tab: one event laid out in full, with the controls for acting on
 * it under its heading.
 * <p>
 * The two kinds of event look nothing alike and are not forced into one shape. An LMSR event has a
 * value per option and a history of purchases; an order book has a book of waiting orders for every
 * option, the prices they imply, and a table of who holds what. Each gets the layout that suits it.
 * An event may have any number of options, so an order book's books are laid out two to a row, which
 * for the usual two options is exactly the side-by-side pair of the sketch.
 * <p>
 * The event being shown is pulled from the server with everything else, and redrawn only when what
 * arrives differs from what is on the screen.
 */
public final class EventDetailView {

    private static final String ORDER_BOOK = "Order book";
    private static final int BOOKS_PER_ROW = 2;

    private final MarketServer server;
    private final MarketFeed feed;
    private final Node actions;
    private final VBox content = new VBox(12);
    private final ScrollPane root = Views.scrolling(content);

    private EventInfoDto watched;
    private Object shown;

    /**
     * @param actions the controls for acting on the event, placed under its heading; they belong to
     *                whoever owns this view and are never rebuilt here
     */
    public EventDetailView(MarketServer server, MarketFeed feed, Node actions) {
        this.server = server;
        this.feed = feed;
        this.actions = actions;
        content.setPadding(new Insets(12));
        root.setId("eventDetail");
        feed.every(this::pull);
        showNothing();
    }

    public Node view() {
        return root;
    }

    /** Starts showing an event, or nothing at all. */
    public void watch(EventInfoDto event) {
        boolean another = event == null || watched == null || event.number() != watched.number();
        watched = event;
        if (event == null) {
            showNothing();
            return;
        }
        if (another) {
            shown = null;
            content.getChildren().setAll(heading(event), actions, Views.wrapping("Fetching the event..."));
            pull(() -> { });
        }
    }

    private void showNothing() {
        shown = null;
        content.getChildren().setAll(Views.wrapping("Choose an event on the left to see it in full."));
    }

    private void pull(Runnable finished) {
        EventInfoDto event = watched;
        if (event == null) {
            finished.run();
            return;
        }
        if (ORDER_BOOK.equals(event.methodKind())) {
            server.orderBook(event.number(), state -> {
                showIfNew(event, state, () -> showOrderBook(state));
                finished.run();
            }, feed.failed(finished));
        } else {
            server.lmsrEvent(event.number(), state -> {
                showIfNew(event, state, () -> showLmsr(state));
                finished.run();
            }, feed.failed(finished));
        }
    }

    /**
     * Redraws only if this is still the event being watched and something about it changed, and keeps
     * the reader's place: a redraw caused by somebody else's trade should not throw them back to the
     * top of the page.
     */
    private void showIfNew(EventInfoDto askedAbout, Object state, Runnable draw) {
        if (watched == null || watched.number() != askedAbout.number() || Objects.equals(state, shown)) {
            return;
        }
        double place = root.getVvalue();
        shown = state;
        draw.run();
        Platform.runLater(() -> root.setVvalue(place));
    }

    private Node heading(EventInfoDto event) {
        GridPane details = Views.labelled(
                "Status", event.status(),
                "Type", event.tradingMethod(),
                "Commission", Format.percent(event.commissionPercent()) + " — " + event.commissionTiming(),
                "Market maker", String.valueOf(event.marketMakerName()));
        Label title = new Label(event.name());
        title.getStyleClass().add("detail-title");
        return new VBox(6, title, Views.wrapping(event.description()), details, new Separator());
    }

    private void showLmsr(MarketStateDto state) {
        TableView<OptionStateDto> options = Views.fitted(new TableView<>());
        options.getColumns().addAll(List.of(
                Views.<OptionStateDto>textColumn("#", option -> String.valueOf(option.number()), 40),
                Views.<OptionStateDto>textColumn("Option", OptionStateDto::name, 160),
                Views.<OptionStateDto>textColumn("Value", option -> Format.money(option.value()), 80),
                Views.<OptionStateDto>textColumn("Shares held",
                        option -> Format.shares(option.sharesBought()), 110)));
        options.getItems().setAll(state.options());
        options.setPrefHeight(70 + 28.0 * state.options().size());

        content.getChildren().setAll(heading(state.event()), actions,
                Views.section("Current standing"), options,
                Views.section("Accounts"),
                Views.labelled("Event account", Format.money(state.eventAccountBalance()),
                        "Commission collected", Format.money(state.commissionCollected()),
                        "Market maker holds", Format.money(state.marketMakerBalance()),
                        "A winning share pays", Format.money(state.payoutPerWinningShare())),
                Views.section("Trading history, newest first"), tradeTable(state.history()));

        if (state.closed()) {
            content.getChildren().addAll(Views.section("Result"),
                    Views.labelled("Winning option", state.winningOptionName(),
                            "Winning shares", Format.shares(state.winningShares()),
                            "Paid out", Format.money(state.totalPaidOut())));
        }
    }

    private void showOrderBook(OrderBookStateDto state) {
        GridPane books = new GridPane();
        books.setHgap(12);
        books.setVgap(12);
        for (int column = 0; column < BOOKS_PER_ROW; column++) {
            ColumnConstraints half = new ColumnConstraints();
            half.setPercentWidth(100.0 / BOOKS_PER_ROW);
            half.setHgrow(Priority.ALWAYS);
            books.getColumnConstraints().add(half);
        }
        List<OptionMarketDto> options = state.options();
        for (int i = 0; i < options.size(); i++) {
            books.add(optionPane(options.get(i)), i % BOOKS_PER_ROW, i / BOOKS_PER_ROW);
        }

        content.getChildren().setAll(heading(state.event()), actions,
                Views.section("Accounts"),
                Views.labelled("Event account", Format.money(state.eventAccountBalance()),
                        "Commission collected", Format.money(state.commissionCollected()),
                        "A winning share pays", Format.money(state.baseValue()),
                        "Highest price allowed", Format.money(state.highestAllowedPrice()),
                        "Minting", state.mintAllowed() ? "allowed" : "not allowed"),
                Views.section("Order books"), books,
                Views.section("Participants"), participantsTable(state));
    }

    private TableView<ParticipantDto> participantsTable(OrderBookStateDto state) {
        TableView<ParticipantDto> participants = new TableView<>();
        participants.setPlaceholder(new Label("Nobody has traded here yet."));
        participants.getColumns().add(Views.textColumn("Trader", ParticipantDto::userName, 120));
        for (int i = 0; i < state.options().size(); i++) {
            int optionIndex = i;
            String name = state.options().get(i).name();
            participants.getColumns().add(Views.<ParticipantDto>textColumn(name,
                    who -> Format.shares(who.options().get(optionIndex).shares()), 100));
            participants.getColumns().add(Views.<ParticipantDto>textColumn(name + " paid",
                    who -> Format.money(who.options().get(optionIndex).paidFor()), 100));
            participants.getColumns().add(Views.<ParticipantDto>textColumn(name + " worth",
                    who -> Format.moneyOrNothing(who.options().get(optionIndex).currentValue()), 100));
        }
        participants.getItems().setAll(state.participants());
        participants.setPrefHeight(180);
        return participants;
    }

    private VBox optionPane(OptionMarketDto option) {
        Label name = new Label(option.name());
        name.getStyleClass().add("option-title");

        GridPane stats = Views.labelled(
                "Last", Format.moneyOrNothing(option.lastPrice()),
                "Bid", Format.moneyOrNothing(option.bestBid()),
                "Ask", Format.moneyOrNothing(option.bestAsk()),
                "Mid", Format.moneyOrNothing(option.midPrice()),
                "Spread", Format.moneyOrNothing(option.spread()),
                "Shares in issue", Format.shares(option.sharesInIssue()));

        VBox pane = new VBox(6, name, stats,
                new Label("Buyers"), orderTable(option.bids(), "Nobody is bidding."),
                new Label("Sellers"), orderTable(option.asks(), "Nobody is selling."));
        pane.getStyleClass().add("option-book");
        pane.setMinWidth(0);
        return pane;
    }

    private static TableView<OrderDto> orderTable(List<OrderDto> orders, String whenEmpty) {
        TableView<OrderDto> table = Views.fitted(new TableView<>());
        table.setPlaceholder(new Label(whenEmpty));
        table.getColumns().addAll(List.of(
                Views.textColumn("Trader", OrderDto::userName, 100),
                Views.<OrderDto>textColumn("Shares", order -> Format.shares(order.quantity()), 70),
                Views.<OrderDto>textColumn("Price", order -> Format.money(order.price()), 70)));
        table.getItems().setAll(orders);
        table.setPrefHeight(130);
        return table;
    }

    /** Trades, newest first. Shared with the account tab, which shows a user's own trades the same way. */
    public static TableView<TradeDto> tradeTable(List<TradeDto> trades) {
        TableView<TradeDto> table = Views.fitted(new TableView<>());
        table.setPlaceholder(new Label("Nothing has been traded here yet."));
        table.getColumns().addAll(List.of(
                Views.textColumn("Option", TradeDto::optionName, 150),
                Views.<TradeDto>textColumn("Shares", trade -> Format.shares(trade.quantity()), 80),
                Views.<TradeDto>textColumn("Cost", trade -> Format.money(trade.sharesCost()), 90),
                Views.<TradeDto>textColumn("Commission", trade -> Format.money(trade.commission()), 100),
                Views.<TradeDto>textColumn("Total", trade -> Format.money(trade.totalPaid()), 90)));
        table.getItems().setAll(trades);
        table.setPrefHeight(160);
        return table;
    }
}
