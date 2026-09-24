package gm.ui.fx;

import gm.engine.api.GuessMarketEngine;
import gm.engine.api.GuessMarketException;
import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.OptionHoldingDto;
import gm.engine.api.dto.ParticipationDto;
import gm.engine.api.dto.TradeDto;
import gm.engine.api.dto.UserDetailDto;
import gm.engine.api.dto.UserDto;
import gm.engine.model.orderbook.OrderSide;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The users tab, and the place trading actually happens.
 * <p>
 * That is what the supplied sketch shows: a narrow list of people on the left, and on the right the
 * one who is selected — their money, the events they are involved in, and underneath, the panel for
 * acting on whichever of those events is picked. Trading from here rather than from the events tab is
 * what makes "who is doing this" unambiguous: it is whoever is selected.
 */
public final class UsersController {

    private final GuessMarketEngine engine;
    private final MainController main;
    private final Animations animations;

    private final SplitPane root = new SplitPane();
    private final TableView<UserDto> users = new TableView<>();
    private final ObservableList<UserDto> userRows = FXCollections.observableArrayList();

    private final Label userName = new Label();
    private final Label userBalance = new Label();
    private final Label userState = new Label();
    private final TableView<EventRole> involvement = new TableView<>();
    private final ObservableList<EventRole> involvementRows = FXCollections.observableArrayList();
    /** The whole right hand side, kept so a change of user can be seen to happen. */
    private final VBox whoIsSelected = new VBox(10);
    private final VBox involvementDetail = new VBox(8);
    private final VBox actions = new VBox(8);
    private final VBox moneyChart = new VBox(8);

    /** One line of the "events participation \ owner" table: an event and how this user stands in it. */
    public record EventRole(EventInfoDto event, boolean runsIt, ParticipationDto holding) {
        String role() {
            if (runsIt) {
                return holding == null ? "Market maker" : "Market maker, trading";
            }
            return holding == null ? "Not taken part yet" : "Trading";
        }
    }

    public UsersController(GuessMarketEngine engine, MainController main, Animations animations) {
        this.engine = engine;
        this.main = main;
        this.animations = animations;
        build();
    }

    public Node view() {
        return root;
    }

    public void refresh() {
        UserDto wasSelected = users.getSelectionModel().getSelectedItem();
        userRows.setAll(engine.isLoaded() ? engine.listUsers() : List.of());
        if (wasSelected != null) {
            userRows.stream()
                    .filter(user -> user.number() == wasSelected.number())
                    .findFirst()
                    .ifPresent(users.getSelectionModel()::select);
        }
        showSelectedUser();
    }

    private void build() {
        users.setId("usersTable");
        users.setItems(userRows);
        users.setPlaceholder(new Label("No users loaded."));
        users.getColumns().addAll(List.of(
                column("#", user -> String.valueOf(user.number()), 36),
                column("Name", UserDto::name, 110),
                column("Balance", user -> Format.money(user.balance()), 90)));
        users.getSelectionModel().selectedItemProperty().addListener((ignored, was, now) -> {
            showSelectedUser();
            // Only on a change of person, not on every redraw: a panel that flashed after each
            // trade would be a nuisance rather than a signal.
            animations.play(Animations.Motion.APPEARING, whoIsSelected);
        });
        VBox.setVgrow(users, Priority.ALWAYS);
        VBox left = new VBox(8, EventDetailView.section("Users"), users);
        left.setPadding(new Insets(10));
        left.setMinWidth(240);

        userName.getStyleClass().add("detail-title");
        userBalance.getStyleClass().add("balance");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button createEvent = new Button("Create an event");
        createEvent.setOnAction(ignored -> createEventForSelectedUser());
        HBox header = new HBox(10, userName, userState, spacer, createEvent, userBalance);
        header.setAlignment(Pos.CENTER_LEFT);

        involvement.setId("involvementTable");
        involvement.setPlaceholder(new Label("This user has not taken part in anything yet."));
        involvement.getColumns().addAll(List.of(
                EventDetailView.<EventRole>textColumn("Event", role -> role.event().name(), 190),
                EventDetailView.<EventRole>textColumn("Status", role -> role.event().status(), 90),
                EventDetailView.<EventRole>textColumn("Type", role -> role.event().methodKind(), 90),
                EventDetailView.<EventRole>textColumn("Role", EventRole::role, 140),
                EventDetailView.<EventRole>textColumn("Holding", UsersController::holdingSummary, 160)));
        involvement.setItems(involvementRows);
        involvement.setPrefHeight(190);
        involvement.getSelectionModel().selectedItemProperty()
                .addListener((ignored, was, now) -> {
                    showInvolvementIn(now);
                    showActionsFor(now);
                    animations.play(Animations.Motion.APPEARING, involvementDetail);
                    animations.play(Animations.Motion.APPEARING, actions);
                });

        whoIsSelected.getChildren().setAll(header, new Separator(),
                EventDetailView.section("Events participation and ownership"), involvement,
                EventDetailView.section("This user's part in the selected event"), involvementDetail,
                EventDetailView.section("Act on the selected event"), actions,
                moneyChart);
        whoIsSelected.setPadding(new Insets(12));

        ScrollPane scrollingRight = new ScrollPane(whoIsSelected);
        scrollingRight.setId("userDetail");
        scrollingRight.setFitToWidth(true);
        scrollingRight.setPannable(true);

        root.getItems().addAll(left, scrollingRight);
        root.setDividerPositions(0.24);
    }

    /**
     * Opens the form for a new event run by whoever is selected, and shows the result once it exists.
     * <p>
     * The creator is not asked for anywhere in the form. It is the selected user, for the same reason
     * trading works that way: it leaves no room to wonder who ends up running the thing.
     */
    private void createEventForSelectedUser() {
        UserDto creator = users.getSelectionModel().getSelectedItem();
        if (creator == null) {
            main.setStatus("Choose whose event it is first.");
            return;
        }
        CreateEventDialog.askFor(root.getScene() == null ? null : root.getScene().getWindow(),
                        engine, creator, main)
                .ifPresent(number -> {
                    main.refreshEverything();
                    main.setStatus(creator.name() + " created event " + number
                            + ". It is waiting to be opened.");
                });
    }

    private void showSelectedUser() {
        UserDto selected = users.getSelectionModel().getSelectedItem();
        if (selected == null || !engine.isLoaded()) {
            userName.setText("No user selected");
            userState.setText("");
            userBalance.setText("");
            involvementRows.clear();
            involvementDetail.getChildren().clear();
            actions.getChildren().clear();
            moneyChart.getChildren().clear();
            return;
        }
        UserDetailDto detail = engine.userDetail(selected.number());
        userName.setText(detail.name());
        userBalance.setText("Balance " + Format.money(detail.balance()));
        userState.setText(detail.blocked() ? "(blocked — has spent past zero)" : "");

        EventRole wasSelected = involvement.getSelectionModel().getSelectedItem();
        involvementRows.setAll(rolesOf(detail));
        if (wasSelected != null) {
            involvementRows.stream()
                    .filter(role -> role.event().number() == wasSelected.event().number())
                    .findFirst()
                    .ifPresent(involvement.getSelectionModel()::select);
        }
        showInvolvementIn(involvement.getSelectionModel().getSelectedItem());
        showActionsFor(involvement.getSelectionModel().getSelectedItem());
        moneyChart.getChildren().setAll(Charts.titled("How " + detail.name() + "'s money has moved",
                Charts.balanceChart(engine.balanceHistory(selected.number()))));
    }

    /**
     * Where this user stands in every event: what they run, what they hold, and what they have not
     * touched yet.
     * <p>
     * Every event is listed, not only the ones already taken part in. Taking part has to be able to
     * begin somewhere, and this table is where an event is chosen to act on — listing only events
     * already joined would mean nobody could ever place a first order, leaving the market makers and
     * the existing holders as the only people able to trade at all. The role column says plainly
     * which events this person is actually in.
     */
    private List<EventRole> rolesOf(UserDetailDto detail) {
        List<EventRole> roles = new ArrayList<>();
        for (EventInfoDto event : engine.listEvents()) {
            boolean runsIt = detail.marketMakerOf().contains(event.name());
            ParticipationDto holding = detail.participations().stream()
                    .filter(part -> part.event().number() == event.number())
                    .findFirst()
                    .orElse(null);
            roles.add(new EventRole(event, runsIt, holding));
        }
        return roles;
    }

    private static String holdingSummary(EventRole role) {
        if (role.holding() == null) {
            return "—";
        }
        return role.holding().options().stream()
                .map(option -> option.optionName() + " " + Format.shares(option.shares()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("—");
    }

    /**
     * What this user's part in the chosen event actually amounts to.
     * <p>
     * The two kinds of event are asked different questions, because different things are worth
     * knowing about each. On an LMSR event what matters is the run of purchases and sales this
     * person made; on an order book it is what they are left holding and what they paid for it.
     * Both are asked what the commission has cost them, and once an event has been decided both are
     * told which option won and whether they came out ahead.
     */
    private void showInvolvementIn(EventRole role) {
        involvementDetail.getChildren().clear();
        if (role == null) {
            involvementDetail.getChildren().add(new Label("Choose one of the events above."));
            return;
        }
        ParticipationDto part = role.holding();
        if (part == null) {
            involvementDetail.getChildren().add(new Label(role.runsIt()
                    ? "This user runs the event but has not traded in it, so they hold nothing here."
                    : "This user has not taken part in this event."));
            return;
        }
        EventInfoDto event = role.event();
        if ("Order book".equals(event.methodKind())) {
            involvementDetail.getChildren().addAll(holdingsTable(part), moneySummary(part, event));
            return;
        }
        involvementDetail.getChildren().addAll(
                new Label("Everything this user has bought and sold here, newest first."),
                EventDetailView.tradeTable(part.trades()),
                moneySummary(part, event));
        if (event.winningOptionName() != null) {
            involvementDetail.getChildren().add(holdingsTable(part));
        }
    }

    /** What is held in each option, and what was paid for it. */
    private TableView<OptionHoldingDto> holdingsTable(ParticipationDto part) {
        TableView<OptionHoldingDto> table = new TableView<>();
        table.setPlaceholder(new Label("Nothing is held here."));
        table.getColumns().addAll(List.of(
                EventDetailView.<OptionHoldingDto>textColumn("Option", OptionHoldingDto::optionName, 180),
                EventDetailView.<OptionHoldingDto>textColumn("Shares held",
                        option -> Format.shares(option.shares()), 110),
                EventDetailView.<OptionHoldingDto>textColumn("Paid",
                        option -> Format.money(option.paidFor()), 110),
                EventDetailView.<OptionHoldingDto>textColumn("Worth now",
                        option -> Format.moneyOrNothing(option.currentValue()), 110)));
        table.getItems().setAll(part.options());
        table.setPrefHeight(110);
        return table;
    }

    /**
     * The money side of taking part. Profit and loss only appears once the event has been decided,
     * since until then there is no answer to give and a nought would read as one.
     */
    private Node moneySummary(ParticipationDto part, EventInfoDto event) {
        if (event.winningOptionName() == null) {
            return EventDetailView.labelled("Commission paid", Format.money(part.commissionPaid()));
        }
        return EventDetailView.labelled(
                "Commission paid", Format.money(part.commissionPaid()),
                "Winning option", event.winningOptionName(),
                "Profit or loss", Format.money(part.netResult()));
    }

    private void showActionsFor(EventRole role) {
        actions.getChildren().clear();
        UserDto user = users.getSelectionModel().getSelectedItem();
        if (role == null || user == null) {
            actions.getChildren().add(new Label("Choose one of the events above."));
            return;
        }
        if (user.blocked()) {
            actions.getChildren().add(new Label(
                    user.name() + " has spent past zero and can take no further part in the market."));
            return;
        }
        EventInfoDto event = role.event();
        if ("Not started".equals(event.status())) {
            actions.getChildren().add(role.runsIt()
                    ? openPanel(event, user)
                    : new Label("This event has not been started by its market maker yet."));
            return;
        }
        if ("Closed".equals(event.status())) {
            actions.getChildren().add(new Label("This event is closed. Nothing more can be done here."));
            return;
        }
        actions.getChildren().add("Order book".equals(event.methodKind())
                ? orderPanel(event, user)
                : lmsrPanel(event, user));
        if (role.runsIt()) {
            actions.getChildren().add(closePanel(event, user));
        }
    }

    private Node openPanel(EventInfoDto event, UserDto user) {
        Button open = new Button("Open this event");
        open.getStyleClass().add("primary-button");
        open.setOnAction(ignored -> attempt(() -> {
            engine.openEvent(event.number(), user.number());
            main.setStatus(user.name() + " opened \"" + event.name() + "\".");
        }));
        return new VBox(6, new Label("Opening this event will cost " + user.name()
                + " whatever it takes to fund it, out of their own account."), open);
    }

    private Node lmsrPanel(EventInfoDto event, UserDto user) {
        ChoiceBox<String> option = optionChoice(event);
        option.setId("tradeOption");
        Spinner<Integer> quantity = quantitySpinner();
        quantity.setId("tradeQuantity");

        Button buy = new Button("Buy");
        buy.getStyleClass().add("primary-button");
        buy.setOnAction(ignored -> attempt(() -> {
            long wanted = quantityIn(quantity);
            engine.buyShares(event.number(), user.number(),
                    option.getSelectionModel().getSelectedIndex() + 1, wanted);
            main.setStatus(user.name() + " bought " + wanted + " shares.");
        }));

        Button sell = new Button("Sell");
        sell.setOnAction(ignored -> attempt(() -> {
            long offered = quantityIn(quantity);
            engine.sellShares(event.number(), user.number(),
                    option.getSelectionModel().getSelectedIndex() + 1, offered);
            main.setStatus(user.name() + " sold " + offered + " shares.");
        }));

        HBox row = new HBox(8, new Label("Option"), option, new Label("Shares"), quantity, buy, sell);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, new Label("Shares are bought from and sold back to the event itself."), row);
    }

    private Node orderPanel(EventInfoDto event, UserDto user) {
        ChoiceBox<String> option = optionChoice(event);
        option.setId("tradeOption");
        ChoiceBox<OrderSide> side = new ChoiceBox<>(
                FXCollections.observableArrayList(OrderSide.BUY, OrderSide.SELL));
        side.setId("tradeSide");
        side.getSelectionModel().selectFirst();
        Spinner<Integer> quantity = quantitySpinner();
        quantity.setId("tradeQuantity");
        TextField price = new TextField("0.50");
        price.setId("tradePrice");
        price.setPrefWidth(80);

        Button place = new Button("Place order");
        place.getStyleClass().add("primary-button");
        place.setOnAction(ignored -> attempt(() -> {
            double asked = parsePrice(price.getText());
            List<?> trades = engine.submitOrder(event.number(), user.number(),
                    option.getSelectionModel().getSelectedIndex() + 1,
                    side.getValue(), quantityIn(quantity), asked);
            main.setStatus(trades.isEmpty()
                    ? "The order is waiting in the book."
                    : "The order went through in " + trades.size() + " trade(s).");
        }));

        HBox row = new HBox(8, new Label("Option"), option, new Label("Side"), side,
                new Label("Shares"), quantity, new Label("Price"), price, place);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, new Label("Orders meet other people. Anything unmatched waits in the book."), row);
    }

    private Node closePanel(EventInfoDto event, UserDto user) {
        ChoiceBox<String> option = optionChoice(event);
        option.setId("closeOption");
        Button close = new Button("Close on this outcome");
        close.setOnAction(ignored -> attempt(() -> {
            engine.closeEvent(event.number(), user.number(),
                    option.getSelectionModel().getSelectedIndex() + 1);
            main.setStatus("\"" + event.name() + "\" is closed. Holders of the winning option were paid.");
        }));
        HBox row = new HBox(8, new Label("Winning option"), option, close);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(6, new Separator(),
                new Label("As market maker, " + user.name() + " decides this event. This cannot be undone."),
                row);
    }

    private ChoiceBox<String> optionChoice(EventInfoDto event) {
        ChoiceBox<String> choice = new ChoiceBox<>(
                FXCollections.observableArrayList(event.optionNames()));
        choice.getSelectionModel().selectFirst();
        return choice;
    }

    private Spinner<Integer> quantitySpinner() {
        Spinner<Integer> spinner = new Spinner<>(1, 1_000_000, 10);
        spinner.setEditable(true);
        spinner.setPrefWidth(100);
        return spinner;
    }

    /**
     * The number in the box, including one that has been typed but not entered.
     * <p>
     * A JavaFX spinner keeps its value and its text apart: typing over the number changes only the
     * text, and the value stays behind until Enter is pressed or the box loses the focus. Somebody
     * who types 100 and goes straight to Buy would otherwise buy the ten that were there before,
     * be told they bought ten, and have no reason to suspect the box had ignored them.
     * Incrementing by nothing is the documented way to make a spinner take what it has been given.
     */
    private static long quantityIn(Spinner<Integer> spinner) {
        spinner.increment(0);
        return spinner.getValue();
    }

    private double parsePrice(String text) {
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + text.trim()
                    + "\" is not a price. Please type a number, such as 0.50.");
        }
    }

    /**
     * Carries an action out, tells the user if it could not be done, and redraws either way.
     * <p>
     * This is the one place every action passes through, which makes it the right place for money
     * that has just moved to swell and settle. A refusal says so in words instead, in a dialogue the
     * user has to dismiss, which is a plainer answer than a panel twitching behind it.
     */
    private void attempt(Runnable action) {
        boolean worked = true;
        try {
            action.run();
        } catch (GuessMarketException | IllegalArgumentException e) {
            worked = false;
            main.reportFailure(e);
        }
        main.refreshEverything();
        if (worked) {
            animations.play(Animations.Motion.PULSING, userBalance);
        }
    }

    private static TableColumn<UserDto, String> column(String title, Function<UserDto, String> value,
                                                       double width) {
        TableColumn<UserDto, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(value.apply(row.getValue())));
        column.setPrefWidth(width);
        return column;
    }
}
