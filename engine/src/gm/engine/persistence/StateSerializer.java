package gm.engine.persistence;

import gm.engine.api.PersistenceException;
import gm.engine.model.SystemState;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.util.Locale;

/**
 * Writes the whole system to a file and reads it back, using Java's own object serialization.
 * <p>
 * The caller names the file without an extension and this class adds {@value #EXTENSION}, so a saved
 * system is always recognisable and a save and its matching load can never disagree about the name.
 * A path that already carries the extension is accepted as it is rather than gaining a second one.
 */
public final class StateSerializer {

    public static final String EXTENSION = ".gm";

    /**
     * @return the full path of the file that was written
     * @throws PersistenceException if the state could not be written
     */
    public String save(SystemState state, String pathWithoutExtension) {
        File file = fileFor(pathWithoutExtension);
        File folder = file.getAbsoluteFile().getParentFile();
        if (folder != null && !folder.isDirectory()) {
            throw new PersistenceException("There is no folder \"" + folder.getPath() + "\" to save into.");
        }
        try (FileOutputStream fileOut = new FileOutputStream(file);
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(state);
            return file.getPath();
        } catch (IOException e) {
            throw new PersistenceException("The system could not be saved to \"" + file.getPath() + "\": "
                    + e.getMessage(), e);
        }
    }

    /**
     * @return the system exactly as it was when it was saved
     * @throws PersistenceException if there is no such file, or it does not hold a saved system
     */
    public SystemState load(String pathWithoutExtension) {
        File file = fileFor(pathWithoutExtension);
        if (!file.isFile()) {
            throw new PersistenceException("There is no saved system at \"" + file.getPath() + "\"."
                    + " Give the same path and file name you used when you saved, without the extension.");
        }
        // The two streams are opened separately on purpose: if the ObjectInputStream constructor
        // rejects the file, a single nested statement would leave the file stream open and the file
        // locked on Windows.
        try (FileInputStream fileIn = new FileInputStream(file);
             ObjectInputStream in = new ObjectInputStream(fileIn)) {
            Object restored = in.readObject();
            if (restored instanceof SystemState state) {
                return state;
            }
            throw new PersistenceException("The file \"" + file.getPath()
                    + "\" does not hold a saved Guess Market system.");
        } catch (StreamCorruptedException e) {
            throw new PersistenceException("The file \"" + file.getPath()
                    + "\" is not a Guess Market save file.", e);
        } catch (InvalidClassException | ClassNotFoundException e) {
            throw new PersistenceException("The file \"" + file.getPath() + "\" was written by a different"
                    + " version of Guess Market and can no longer be read.", e);
        } catch (IOException e) {
            throw new PersistenceException("The saved system at \"" + file.getPath() + "\" could not be read: "
                    + e.getMessage(), e);
        }
    }

    private File fileFor(String pathWithoutExtension) {
        if (pathWithoutExtension == null || pathWithoutExtension.isBlank()) {
            throw new PersistenceException("No file path was given.");
        }
        String trimmed = pathWithoutExtension.trim();
        boolean alreadyHasExtension = trimmed.toLowerCase(Locale.ROOT).endsWith(EXTENSION);
        return new File(alreadyHasExtension ? trimmed : trimmed + EXTENSION);
    }
}
