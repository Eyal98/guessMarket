package gm.engine.api;

import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.LoadResultDto;
import gm.engine.api.dto.MarketStateDto;

import java.util.List;

/**
 * Everything the system can do, and the only thing a user interface ever talks to.
 * <p>
 * The engine is passive: it answers questions and carries out commands, and it neither knows nor
 * cares who is asking. It never reads input and never prints anything. Failures arrive as a
 * {@link GuessMarketException} whose message is already fit to show to a person.
 * <p>
 * Every number crossing this interface is counted from 1, so a caller can pass on exactly what a
 * user chose from a numbered list. Event numbers refer to the position of an event among <em>all</em>
 * loaded events, which is what {@link EventInfoDto#number()} carries, so a number taken from a
 * filtered list such as {@link #listOpenEvents()} means the same thing as one taken from
 * {@link #listEvents()}.
 */
public interface GuessMarketEngine {

    /**
     * Reads an events file and, if it is sound, makes its events the state of the system, replacing
     * whatever was loaded before. A file that turns out to be faulty changes nothing.
     *
     * @param path the full path of the XML file to read
     * @return what was loaded
     * @throws FileLoadException if the file is missing, unreadable, or not a valid set of events
     */
    LoadResultDto loadEventsFile(String path);

    /** Whether an events file has been loaded successfully. */
    boolean isLoaded();

    /**
     * Every loaded event.
     *
     * @throws NoFileLoadedException if no file has been loaded
     */
    List<EventInfoDto> listEvents();

    /**
     * The loaded events that are still trading.
     *
     * @throws NoFileLoadedException if no file has been loaded
     */
    List<EventInfoDto> listOpenEvents();

    /**
     * The full state of one event: option values, share counts, account, commission and history.
     *
     * @param eventNumber the event's number, counted from 1
     * @throws NoFileLoadedException     if no file has been loaded
     * @throws InvalidSelectionException if there is no such event
     */
    MarketStateDto marketState(int eventNumber);

    // Trading and closing leave this interface for the moment. In this version both need to know
    // which user is acting, and the engine cannot say until the loader reads the users out of the
    // file. They return, user aware, in the next step.


    /**
     * Writes the whole state of the system, including all trading history, to a file.
     *
     * @param pathWithoutExtension the full path and file name, without an extension; the engine adds
     *                             its own
     * @return the full path of the file that was written, extension included
     * @throws NoFileLoadedException if there is nothing to save
     * @throws PersistenceException  if the state could not be written
     */
    String saveState(String pathWithoutExtension);

    /**
     * Restores a state written earlier by {@link #saveState(String)}, replacing whatever is loaded.
     * A failed restore changes nothing.
     *
     * @param pathWithoutExtension the full path and file name, without an extension
     * @throws PersistenceException if the state could not be read
     */
    void loadState(String pathWithoutExtension);
}
