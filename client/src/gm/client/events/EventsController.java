package gm.client.events;

import gm.client.Format;
import gm.client.Views;
import gm.client.http.MarketServer;
import gm.client.main.MarketFeed;
import gm.client.main.Messenger;
import gm.client.trade.TradePanel;
import gm.dto.EventInfoDto;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Predicate;

/**
 * The events tab: every event on the server on the left with the filters above them, and whichever
 * one is selected laid out on the right, with the means of acting on it.
 * <p>
 * The filters are three groups of toggles, each with an "All" that is chosen to begin with, which is
 * what the requirements ask for. Filtering happens here rather than on the server: the server's job is
 * to say what is true, and which of that to show is a question about this screen.
 * <p>
 * The list arrives from the server twice a second, but the table is only redrawn when the list has
 * actually changed, and it keeps whichever event was selected.
 */
public final class EventsController {

    private static final String ALL = "All";

    private final MarketFeed feed;
    private final EventDetailView detail;
    private final TradePanel trade;

    private final SplitPane root = new SplitPane();
    private final TableView<EventInfoDto> table = new TableView<>();
    private final ObservableList<EventInfoDto> shown = FXCollections.observableArrayList();
    private final Label summary = new Label();

    private final ToggleGroup methodFilter = new ToggleGroup();
    private final ToggleGroup statusFilter = new ToggleGroup();
    private final ToggleGroup commissionFilter = new ToggleGroup();

    /**
     * Set while the list is being replaced. Replacing a table's rows clears its selection for an
     * instant, and acting on that instant would blank the detail on the right every time any event on
     * the server changed.
     */
    private boolean redrawing;

    public EventsController(MarketServer server, MarketFeed feed, Messenger messenger) {
        this.feed = feed;
        this.trade = new TradePanel(server, feed, messenger);
        this.detail = new EventDetailView(server, feed, trade.view());
        build();
        feed.events().addListener((ignored, was, now) -> refresh());
        refresh();
    }

    public Node view() {
        return root;
    }

    /** Redraws the list from the latest the server said, keeping whichever event was being looked at. */
    private void refresh() {
        EventInfoDto wasSelected = table.getSelectionModel().getSelectedItem();
        List<EventInfoDto> passing = feed.events().get().stream().filter(passesFilters()).toList();
        redrawing = true;
        try {
            if (!passing.equals(shown)) {
                shown.setAll(passing);
            }
            if (wasSelected != null) {
                shown.stream()
                        .filter(event -> event.number() == wasSelected.number())
                        .findFirst()
                        .ifPresent(table.getSelectionModel()::select);
            }
        } finally {
            redrawing = false;
        }
        summary.setText(summaryText());
        showSelected();
    }

    private void build() {
        Views.fitted(table).setId("eventsTable");
        table.setItems(shown);
        table.setPlaceholder(new Label("No events yet. Upload an events file from the Account tab."));
        table.getColumns().addAll(List.of(
                Views.<EventInfoDto>textColumn("#", event -> String.valueOf(event.number()), 34),
                Views.textColumn("Name", EventInfoDto::name, 160),
                Views.textColumn("Status", EventInfoDto::status, 78),
                Views.textColumn("Type", EventInfoDto::methodKind, 82),
                Views.<EventInfoDto>textColumn("Commission", EventsController::commissionOf, 112),
                // The event's own account is one of the columns the exercise asks for by name, so it
                // comes before the market maker, which is an addition of ours and may scroll instead.
                Views.<EventInfoDto>textColumn("Account", event -> Format.money(event.accountBalance()), 82),
                Views.textColumn("Market maker", EventInfoDto::marketMakerName, 100)));
        table.getSelectionModel().selectedItemProperty().addListener((ignored, was, now) -> {
            if (!redrawing) {
                showSelected();
            }
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox left = new VBox(8, filterBar(), summary, table);
        left.setPadding(new Insets(10));
        root.getItems().addAll(left, detail.view());
        root.setDividerPositions(0.48);
        SplitPane.setResizableWithParent(left, Boolean.TRUE);
    }

    private Node filterBar() {
        FlowPane bar = new FlowPane(14, 8,
                filterGroup("Type", methodFilter, ALL, "LMSR", "Order book"),
                filterGroup("Status", statusFilter, ALL, "Not started", "Active", "Closed"),
                filterGroup("Commission", commissionFilter, ALL, "on-purchase", "on-close"));
        bar.setPadding(new Insets(2, 0, 2, 0));
        return bar;
    }

    /**
     * One group of toggles where exactly one is always chosen. Clicking the chosen one again would
     * otherwise leave nothing selected and quietly show everything, which looks like a bug.
     */
    private Node filterGroup(String caption, ToggleGroup group, String... choices) {
        HBox buttons = new HBox(4);
        buttons.setAlignment(Pos.CENTER_LEFT);
        for (String choice : choices) {
            ToggleButton button = new ToggleButton(choice);
            button.setToggleGroup(group);
            button.setUserData(choice);
            button.setOnAction(ignored -> refresh());
            if (ALL.equals(choice)) {
                button.setSelected(true);
            }
            buttons.getChildren().add(button);
        }
        group.selectedToggleProperty().addListener((ignored, was, now) -> {
            if (now == null && was != null) {
                was.setSelected(true);
            }
        });
        Label label = new Label(caption);
        label.getStyleClass().add("filter-caption");
        HBox captionedButtons = new HBox(6, label, buttons);
        captionedButtons.setAlignment(Pos.CENTER_LEFT);
        return captionedButtons;
    }

    private Predicate<EventInfoDto> passesFilters() {
        return event -> matches(methodFilter, event.methodKind())
                && matches(statusFilter, event.status())
                && matches(commissionFilter, event.commissionType());
    }

    private boolean matches(ToggleGroup group, String value) {
        Object chosen = group.getSelectedToggle() == null ? ALL : group.getSelectedToggle().getUserData();
        return ALL.equals(chosen) || chosen.equals(value);
    }

    private String summaryText() {
        int total = feed.events().get().size();
        if (total == 0) {
            return "No events on the server yet.";
        }
        return shown.size() == total
                ? total + (total == 1 ? " event" : " events")
                : shown.size() + " of " + total + " events shown";
    }

    private static String commissionOf(EventInfoDto event) {
        return Format.percent(event.commissionPercent()) + " " + event.commissionType();
    }

    private void showSelected() {
        EventInfoDto selected = table.getSelectionModel().getSelectedItem();
        detail.watch(selected);
        trade.show(selected);
    }
}
