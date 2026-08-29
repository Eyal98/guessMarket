package gm.engine.api;

import gm.engine.api.dto.EventInfoDto;
import gm.engine.api.dto.LoadResultDto;
import gm.engine.api.dto.MarketStateDto;
import gm.engine.api.dto.OrderBookStateDto;
import gm.engine.api.dto.PurchaseResultDto;
import gm.engine.api.dto.TradeDto;
import gm.engine.api.dto.UserDetailDto;
import gm.engine.api.dto.UserDto;
import gm.engine.model.orderbook.OrderSide;

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

    /**
     * Every user in the market, in file order.
     *
     * @throws NoFileLoadedException if no file has been loaded
     */
    List<UserDto> listUsers();

    /**
     * Everything worth showing about one user: their money, the events they run, and what they hold.
     *
     * @param userNumber the user's number, counted from 1
     * @throws InvalidSelectionException if there is no such user
     */
    UserDetailDto userDetail(int userNumber);

    /**
     * Starts an event trading, at its market maker's expense.
     *
     * @param eventNumber the event's number, counted from 1
     * @param userNumber  the user asking, who must be the event's market maker
     * @throws InvalidSelectionException if the numbers name nothing, if the user does not run this
     *                                   event, if it has already started, or if they cannot afford it
     */
    EventInfoDto openEvent(int eventNumber, int userNumber);

    /**
     * Buys shares of one option for a user.
     *
     * @param eventNumber  the event's number, counted from 1
     * @param userNumber   the buyer's number, counted from 1
     * @param optionNumber the option's number within the event, counted from 1
     * @param quantity     how many shares to buy, at least 1
     * @throws InvalidSelectionException if anything named does not exist, the quantity is not
     *                                   positive, the event is not trading, or the buyer is blocked
     */
    PurchaseResultDto buyShares(int eventNumber, int userNumber, int optionNumber, long quantity);

    /**
     * Sells shares of one option back to the event.
     *
     * @throws InvalidSelectionException if anything named does not exist, the seller does not hold
     *                                   that many shares, the event is not trading, or they are blocked
     */
    PurchaseResultDto sellShares(int eventNumber, int userNumber, int optionNumber, long quantity);

    /**
     * Both books of an order book event, what they say about the price of each option, and where every
     * participant stands.
     *
     * @param eventNumber the event's number, counted from 1
     * @throws InvalidSelectionException if there is no such event, or it is not an order book event
     */
    OrderBookStateDto orderBookState(int eventNumber);

    /**
     * Places an order on one option of an order book event and settles whatever it can at once.
     * <p>
     * {@link OrderSide} crosses this boundary as itself rather than as text. It is a plain choice of
     * two with nothing behind it, so passing it whole costs the caller nothing in coupling and saves
     * them guessing at a spelling.
     *
     * @param eventNumber  the event's number, counted from 1
     * @param userNumber   the trader's number, counted from 1
     * @param optionNumber the option's number within the event, counted from 1
     * @param side         whether they are buying or selling
     * @param quantity     how many shares, at least 1
     * @param price        what they will pay, or accept, for each share
     * @return the trades the order caused, which may be none if it simply rests
     * @throws InvalidSelectionException if anything named does not exist, the price is not allowed,
     *                                   the event is not trading, the trader is blocked, or they are
     *                                   offering shares they do not hold
     */
    List<TradeDto> submitOrder(int eventNumber, int userNumber, int optionNumber, OrderSide side,
                               long quantity, double price);

    /**
     * Decides an event and pays the holders of the winning option.
     *
     * @param eventNumber         the event's number, counted from 1
     * @param userNumber          the user asking, who must be the event's market maker
     * @param winningOptionNumber the option the event ended on, counted from 1
     * @throws InvalidSelectionException if the numbers name nothing, the user does not run this event,
     *                                   or it is not currently trading
     */
    MarketStateDto closeEvent(int eventNumber, int userNumber, int winningOptionNumber);


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
