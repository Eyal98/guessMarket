package gm.client;

import gm.client.account.AccountController;
import gm.client.chat.ChatController;
import gm.client.events.EventsController;
import gm.client.http.MarketServer;
import gm.client.http.ServerAddress;
import gm.client.login.LoginController;
import gm.client.main.MainController;
import gm.client.main.MarketFeed;
import gm.dto.UserDetailDto;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * The client application: the login screen first, and the market once the server has accepted a name.
 * <p>
 * This is the one place that knows where the server is and how the screens fit together. Everything
 * else is handed the connection to the server and the feed that keeps the market up to date, and
 * could not tell whether the market behind them was in this process or on another computer.
 */
public class GuessMarketClientApp extends Application {

    private static final String TITLE = "Guess Market";
    private static final int INITIAL_WIDTH = 1280;
    private static final int INITIAL_HEIGHT = 720;
    /** Small enough to prove the layout survives being squeezed, which the marking will try. */
    private static final int SMALLEST_WIDTH = 760;
    private static final int SMALLEST_HEIGHT = 500;

    private MarketServer server;
    private Stage stage;
    private Scene scene;
    private MarketFeed feed;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        server = new MarketServer(ServerAddress.from(getParameters().getRaw().toArray(String[]::new)),
                Platform::runLater);
        scene = new Scene(new Pane(), INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("guess-market.css").toExternalForm());
        showLogin(null);
        stage.setScene(scene);
        stage.setMinWidth(SMALLEST_WIDTH);
        stage.setMinHeight(SMALLEST_HEIGHT);
        stage.show();
    }

    /** Logs out as the window closes, so the name is free at once, and lets the network threads go. */
    @Override
    public void stop() {
        if (feed != null) {
            feed.stop();
        }
        server.shutDown();
    }

    private void showLogin(String message) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("login.fxml"));
        scene.setRoot(load(loader));
        LoginController login = loader.getController();
        login.start(server, this::showMarket, message);
        stage.setTitle(TITLE);
    }

    private void showMarket(UserDetailDto me) {
        MarketFeed started = new MarketFeed(server, this::sessionEnded);
        FXMLLoader loader = new FXMLLoader(getClass().getResource("main.fxml"));
        Parent shell = load(loader);
        MainController main = loader.getController();
        EventsController events = new EventsController(server, started, main);
        AccountController account = new AccountController(server, started, main);
        ChatController chat = new ChatController(server, started, main);
        main.start(me, events.view(), account.view(), chat.view(), () -> logOut(started),
                () -> sessionEnded(started));

        feed = started;
        scene.setRoot(shell);
        stage.setTitle(TITLE + " — " + me.name());
        started.start();
    }

    private void logOut(MarketFeed leaving) {
        leaving.stop();
        feed = null;
        server.logOut(() -> showLogin("You have logged out."));
    }

    /**
     * The server no longer knows this client — it was restarted, or the session lapsed. Only the
     * session in use is acted on: an answer arriving late for one already left behind changes nothing.
     */
    private void sessionEnded(MarketFeed lost) {
        if (lost != feed) {
            return;
        }
        lost.stop();
        feed = null;
        showLogin("The server no longer knows this session; it may have been restarted. Please log in"
                + " again.");
    }

    private static Parent load(FXMLLoader loader) {
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("A screen of the program could not be built.", e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
