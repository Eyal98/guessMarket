package gm.client.chat;

import gm.client.Views;
import gm.client.http.MarketServer;
import gm.client.main.MarketFeed;
import gm.client.main.Messenger;
import gm.dto.ChatLineDto;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The chat room of the bonus: everybody logged in sees everything anybody writes.
 * <p>
 * It works exactly as the course's chat example does. Sending is one request; receiving is the feed
 * asking every half second for the messages after the ones already here, so a conversation of any
 * length costs only its new lines.
 */
public final class ChatController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final MarketServer server;
    private final Messenger messenger;
    private final ListView<ChatLineDto> lines;
    private final TextField message = new TextField();
    private final Button send = new Button("Send");
    private final VBox root;

    public ChatController(MarketServer server, MarketFeed feed, Messenger messenger) {
        this.server = server;
        this.messenger = messenger;
        this.lines = new ListView<>(feed.chat());
        lines.setId("chatLines");
        lines.setPlaceholder(new Label("Nobody has said anything yet. Say hello."));
        lines.setCellFactory(ignored -> new ChatCell());
        feed.chat().addListener((ListChangeListener<ChatLineDto>) change ->
                lines.scrollTo(feed.chat().size() - 1));
        VBox.setVgrow(lines, Priority.ALWAYS);

        message.setId("chatMessage");
        message.setPromptText("Write to everybody, then press Enter");
        message.setOnAction(ignored -> sendMessage());
        HBox.setHgrow(message, Priority.ALWAYS);
        send.getStyleClass().add("primary-button");
        send.setOnAction(ignored -> sendMessage());
        HBox writing = new HBox(8, message, send);
        writing.setAlignment(Pos.CENTER_LEFT);

        root = new VBox(8, Views.section("Chat with everybody in the market"), lines, writing);
        root.setPadding(new Insets(12));
    }

    public Node view() {
        return root;
    }

    private void sendMessage() {
        String text = message.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        send.setDisable(true);
        server.say(text, sent -> {
            send.setDisable(false);
            message.clear();
        }, failure -> {
            send.setDisable(false);
            messenger.refused(failure);
        });
    }

    /** One message: when, who, and what. */
    private static final class ChatCell extends ListCell<ChatLineDto> {

        ChatCell() {
            setWrapText(true);
            setPrefWidth(0);
        }

        @Override
        protected void updateItem(ChatLineDto line, boolean empty) {
            super.updateItem(line, empty);
            setText(empty || line == null ? null
                    : TIME.format(Instant.ofEpochMilli(line.sentAt())) + "  " + line.userName() + ":  "
                    + line.text());
        }
    }
}
