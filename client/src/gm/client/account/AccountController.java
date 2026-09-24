package gm.client.account;

import gm.client.Format;
import gm.client.Views;
import gm.client.events.EventDetailView;
import gm.client.http.MarketServer;
import gm.client.main.MarketFeed;
import gm.client.main.Messenger;
import gm.client.trade.TradePanel;
import gm.dto.EventInfoDto;
import gm.dto.LedgerLineDto;
import gm.dto.OptionHoldingDto;
import gm.dto.ParticipationDto;
import gm.dto.UploadResultDto;
import gm.dto.UserDetailDto;
import gm.dto.UserDto;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The account tab: the logged-in user's own place in the market, laid out as the exercise 3 sketch
 * draws it.
 * <p>
 * Across the top, uploading an events file. On the left, everybody else — their name, balance and
 * whether they run an event, which is all the exercise lets one user see of another — and under them
 * the lines of this user's own account, with the balance and the means of loading funds. On the right,
 * every event and how this user stands in it, what they hold in the chosen one, and the same trade
 * panel the events tab uses.
 * <p>
 * Nothing here asks the server for anything on its own account except the upload and the deposit;
 * everything it shows comes from the feed, which already pulls it.
 */
public final class AccountController {

    private final MarketServer server;
    private final MarketFeed feed;
    private final Messenger messenger;
    private final TradePanel trade;

    private final BorderPane root = new BorderPane();
    private final Button uploadButton = new Button("Upload File");
    private final Label uploadedPath = new Label("No file uploaded yet.");
    private final ProgressIndicator uploading = new ProgressIndicator();

    private final ObservableList<UserDto> others = FXCollections.observableArrayList();
    private final TableView<LedgerLineDto> ledger = new TableView<>();
    private final Label balance = new Label();
    private final TextField amount = new TextField();

    private final Label title = new Label();
    private final Label state = new Label();
    private final TableView<EventRole> involvement = new TableView<>();
    private final ObservableList<EventRole> roles = FXCollections.observableArrayList();
    private final VBox partInEvent = new VBox(8);
    private EventRole shownPart;
    private boolean redrawing;

    public AccountController(MarketServer server, MarketFeed feed, Messenger messenger) {
        this.server = server;
        this.feed = feed;
        this.messenger = messenger;
        this.trade = new TradePanel(server, feed, messenger);
        root.setTop(uploadStrip());
        root.setCenter(body());
        feed.users().addListener((ignored, was, now) -> showOthers());
        feed.me().addListener((ignored, was, now) -> showMe());
        feed.events().addListener((ignored, was, now) -> showMe());
        showOthers();
        showMe();
    }

    public Node view() {
        return root;
    }

    private Node uploadStrip() {
        uploadButton.getStyleClass().add("primary-button");
        uploadButton.setId("uploadButton");
        uploadButton.setPrefWidth(140);
        uploadButton.setOnAction(ignored -> chooseAndUpload());
        uploadedPath.getStyleClass().add("file-path");
        uploadedPath.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(uploadedPath, Priority.ALWAYS);
        uploading.setPrefSize(22, 22);
        uploading.setVisible(false);
        HBox strip = new HBox(10, uploadButton, uploadedPath, uploading);
        strip.setAlignment(Pos.CENTER_LEFT);
        strip.setPadding(new Insets(10, 12, 6, 12));
        return strip;
    }

    private Node body() {
        SplitPane left = new SplitPane(othersPane(), accountPane());
        left.setOrientation(Orientation.VERTICAL);
        left.setDividerPositions(0.42);

        SplitPane body = new SplitPane(left, Views.scrolling(detailPane()));
        body.setDividerPositions(0.3);
        SplitPane.setResizableWithParent(left, Boolean.FALSE);
        return body;
    }

    private Node othersPane() {
        TableView<UserDto> table = Views.fitted(new TableView<>(others));
        table.setId("usersTable");
        table.setPlaceholder(new Label("Nobody else has logged in yet."));
        table.getColumns().addAll(List.of(
                Views.textColumn("Name", UserDto::name, 110),
                Views.<UserDto>textColumn("Balance", user -> Format.money(user.balance()), 80),
                Views.<UserDto>textColumn("Market maker", user -> user.marketMaker() ? "Yes" : "No", 90)));
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox pane = new VBox(6, Views.section("Other users"), table);
        pane.setPadding(new Insets(8, 8, 8, 12));
        return pane;
    }

    private Node accountPane() {
        ledger.setId("ledgerTable");
        ledger.setItems(feed.ledger());
        ledger.setPlaceholder(new Label("Nothing has happened in your account yet."));
        Views.fitted(ledger).getColumns().addAll(List.of(
                Views.<LedgerLineDto>textColumn("#", line -> String.valueOf(line.number()), 30),
                Views.wrappingColumn("Description", LedgerLineDto::description, 200),
                Views.<LedgerLineDto>textColumn("Amount", line -> Format.signedMoney(line.amount()), 80),
                Views.<LedgerLineDto>textColumn("Balance", line -> Format.money(line.balanceAfter()), 80)));
        // The newest line is the one worth seeing, so the table follows the account as it grows.
        feed.ledger().addListener((ListChangeListener<LedgerLineDto>) change ->
                ledger.scrollTo(feed.ledger().size() - 1));
        VBox.setVgrow(ledger, Priority.ALWAYS);

        balance.getStyleClass().add("balance");
        balance.setId("balance");
        amount.setPromptText("Amount");
        amount.setPrefWidth(90);
        amount.setId("depositAmount");
        amount.setOnAction(ignored -> deposit());
        Button load = new Button("Load funds");
        load.setId("depositButton");
        load.setOnAction(ignored -> deposit());
        HBox funds = new HBox(8, balance, spacer(), amount, load);
        funds.setAlignment(Pos.CENTER_LEFT);

        VBox pane = new VBox(6, Views.section("My account"), ledger, funds);
        pane.setPadding(new Insets(8, 8, 10, 12));
        return pane;
    }

    private Node detailPane() {
        title.getStyleClass().add("detail-title");
        HBox header = new HBox(10, title, state);
        header.setAlignment(Pos.CENTER_LEFT);

        Views.fitted(involvement).setId("involvementTable");
        involvement.setItems(roles);
        involvement.setPlaceholder(new Label("There are no events on the server yet."));
        involvement.getColumns().addAll(List.of(
                Views.<EventRole>textColumn("Event", role -> role.event().name(), 190),
                Views.<EventRole>textColumn("Status", role -> role.event().status(), 90),
                Views.<EventRole>textColumn("Type", role -> role.event().methodKind(), 90),
                Views.textColumn("My role", EventRole::role, 140),
                Views.textColumn("Holding", AccountController::holdingSummary, 170)));
        involvement.setPrefHeight(190);
        involvement.getSelectionModel().selectedItemProperty().addListener((ignored, was, now) -> {
            if (!redrawing) {
                showPartIn(now);
            }
        });

        VBox pane = new VBox(10, header, new Separator(),
                Views.section("Events participation and ownership"), involvement,
                Views.section("My part in the selected event"), partInEvent,
                Views.section("Act on the selected event"), trade.view());
        pane.setPadding(new Insets(12));
        return pane;
    }

    private void showOthers() {
        UserDetailDto me = feed.me().get();
        List<UserDto> everybodyElse = feed.users().get().stream()
                .filter(user -> me == null || !user.name().equals(me.name()))
                .toList();
        if (!everybodyElse.equals(others)) {
            others.setAll(everybodyElse);
        }
    }

    private void showMe() {
        UserDetailDto me = feed.me().get();
        if (me == null) {
            title.setText("Fetching your account...");
            return;
        }
        title.setText(me.name());
        state.setText(me.blocked() ? "(blocked — you have spent past zero)" : "");
        balance.setText("Balance " + Format.money(me.balance()));
        showOthers();

        EventRole wasSelected = involvement.getSelectionModel().getSelectedItem();
        List<EventRole> fresh = rolesOf(me, feed.events().get());
        redrawing = true;
        try {
            if (!fresh.equals(roles)) {
                roles.setAll(fresh);
            }
            if (wasSelected != null) {
                roles.stream()
                        .filter(role -> role.event().number() == wasSelected.event().number())
                        .findFirst()
                        .ifPresent(involvement.getSelectionModel()::select);
            }
        } finally {
            redrawing = false;
        }
        showPartIn(involvement.getSelectionModel().getSelectedItem());
    }

    /**
     * Where the user stands in every event on the server: what they run, what they hold, and what they
     * have not touched yet.
     * <p>
     * Every event is listed, not only the ones already taken part in. Taking part has to be able to
     * begin somewhere, and this table is where an event is chosen to act on.
     */
    private static List<EventRole> rolesOf(UserDetailDto me, List<EventInfoDto> events) {
        List<EventRole> roles = new ArrayList<>();
        for (EventInfoDto event : events) {
            ParticipationDto holding = me.participations().stream()
                    .filter(part -> part.event().number() == event.number())
                    .findFirst()
                    .orElse(null);
            roles.add(new EventRole(event, me.marketMakerOf().contains(event.name()), holding));
        }
        return roles;
    }

    private static String holdingSummary(EventRole role) {
        if (role.holding() == null) {
            return Format.NOTHING;
        }
        return role.holding().options().stream()
                .map(option -> option.optionName() + " " + Format.shares(option.shares()))
                .reduce((a, b) -> a + ", " + b)
                .orElse(Format.NOTHING);
    }

    /**
     * What the user's part in the chosen event amounts to. An LMSR event is described by the run of
     * purchases and sales made; an order book by what is held and what it cost. Profit or loss appears
     * once the event has been decided, since until then a nought would read as an answer.
     */
    private void showPartIn(EventRole role) {
        trade.show(role == null ? null : role.event());
        if (Objects.equals(role, shownPart) && !partInEvent.getChildren().isEmpty()) {
            return;
        }
        shownPart = role;
        partInEvent.getChildren().clear();
        if (role == null) {
            partInEvent.getChildren().add(new Label("Choose one of the events above."));
            return;
        }
        ParticipationDto part = role.holding();
        if (part == null) {
            partInEvent.getChildren().add(Views.wrapping(role.runsIt()
                    ? "You run this event but have not traded in it, so you hold nothing here."
                    : "You have not taken part in this event."));
            return;
        }
        EventInfoDto event = role.event();
        if (!"Order book".equals(event.methodKind())) {
            partInEvent.getChildren().addAll(
                    new Label("Everything you have bought and sold here, newest first."),
                    EventDetailView.tradeTable(part.trades()));
        }
        partInEvent.getChildren().addAll(holdingsTable(part), moneySummary(part, event));
    }

    private static TableView<OptionHoldingDto> holdingsTable(ParticipationDto part) {
        TableView<OptionHoldingDto> table = Views.fitted(new TableView<>());
        table.setPlaceholder(new Label("Nothing is held here."));
        table.getColumns().addAll(List.of(
                Views.textColumn("Option", OptionHoldingDto::optionName, 180),
                Views.<OptionHoldingDto>textColumn("Shares held",
                        option -> Format.shares(option.shares()), 110),
                Views.<OptionHoldingDto>textColumn("Paid", option -> Format.money(option.paidFor()), 110),
                Views.<OptionHoldingDto>textColumn("Worth now",
                        option -> Format.moneyOrNothing(option.currentValue()), 110)));
        table.getItems().setAll(part.options());
        table.setPrefHeight(70 + 28.0 * part.options().size());
        return table;
    }

    private static Node moneySummary(ParticipationDto part, EventInfoDto event) {
        if (event.winningOptionName() == null) {
            return Views.labelled("Commission paid", Format.money(part.commissionPaid()));
        }
        return Views.labelled(
                "Commission paid", Format.money(part.commissionPaid()),
                "Winning option", event.winningOptionName(),
                "Profit or loss", Format.signedMoney(part.netResult()));
    }

    /**
     * Uploads a file chosen from the user's computer. It is sent to the server and read there, which
     * takes as long as the network does, so the button waits and a spinner turns while it happens; the
     * exercise notes that no artificial delay is needed this time, because the wait is real.
     */
    private void chooseAndUpload() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an events file to upload");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Guess Market files", "*.xml"));
        File chosen = chooser.showOpenDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (chosen != null) {
            upload(chosen);
        }
    }

    private void upload(File chosen) {
        uploadButton.setDisable(true);
        uploading.setVisible(true);
        messenger.status("Uploading " + chosen.getName() + "...");
        server.upload(chosen, result -> {
            uploadFinished();
            uploadedPath.setText(chosen.getAbsolutePath());
            messenger.status(describe(result));
            feed.pullEverything();
        }, failure -> {
            uploadFinished();
            messenger.status(chosen.getName() + " was not added.");
            messenger.refused(failure);
        });
    }

    private void uploadFinished() {
        uploadButton.setDisable(false);
        uploading.setVisible(false);
    }

    private static String describe(UploadResultDto result) {
        int count = result.eventNames().size();
        return result.fileName() + " added " + count + (count == 1 ? " event" : " events") + ": "
                + String.join(", ", result.eventNames()) + ". You are the market maker of "
                + (count == 1 ? "it." : "all of them.");
    }

    private void deposit() {
        double wanted;
        try {
            wanted = Double.parseDouble(amount.getText().trim());
        } catch (NumberFormatException e) {
            messenger.refused(new MarketServer.Failure(400, "\"" + amount.getText().trim()
                    + "\" is not an amount. Please type a number, such as 500.", List.of()));
            return;
        }
        server.deposit(wanted, updated -> {
            amount.clear();
            messenger.status("You loaded " + Format.money(wanted) + ". Your balance is "
                    + Format.money(updated.balance()) + ".");
            feed.pullEverything();
        }, messenger::refused);
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
