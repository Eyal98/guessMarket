package gm.ui.fx;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * The JavaFX application. At this stage it only proves that the packaged program starts and that
 * JavaFX is found at runtime; the real screens replace the placeholder below.
 */
public class GuessMarketApp extends Application {

    private static final int INITIAL_WIDTH = 1280;
    private static final int INITIAL_HEIGHT = 720;

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane(new Label("Guess Market"));
        stage.setTitle("Guess Market");
        stage.setScene(new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
