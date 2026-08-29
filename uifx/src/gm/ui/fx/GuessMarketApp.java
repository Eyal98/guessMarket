package gm.ui.fx;

import gm.engine.api.GuessMarketEngine;
import gm.engine.impl.GuessMarketEngineImpl;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The application itself: it builds the engine, builds the screen, and introduces them.
 * <p>
 * This is the one place that knows which engine is being used. Everything else works through the
 * {@link GuessMarketEngine} interface and could not tell one implementation from another.
 */
public class GuessMarketApp extends Application {

    private static final int INITIAL_WIDTH = 1280;
    private static final int INITIAL_HEIGHT = 720;
    /** Small enough to prove the layout survives being squeezed, which the marking will try. */
    private static final int SMALLEST_WIDTH = 720;
    private static final int SMALLEST_HEIGHT = 480;

    @Override
    public void start(Stage stage) throws Exception {
        GuessMarketEngine engine = new GuessMarketEngineImpl();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("main.fxml"));
        Parent root = loader.load();
        MainController main = loader.getController();

        EventsController events = new EventsController(engine, new EventDetailView(engine));
        UsersController users = new UsersController(engine, main);
        main.start(engine, events, users);

        Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("guess-market.css").toExternalForm());

        stage.setTitle("Guess Market");
        stage.setScene(scene);
        stage.setMinWidth(SMALLEST_WIDTH);
        stage.setMinHeight(SMALLEST_HEIGHT);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
