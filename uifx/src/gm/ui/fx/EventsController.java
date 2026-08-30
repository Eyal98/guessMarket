package gm.ui.fx;

import gm.engine.api.GuessMarketEngine;
import gm.engine.api.GuessMarketException;
import gm.engine.api.dto.EventInfoDto;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
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
 * The events tab: every event on the left with the filters above them, and whichever one is selected
 * laid out on the right.
 * <p>
 * The filters are three groups of toggles, each with an "All" that is chosen to begin with, which is
 * what the requirements ask for. Filtering happens here rather than in the engine: the engine's job is
 * to say what is true, and which of that to show is a question about this screen.
 */
public final class EventsController {

    private static final String ALL = "All";

    private final GuessMarketEngine engine;
    private final EventDetailView detail;

    private final SplitPane root = new SplitPane();
    private final TableView<EventInfoDto> table = new TableView<>();
    private final ObservableList<EventInfoDto> shown = FXCollections.observableArrayList();
    private final Label summary = new Label();

    private final ToggleGroup methodFilter = new ToggleGroup();
    private final ToggleGroup statusFilter = new ToggleGroup();
    private final ToggleGroup commissionFilter = new ToggleGroup();

    public EventsController(GuessMarketEngine engine, EventDetailView detail) {
        this.engine = engine;
        this.detail = detail;
        build();
    }

    public Node view() {
        return root;
    }

    /** Rebuilds the list from the engine, keeping whichever event was being looked at. */
    public void refresh() {
        EventInfoDto wasSelected = table.getSelectionModel().getSelectedItem();
        shown.setAll(engine.isLoaded() ? engine.listEvents().stream().filter(passesFilters()).toList()
                : List.of());
        summary.setText(summaryText());
        if (wasSelected != null) {
            shown.stream()
                    .filter(event -> event.number() == wasSelected.number())
                    .findFirst()
                    .ifPresent(table.getSelectionModel()::select);
        }
        if (table.getSelectionModel().getSelectedItem() == null) {
            detail.showNothing();
        } else {
            showSelected();
        }
    }

    private void build() {
        table.setId("eventsTable");
        table.setItems(shown);
        table.setPlaceholder(new Label("No events to show."));
        table.getColumns().addAll(List.of(
                column("#", event -> String.valueOf(event.number()), 34),
                column("Name", EventInfoDto::name, 160),
                column("Status", EventInfoDto::status, 78),
                column("Type", EventInfoDto::methodKind, 82),
                column("Commission", event -> Format.percent(event.commissionPercent())
                        + " " + event.commissionType(), 112),
                // The event's own account is one of the columns the exercise asks for by name, so it
                // comes before the market maker, which is an addition of ours and may scroll instead.
                column("Account", event -> Format.money(event.accountBalance()), 82),
                column("Market maker", EventInfoDto::marketMakerName, 100)));
        table.getSelectionModel().selectedItemProperty().addListener((ignored, was, now) -> showSelected());
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
        if (!engine.isLoaded()) {
            return "No file loaded.";
        }
        int total = engine.listEvents().size();
        return shown.size() == total
                ? total + (total == 1 ? " event" : " events")
                : shown.size() + " of " + total + " events shown";
    }

    private void showSelected() {
        EventInfoDto selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            detail.showNothing();
            return;
        }
        try {
            detail.show(selected);
        } catch (GuessMarketException e) {
            detail.showMessage(e.getMessage());
        }
    }

    private static TableColumn<EventInfoDto, String> column(String title,
                                                            java.util.function.Function<EventInfoDto, String> value,
                                                            double width) {
        TableColumn<EventInfoDto, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(value.apply(row.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    /** Wraps anything that can outgrow its space, so a small window stays usable. */
    static ScrollPane scrolling(Node content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.setPannable(true);
        return pane;
    }
}
