package gm.ui.fx;

import gm.engine.api.GuessMarketEngine;
import gm.engine.api.GuessMarketException;
import gm.engine.api.dto.LoadResultDto;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;

/**
 * The shell around everything: choosing a file, loading it, and the two tabs.
 * <p>
 * Loading runs on a background task rather than on the screen's own thread. The reading itself is
 * quick, so a short pause is added deliberately — without it the progress bar would appear and vanish
 * in the same instant and nobody would see that anything had happened.
 */
public final class MainController {

    /** Long enough for the progress to be visible, short enough not to be a nuisance. */
    private static final long SIMULATED_WORK_MILLIS = 1400;
    private static final int PROGRESS_STEPS = 20;

    @FXML private Button loadButton;
    @FXML private Label loadedPathLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressBar loadProgress;
    @FXML private ComboBox<Skin> skinChooser;
    @FXML private TabPane tabs;
    @FXML private Tab eventsTab;
    @FXML private Tab usersTab;

    private GuessMarketEngine engine;
    private EventsController events;
    private UsersController users;

    /** Called once, after the screen is built, to give it the engine and its two panes. */
    public void start(GuessMarketEngine engine, EventsController events, UsersController users) {
        this.engine = engine;
        this.events = events;
        this.users = users;
        eventsTab.setContent(events.view());
        usersTab.setContent(users.view());
        offerTheSkins();
    }

    /**
     * Fills the skin chooser and lets it redress the screen. It starts on the plain look, because a
     * bonus is meant to be switched on deliberately rather than found already running.
     */
    private void offerTheSkins() {
        skinChooser.getItems().setAll(Skin.values());
        skinChooser.setValue(Skin.DEFAULT);
        skinChooser.valueProperty().addListener((ignored, was, now) -> {
            if (now != null && loadButton.getScene() != null) {
                now.applyTo(loadButton.getScene());
            }
        });
    }

    @FXML
    private void onLoadFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose an events file");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Guess Market files", "*.xml"));
        File chosen = chooser.showOpenDialog(window());
        if (chosen != null) {
            load(chosen);
        }
    }

    private void load(File file) {
        Task<LoadResultDto> loading = new Task<>() {
            @Override
            protected LoadResultDto call() throws InterruptedException {
                for (int step = 1; step <= PROGRESS_STEPS; step++) {
                    Thread.sleep(SIMULATED_WORK_MILLIS / PROGRESS_STEPS);
                    updateProgress(step, PROGRESS_STEPS);
                }
                return engine.loadEventsFile(file.getAbsolutePath());
            }
        };

        loadProgress.progressProperty().bind(loading.progressProperty());
        showBusy(true);

        loading.setOnSucceeded(ignored -> {
            showBusy(false);
            LoadResultDto result = loading.getValue();
            loadedPathLabel.setText(file.getAbsolutePath());
            statusLabel.setText(result.eventsLoaded() + " events and "
                    + engine.listUsers().size() + " users loaded. Opening every event would cost "
                    + Format.money(result.costOfOpeningEverything()) + ".");
            refreshEverything();
        });
        loading.setOnFailed(ignored -> {
            showBusy(false);
            Throwable cause = loading.getException();
            statusLabel.setText("The file was not loaded.");
            reportFailure(cause);
        });

        Thread worker = new Thread(loading, "guess-market-file-loader");
        worker.setDaemon(true);
        worker.start();
    }

    private void showBusy(boolean busy) {
        loadProgress.setVisible(busy);
        loadProgress.setManaged(busy);
        loadButton.setDisable(busy);
        if (!busy) {
            loadProgress.progressProperty().unbind();
        }
    }

    /** Redraws both tabs. Anything either of them does can change what the other shows. */
    void refreshEverything() {
        events.refresh();
        users.refresh();
    }

    /**
     * Shows why something could not be done. A rejected file can carry a whole list of faults, so it
     * goes in a scrolling box rather than a single line that would be cut off.
     */
    void reportFailure(Throwable cause) {
        String message = cause instanceof GuessMarketException || cause.getMessage() != null
                ? cause.getMessage()
                : cause.toString();
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(window());
        alert.setTitle("Guess Market");
        alert.setHeaderText("That could not be done");
        TextArea detail = new TextArea(message);
        detail.setEditable(false);
        detail.setWrapText(true);
        detail.setPrefRowCount(Math.min(14, message.split("\n").length + 2));
        alert.getDialogPane().setContent(detail);
        alert.getDialogPane().setPrefWidth(640);
        alert.showAndWait();
    }

    void setStatus(String message) {
        statusLabel.setText(message);
    }

    private Window window() {
        return loadButton.getScene() == null ? null : loadButton.getScene().getWindow();
    }

    /** Runs something on the screen thread, whichever thread noticed it needed doing. */
    static void onScreenThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
