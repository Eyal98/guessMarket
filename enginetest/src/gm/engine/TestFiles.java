package gm.engine;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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
    public static Path path(String fileName) {
        return FOLDER.resolve(fileName).toAbsolutePath();
    }

    /**
     * The content of a sample file as a stream, which is how an uploaded file reaches the engine: the
     * server never has a path to give it, only the bytes that arrived.
     */
    public static InputStream open(String fileName) {
        try {
            return Files.newInputStream(path(fileName));
        } catch (IOException e) {
            throw new UncheckedIOException("The test file " + fileName + " could not be opened.", e);
        }
    }
}
