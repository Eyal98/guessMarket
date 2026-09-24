package gm.client;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;

import java.util.function.Function;

/**
 * The small pieces every screen is built from, made the same way everywhere so the screens look like
 * one program: section titles, captioned values that line up, text columns, and scrolling for anything
 * that can outgrow its space.
 */
public final class Views {

    private Views() {
    }

    public static Label section(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("section-title");
        return label;
    }

    /** Pairs of caption and value, laid out in two columns so they line up down the screen. */
    public static GridPane labelled(String... captionsAndValues) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);
        ColumnConstraints captions = new ColumnConstraints();
        captions.setMinWidth(150);
        grid.getColumnConstraints().add(captions);
        for (int i = 0; i + 1 < captionsAndValues.length; i += 2) {
            Label caption = new Label(captionsAndValues[i]);
            caption.getStyleClass().add("caption");
            grid.add(caption, 0, i / 2);
            grid.add(new Label(captionsAndValues[i + 1]), 1, i / 2);
        }
        return grid;
    }

    public static <T> TableColumn<T, String> textColumn(String title, Function<T, String> value,
                                                        double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(row -> new SimpleStringProperty(value.apply(row.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    /**
     * A text column whose long values wrap onto more lines instead of being cut short. A table row is
     * one line high unless something inside it asks for more, so the text is drawn as a graphic that
     * wraps at the column's own width and makes the row grow to fit.
     */
    public static <T> TableColumn<T, String> wrappingColumn(String title, Function<T, String> value,
                                                            double width) {
        TableColumn<T, String> column = textColumn(title, value, width);
        column.setCellFactory(ignored -> {
            TableCell<T, String> cell = new TableCell<>();
            Text text = new Text();
            text.getStyleClass().add("cell-text");
            text.wrappingWidthProperty().bind(column.widthProperty().subtract(10));
            text.textProperty().bind(cell.itemProperty());
            cell.setGraphic(text);
            cell.setPrefHeight(Control.USE_COMPUTED_SIZE);
            return cell;
        });
        return column;
    }

    /**
     * Makes a table's columns share out exactly the width it has, rather than leaving an empty column
     * on the right or pushing the last ones out of sight, so everything stays visible as the window
     * narrows.
     */
    public static <T> TableView<T> fitted(TableView<T> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        return table;
    }

    /** Wraps anything that can outgrow its space, so a small window stays usable. */
    public static ScrollPane scrolling(Node content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.setPannable(true);
        return pane;
    }

    /** A line of text that wraps rather than being cut off when its space narrows. */
    public static Label wrapping(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }
}
