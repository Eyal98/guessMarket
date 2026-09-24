package gm.client.main;

import gm.client.http.MarketServer;
import gm.dto.UserDetailDto;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;

/**
 * The shell around everything once somebody has logged in: who they are, the tabs, and the status
 * line. It is also where the rest of the screen reports to, since it owns both the status line and
 * the window a refusal is shown over.
 */
public final class MainController implements Messenger {

    @FXML private Label userLabel;
    @FXML private Label statusLabel;
    @FXML private Button logoutButton;
    @FXML private TabPane tabs;
    @FXML private Tab eventsTab;
    @FXML private Tab accountTab;
    @FXML private Tab chatTab;

    private Runnable logOut;
    private Runnable sessionEnded;

    /**
     * Called once, after the screen is built, to give it its tabs and say what leaving means.
     *
     * @param logOut       what the Log out button does
     * @param sessionEnded what to do when the server no longer knows this session
     */
    public void start(UserDetailDto me, Node events, Node account, Node chat, Runnable logOut,
                      Runnable sessionEnded) {
        this.logOut = logOut;
        this.sessionEnded = sessionEnded;
        userLabel.setText("Logged in as " + me.name());
        eventsTab.setContent(events);
        accountTab.setContent(account);
        chatTab.setContent(chat);
        // The exercise sends a user to the events screen once they have logged in.
        tabs.getSelectionModel().select(eventsTab);
        statusLabel.setText("Welcome, " + me.name() + ". Upload an events file or load funds from the"
                + " Account tab.");
    }

    @FXML
    private void onLogOut() {
        logoutButton.setDisable(true);
        logOut.run();
    }

    @Override
    public void status(String message) {
        statusLabel.setText(message);
    }

    /**
     * Shows why something could not be done. A refused file can carry a whole list of faults, so the
     * explanation goes in a scrolling box rather than a single line that would be cut off.
     */
    @Override
    public void refused(MarketServer.Failure failure) {
        if (failure.sessionLost()) {
            sessionEnded.run();
            return;
        }
        warn("That could not be done", failure.message());
    }

    @Override
    public void warn(String headline, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        if (statusLabel.getScene() != null) {
            alert.initOwner(statusLabel.getScene().getWindow());
        }
        alert.setTitle("Guess Market");
        alert.setHeaderText(headline);
        TextArea detail = new TextArea(message);
        detail.setEditable(false);
        detail.setWrapText(true);
        detail.setPrefRowCount(Math.min(14, message.split("\n").length + 2));
        alert.getDialogPane().setContent(detail);
        alert.getDialogPane().setPrefWidth(640);
        alert.showAndWait();
    }
}
