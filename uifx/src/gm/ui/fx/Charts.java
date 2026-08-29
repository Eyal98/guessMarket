package gm.ui.fx;

import gm.engine.api.dto.BalanceHistoryDto;
import gm.engine.api.dto.BalancePointDto;
import gm.engine.api.dto.PriceHistoryDto;
import gm.engine.api.dto.PricePointDto;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;

import java.util.List;

/**
 * The two charts: where an event's prices have been, and where a person's money has been.
 * <p>
 * Both are drawn against the number of things that have happened rather than the clock. Everything
 * in this system happens within a few seconds of everything else, so a chart against wall time would
 * be a vertical smudge; against "how many trades ago" it reads as the story it is.
 */
final class Charts {

    private static final int CHART_HEIGHT = 260;
    /** Beyond about this many labels the horizontal axis starts writing on top of itself. */
    private static final int MOST_LABELS_ACROSS = 10;

    private Charts() {
    }

    /**
     * Whether this event's past contains a single price worth plotting.
     * <p>
     * An order book nobody has traded on has none, and an empty chart with a plausible looking axis
     * is worse than no chart at all: it invites the reader to believe the market has been sitting at
     * nought. Same reasoning as the dash shown in place of a nought everywhere else.
     */
    static boolean hasAnythingToDraw(PriceHistoryDto history) {
        return history.points().stream()
                .flatMap(point -> point.pricePerOption().stream())
                .anyMatch(java.util.Objects::nonNull);
    }

    /** One line per option, showing what each has been worth since the event opened. */
    static Node priceChart(PriceHistoryDto history) {
        if (history.points().isEmpty()) {
            return new Label("This event has not opened yet, so there is nothing to draw.");
        }
        if (!hasAnythingToDraw(history)) {
            return new Label("Nothing has changed hands here yet, so there is no price to draw.");
        }
        LineChart<Number, Number> chart = emptyChart("Trades so far", "Price of one share",
                history.points().get(history.points().size() - 1).step());

        for (int optionIndex = 0; optionIndex < history.optionNames().size(); optionIndex++) {
            XYChart.Series<Number, Number> line = new XYChart.Series<>();
            line.setName(history.optionNames().get(optionIndex));
            for (PricePointDto point : history.points()) {
                Double price = point.pricePerOption().get(optionIndex);
                if (price != null) {
                    line.getData().add(new XYChart.Data<>(point.step(), price));
                }
            }
            chart.getData().add(line);
        }
        return chart;
    }

    /** A single line showing what somebody has been worth after each movement of their money. */
    static Node balanceChart(BalanceHistoryDto history) {
        if (history.points().size() < 2) {
            return new Label(history.userName() + " has not spent or received anything yet.");
        }
        LineChart<Number, Number> chart = emptyChart("Movements of money", "Balance",
                history.points().get(history.points().size() - 1).step());

        XYChart.Series<Number, Number> line = new XYChart.Series<>();
        line.setName(history.userName());
        for (BalancePointDto point : history.points()) {
            line.getData().add(new XYChart.Data<>(point.step(), point.balance()));
        }
        chart.getData().add(line);
        return chart;
    }

    private static LineChart<Number, Number> emptyChart(String across, String up, int lastStep) {
        // The horizontal axis counts things that happened, and there is no such thing as the third
        // tenth of a trade. Left to range itself the axis offers 0.1, 0.2 and so on, so it is given
        // whole numbers and a step wide enough to keep the labels from crowding each other.
        NumberAxis horizontal = new NumberAxis(0, Math.max(lastStep, 1), wholeStepsFor(lastStep));
        horizontal.setLabel(across);
        horizontal.setMinorTickVisible(false);
        NumberAxis vertical = new NumberAxis();
        vertical.setLabel(up);
        vertical.setForceZeroInRange(false);

        LineChart<Number, Number> chart = new LineChart<>(horizontal, vertical);
        chart.setPrefHeight(CHART_HEIGHT);
        chart.setMinHeight(CHART_HEIGHT);
        // Charts animate themselves by default. The movement bonus has to arrive switched off, and a
        // chart that redraws itself with a flourish every time a number changes would break that
        // promise from a direction nobody would think to look in.
        chart.setAnimated(false);
        return chart;
    }

    /** How many steps apart the labels on the horizontal axis should be, never less than one. */
    private static int wholeStepsFor(int lastStep) {
        return Math.max(1, (int) Math.ceil(lastStep / (double) MOST_LABELS_ACROSS));
    }

    /** Convenience for the callers, both of which want the same heading above their chart. */
    static List<Node> titled(String heading, Node chart) {
        return List.of(EventDetailView.section(heading), chart);
    }
}
