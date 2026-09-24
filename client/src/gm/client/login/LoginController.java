package gm.client.login;

import gm.client.http.MarketServer;
import gm.dto.UserDetailDto;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;

import java.util.function.Consumer;

/**
 * The login screen. A name is all it asks for; the server decides whether the name is free, and a
 * refusal is shown right here so another can be tried at once.
 */
public final class LoginController {

    @FXML private TextField nameField;
    @FXML private Button loginButton;
    @FXML private ProgressIndicator waiting;
    @FXML private Label problemLabel;
    @FXML private Label serverLabel;

    private MarketServer server;
    private Consumer<UserDetailDto> loggedIn;

    /**
     * @param loggedIn what to do once the server has accepted the name
     * @param message  why the user is here again, such as having logged out, or null the first time
     */
    public void start(MarketServer server, Consumer<UserDetailDto> loggedIn, String message) {
        this.server = server;
        this.loggedIn = loggedIn;
        serverLabel.setText("Server: " + server.address());
        problemLabel.setText(message == null ? "" : message);
        Platform.runLater(nameField::requestFocus);
    }

    @FXML
    private void onLogIn() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            problemLabel.setText("Please enter a name.");
            return;
        }
        waitForServer(true);
        problemLabel.setText("");
        server.logIn(name, user -> {
            waitForServer(false);
            loggedIn.accept(user);
        }, failure -> {
            waitForServer(false);
            problemLabel.setText(failure.message());
            nameField.selectAll();
            nameField.requestFocus();
        });
    }

    private void waitForServer(boolean waitingForIt) {
        loginButton.setDisable(waitingForIt);
        nameField.setDisable(waitingForIt);
        waiting.setVisible(waitingForIt);
    }
}
