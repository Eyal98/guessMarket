package gm.ui.fx;

import gm.engine.api.GuessMarketEngine;
import gm.engine.api.GuessMarketException;
import gm.engine.api.dto.NewEventDto;
import gm.engine.api.dto.NewLmsrDto;
import gm.engine.api.dto.NewMethodDto;
import gm.engine.api.dto.NewOrderBookDto;
import gm.engine.api.dto.UserDto;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * The form for making a new event, run by whoever is selected in the users tab.
 * <p>
 * It lives in the users tab for the same reason trading does: the person acting is never asked for,
 * it is whoever is selected, so there is no way to fill this in and then be surprised about who ends
 * up running the result.
 * <p>
 * The engine is asked to create the event while the form is still open. If it refuses, the message
 * is shown and the form stays exactly as it was typed, because throwing away somebody's work over
 * one mistyped number would be a poor way to report a mistake.
 */
final class CreateEventDialog {

    private static final int SMALLEST_SENSIBLE_OUTCOMES = 2;
    private static final int FORM_WIDTH = 520;
    private static final int FORM_HEIGHT = 520;
    private static final String FORMULA = "LMSR";

    private final GuessMarketEngine engine;
    private final UserDto creator;
    private final MainController main;

    private final TextField name = new TextField();
    private final TextArea description = new TextArea();
    private final Spinner<Integer> commission = new Spinner<>(0, 90, 5);
    private final ChoiceBox<String> timing = new ChoiceBox<>();
    private final ChoiceBox<String> kind = new ChoiceBox<>();
    private final VBox outcomes = new VBox(6);

    private final Spinner<Integer> liquidity = new Spinner<>(1, 1_000_000, 100);
    private final Spinner<Integer> initial = new Spinner<>(0, 10_000_000, 100);
    private final Spinner<Integer> baseValue = new Spinner<>(1, 1_000, 1);
    private final CheckBox allowMint =
            new CheckBox("Two opposing buyers may create new shares between them");
    private final VBox lmsrFields = new VBox(8);
    private final VBox bookFields = new VBox(8);

    private CreateEventDialog(GuessMarketEngine engine, UserDto creator, MainController main) {
        this.engine = engine;
        this.creator = creator;
        this.main = main;
    }

    /**
     * Shows the form and, if it is filled in and accepted, creates the event.
     *
     * @return the new event's number, or empty if the form was abandoned
     */
    static OptionalInt askFor(Window owner, GuessMarketEngine engine, UserDto creator,
                              MainController main) {
        return new CreateEventDialog(engine, creator, main).show(owner);
    }

    private OptionalInt show(Window owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Guess Market");
        dialog.setHeaderText("A new event, run by " + creator.name());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(form());
        dialog.setResizable(true);
        if (owner != null && owner.getScene() != null) {
            dialog.getDialogPane().getStylesheets().setAll(owner.getScene().getStylesheets());
        }

        int[] created = {0};
        Button accept = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        accept.setText("Create it");
        accept.addEventFilter(ActionEvent.ACTION, pressed -> {
            try {
                created[0] = engine.createEvent(creator.number(), describedEvent());
            } catch (GuessMarketException | IllegalArgumentException refused) {
                main.reportFailure(refused);
                pressed.consume();
            }
        });

        dialog.showAndWait();
        return created[0] == 0 ? OptionalInt.empty() : OptionalInt.of(created[0]);
    }

    private ScrollPane form() {
        name.setId("newEventName");
        description.setPrefRowCount(2);
        description.setWrapText(true);
        timing.getItems().setAll("on-purchase", "on-close");
        timing.getSelectionModel().selectFirst();

        for (int i = 0; i < SMALLEST_SENSIBLE_OUTCOMES; i++) {
            addOutcome();
        }
        Button another = new Button("Add another outcome");
        another.setOnAction(ignored -> addOutcome());

        lmsrFields.getChildren().setAll(
                new Label("The formula prices the outcomes against a pot the market maker puts up."),
                labelled("Liquidity (b)", liquidity));
        bookFields.getChildren().setAll(
                new Label("People trade with each other. The market maker stocks the books to begin with."),
                labelled("Initial investment", initial),
                labelled("A whole pair is worth (d)", baseValue),
                allowMint);

        kind.getItems().setAll(FORMULA, "Order book");
        kind.getSelectionModel().selectFirst();
        kind.valueProperty().addListener((ignored, was, now) -> showFieldsFor(now));
        showFieldsFor(kind.getValue());

        VBox everything = new VBox(10,
                labelled("Name", name),
                labelled("Description", description),
                labelled("Commission (%)", commission),
                labelled("Charged", timing),
                EventDetailView.section("Outcomes"),
                new Label("At least " + SMALLEST_SENSIBLE_OUTCOMES + ", and every one needs a name."),
                outcomes, another,
                EventDetailView.section("How it is traded"),
                labelled("Method", kind),
                lmsrFields, bookFields);
        everything.setPadding(new Insets(4));
        everything.setPrefWidth(FORM_WIDTH);

        ScrollPane scrolling = new ScrollPane(everything);
        scrolling.setFitToWidth(true);
        scrolling.setPrefHeight(FORM_HEIGHT);
        return scrolling;
    }

    /** Only the fields belonging to the chosen method are shown; the others take up no room at all. */
    private void showFieldsFor(String chosen) {
        boolean formula = FORMULA.equals(chosen);
        lmsrFields.setVisible(formula);
        lmsrFields.setManaged(formula);
        bookFields.setVisible(!formula);
        bookFields.setManaged(!formula);
    }

    private void addOutcome() {
        TextField field = new TextField();
        field.setId("newEventOutcome" + outcomes.getChildren().size());
        HBox.setHgrow(field, Priority.ALWAYS);
        Button remove = new Button("Remove");
        HBox row = new HBox(6, field, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        remove.setOnAction(ignored -> {
            if (outcomes.getChildren().size() > SMALLEST_SENSIBLE_OUTCOMES) {
                outcomes.getChildren().remove(row);
            }
        });
        outcomes.getChildren().add(row);
    }

    private NewEventDto describedEvent() {
        List<String> outcomeNames = new ArrayList<>();
        for (Node row : outcomes.getChildren()) {
            outcomeNames.add(((TextField) ((HBox) row).getChildren().get(0)).getText());
        }
        return new NewEventDto(name.getText(), description.getText(), commission.getValue(),
                timing.getValue(), outcomeNames, chosenMethod());
    }

    private NewMethodDto chosenMethod() {
        return FORMULA.equals(kind.getValue())
                ? new NewLmsrDto(liquidity.getValue())
                : new NewOrderBookDto(initial.getValue(), baseValue.getValue(), allowMint.isSelected());
    }

    private static HBox labelled(String caption, Node field) {
        Label label = new Label(caption);
        label.setMinWidth(170);
        label.getStyleClass().add("caption");
        HBox.setHgrow(field, Priority.ALWAYS);
        HBox row = new HBox(8, label, field);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
