package gm.engine.api;

import java.util.List;

/**
 * An events file could not be loaded.
 * <p>
 * Where possible the loader gathers every problem it can find before giving up, so that one attempt
 * tells the user everything that is wrong with the file instead of revealing the faults one at a
 * time. The message is a finished report; {@link #problems()} exposes the same list for callers that
 * would rather lay it out themselves.
 */
public class FileLoadException extends GuessMarketException {

    private static final long serialVersionUID = 1L;

    /** Always an immutable list, which is serializable; the declared type simply cannot say so. */
    @SuppressWarnings("serial")
    private final List<String> problems;

    public FileLoadException(String problem) {
        this(List.of(problem));
    }

    public FileLoadException(List<String> problems) {
        super(report(problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }

    private static String report(List<String> problems) {
        if (problems.isEmpty()) {
            throw new IllegalArgumentException("A file load failure must state at least one problem.");
        }
        if (problems.size() == 1) {
            return "The file was not loaded: " + problems.get(0);
        }
        StringBuilder report = new StringBuilder("The file was not loaded. ")
                .append(problems.size())
                .append(" problems were found:");
        for (int i = 0; i < problems.size(); i++) {
            report.append(System.lineSeparator())
                    .append("  ")
                    .append(i + 1)
                    .append(". ")
                    .append(problems.get(i));
        }
        return report.toString();
    }
}
