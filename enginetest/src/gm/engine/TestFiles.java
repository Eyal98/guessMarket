package gm.engine;

import java.nio.file.Path;

/**
 * Finds the sample event files the tests read.
 * <p>
 * The build script passes the folder in as a system property so the tests do not depend on where
 * they happen to be started from; the fallback keeps them working when they are run from the project
 * root, which is what an IDE does.
 */
public final class TestFiles {

    private static final Path FOLDER = Path.of(System.getProperty("gm.testfiles", "test-files"));

    private TestFiles() {
    }

    /** The full path of a sample file, whether or not it exists. */
    public static String path(String fileName) {
        return FOLDER.resolve(fileName).toAbsolutePath().toString();
    }

    /** The folder itself, for the tests that check what happens when a folder is given as a file. */
    public static String folder() {
        return FOLDER.toAbsolutePath().toString();
    }
}
