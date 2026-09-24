package gm.client.main;

import gm.client.http.MarketServer;
import gm.dto.ChatLineDto;
import gm.dto.EventInfoDto;
import gm.dto.LedgerLineDto;
import gm.dto.UserDetailDto;
import gm.dto.UserDto;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * What the client knows about the market, kept up to date by pulling it from the server twice a
 * second, which is the pace the exercise suggests.
 * <p>
 * Two techniques are used, each where it fits, as the course's chat example does. Lists that change in
 * place — the events, the users, the user's own standing — are fetched whole every time: they are
 * short, and a complete answer can never drift out of step with the server. The account's ledger and
 * the chat only ever grow at the end, so for those the client says how many lines it already has and
 * receives only the rest.
 * <p>
 * Pulling runs on a JavaFX timeline, so every tick starts on the screen thread and may look at what the
 * screen is showing; the requests themselves are asynchronous and never make the screen wait. A value
 * is only published when it differs from the one already held — the data objects compare by value —
 * so a table is not redrawn, and does not lose its selection, just because half a second passed.
 */
public final class MarketFeed {

    /** How often the server is asked what has changed. */
    public static final Duration PULL_PERIOD = Duration.millis(500);

    /** One thing to fetch, which reports back when its answer has been dealt with. */
    @FunctionalInterface
    public interface Pull {
        void fetch(Runnable finished);
    }

    private final MarketServer server;
    private final Consumer<MarketFeed> sessionLost;
    private final List<GuardedPull> pulls = new ArrayList<>();
    private final Timeline timeline;
    private boolean running;

    private final ObjectProperty<List<EventInfoDto>> events = new SimpleObjectProperty<>(List.of());
    private final ObjectProperty<List<UserDto>> users = new SimpleObjectProperty<>(List.of());
    private final ObjectProperty<UserDetailDto> me = new SimpleObjectProperty<>();
    private final ObservableList<LedgerLineDto> ledger = FXCollections.observableArrayList();
    private final ObservableList<ChatLineDto> chat = FXCollections.observableArrayList();
    private final ObservableList<LedgerLineDto> ledgerSeen = FXCollections.unmodifiableObservableList(ledger);
    private final ObservableList<ChatLineDto> chatSeen = FXCollections.unmodifiableObservableList(chat);

    /**
     * @param sessionLost what to do when the server no longer knows this client, which is the one
     *                    failure a pull cannot simply try again later. It is told which feed lost its
     *                    session, so an answer arriving late for a session already left behind can be
     *                    told apart from one about the session in use.
     */
    public MarketFeed(MarketServer server, Consumer<MarketFeed> sessionLost) {
        this.server = server;
        this.sessionLost = sessionLost;
        this.timeline = new Timeline(new KeyFrame(PULL_PERIOD, ignored -> pullEverything()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        every(finished -> server.events(fresh -> publish(events, fresh, finished), failed(finished)));
        every(finished -> server.users(fresh -> publish(users, fresh, finished), failed(finished)));
        every(finished -> server.account(fresh -> publish(me, fresh, finished), failed(finished)));
        every(this::pullLedger);
        every(this::pullChat);
    }

    /** Starts pulling, beginning at once rather than half a second from now. */
    public void start() {
        running = true;
        pullEverything();
        timeline.play();
    }

    public void stop() {
        running = false;
        timeline.stop();
    }

    /**
     * Pulls everything now instead of at the next tick. Called after the user has done something, so
     * its effect shows at once.
     */
    public void pullEverything() {
        pulls.forEach(GuardedPull::run);
    }

    /** Adds something else to fetch on every tick, such as the event a view is showing. */
    public void every(Pull pull) {
        pulls.add(new GuardedPull(pull));
    }

    public ReadOnlyObjectProperty<List<EventInfoDto>> events() {
        return events;
    }

    public ReadOnlyObjectProperty<List<UserDto>> users() {
        return users;
    }

    /** The logged-in user's own standing, or null until it has first arrived. */
    public ReadOnlyObjectProperty<UserDetailDto> me() {
        return me;
    }

    /** Every line of the logged-in user's account, oldest first. Only ever added to. */
    public ObservableList<LedgerLineDto> ledger() {
        return ledgerSeen;
    }

    /** Every chat message since this client logged in, oldest first. Only ever added to. */
    public ObservableList<ChatLineDto> chat() {
        return chatSeen;
    }

    /**
     * A failure of a pull. Only a lost session is acted on, and only once: several pulls are usually
     * out at the same moment, and all of them are refused together. Anything else — a server that did
     * not answer this once — is simply asked again at the next tick.
     */
    public Consumer<MarketServer.Failure> failed(Runnable finished) {
        return failure -> {
            finished.run();
            if (failure.sessionLost() && running) {
                stop();
                sessionLost.accept(this);
            }
        };
    }

    /** Publishes a value only if it differs from the one held, so an unchanged answer changes nothing. */
    public static <T> void publish(ObjectProperty<T> property, T fresh, Runnable finished) {
        if (!Objects.equals(property.get(), fresh)) {
            property.set(fresh);
        }
        finished.run();
    }

    private void pullLedger(Runnable finished) {
        int held = ledger.size();
        server.ledger(held, fresh -> {
            // A stale answer to an earlier question would repeat lines already shown.
            if (fresh.after() == ledger.size()) {
                ledger.addAll(fresh.lines());
            }
            finished.run();
        }, failed(finished));
    }

    private void pullChat(Runnable finished) {
        int held = chat.size();
        server.chat(held, fresh -> {
            if (fresh.after() == chat.size()) {
                chat.addAll(fresh.lines());
            }
            finished.run();
        }, failed(finished));
    }

    /**
     * A pull that never runs twice at once. If a tick comes round while the previous answer is still
     * on its way, the pull is not sent again but remembered, and sent once more as soon as the answer
     * arrives — so a slow server gets at most one question of each kind at a time, and nothing asked
     * for is ever lost.
     */
    private static final class GuardedPull {

        private final Pull pull;
        private boolean underway;
        private boolean askedAgain;

        GuardedPull(Pull pull) {
            this.pull = pull;
        }

        void run() {
            if (underway) {
                askedAgain = true;
                return;
            }
            underway = true;
            pull.fetch(this::finished);
        }

        private void finished() {
            underway = false;
            if (askedAgain) {
                askedAgain = false;
                run();
            }
        }
    }
}
