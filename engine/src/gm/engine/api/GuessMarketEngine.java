package gm.engine.api;

import gm.dto.EventInfoDto;
import gm.dto.LedgerDto;
import gm.dto.MarketStateDto;
import gm.dto.OrderBookStateDto;
import gm.dto.PurchaseResultDto;
import gm.dto.TradeDto;
import gm.dto.UploadResultDto;
import gm.dto.UserDetailDto;
import gm.dto.UserDto;
import gm.engine.model.orderbook.OrderSide;

import java.io.InputStream;
import java.util.List;

/**
 * Everything the market can do, and the only thing the server ever talks to.
 * <p>
 * The engine is passive: it answers questions and carries out commands, and it neither knows nor
 * cares that the questions arrive over HTTP. It never reads input and never prints anything. Failures
 * arrive as a {@link GuessMarketException} whose message is already fit to show to a person.
 * <p>
 * People are named by their user names, which is all a server session knows about whoever is asking.
 * Events are named by number, counted from 1 across every event on the server; events are only ever
 * added, so a number handed out once keeps meaning the same event. Options are counted from 1 within
 * their event.
 * <p>
 * Every implementation must be safe to call from many threads at once, because a servlet container
 * serves each request on a thread of its own.
 */
public interface GuessMarketEngine {

    /**
     * Brings a user into the market, with an empty account, the first time their name is seen. A name
     * seen before gives back the same person, with everything they had. Whether somebody else is
     * already using the name right now is a question about sessions, which is the server's to answer.
     *
     * @throws InvalidSelectionException if the name is empty
     */
    UserDetailDto enterMarket(String userName);

    /** Everybody who has ever logged in, in the order they first did. */
    List<UserDto> listUsers();

    /**
     * Everything worth showing a user about themselves: their money, the events they run, and what
     * they hold.
     *
     * @throws InvalidSelectionException if nobody by that name has ever logged in
     */
    UserDetailDto userDetail(String userName);

    /**
     * The lines of a user's account after the first {@code after} of them.
     *
     * @param after how many lines the caller already has; 0 for the whole account
     * @throws InvalidSelectionException if there is no such user or {@code after} is negative
     */
    LedgerDto ledger(String userName, int after);

    /**
     * Loads money into a user's account from outside the market.
     *
     * @throws InvalidSelectionException if there is no such user, the amount is not positive, or the
     *                                   user is blocked
     */
    UserDetailDto deposit(String userName, double amount);

    /**
     * Reads an uploaded events file and, if every event in it is sound and new, adds them all to the
     * market with the uploader as their market maker. A faulty file changes nothing.
     *
     * @param uploaderName who sent the file
     * @param fileName     the name it had on their computer, which must end in .xml
     * @param content      its bytes, read here and never written anywhere
     * @throws FileLoadException         if the file is not a sound set of new events
     * @throws InvalidSelectionException if there is no such user
     */
    UploadResultDto uploadEvents(String uploaderName, String fileName, InputStream content);

    /** Every event on the server, of every kind and in every state, in the order they arrived. */
    List<EventInfoDto> listEvents();

    /**
     * The full state of an LMSR event: option values, share counts, account, commission and history.
     *
     * @throws InvalidSelectionException if there is no such event, or it is an order book
     */
    MarketStateDto marketState(int eventNumber);

    /**
     * Every book of an order book event, what they say about the price of each option, and where every
     * participant stands.
     *
     * @throws InvalidSelectionException if there is no such event, or it is not an order book
     */
    OrderBookStateDto orderBookState(int eventNumber);

    /**
     * Starts an event trading, at its market maker's expense.
     *
     * @throws InvalidSelectionException if the event or user does not exist, the user does not run
     *                                   this event, it has already started, or they cannot afford it
     */
    EventInfoDto openEvent(int eventNumber, String userName);

    /**
     * Decides an event and pays the holders of the winning option.
     *
     * @throws InvalidSelectionException if anything named does not exist, the user does not run this
     *                                   event, or it is not currently trading
     */
    EventInfoDto closeEvent(int eventNumber, String userName, int winningOptionNumber);

    /**
     * Buys shares of one option of an LMSR event.
     *
     * @throws InvalidSelectionException if anything named does not exist, the quantity is not
     *                                   positive, the event is not trading, or the buyer is blocked
     */
    PurchaseResultDto buyShares(int eventNumber, String userName, int optionNumber, long quantity);

    /**
     * Sells shares of one option back to an LMSR event.
     *
     * @throws InvalidSelectionException if anything named does not exist, the seller does not hold
     *                                   that many shares, the event is not trading, or they are blocked
     */
    PurchaseResultDto sellShares(int eventNumber, String userName, int optionNumber, long quantity);

    /**
     * Places an order on one option of an order book event and settles whatever it can at once.
     *
     * @return the trades the order caused, which may be none if it simply rests
     * @throws InvalidSelectionException if anything named does not exist, the price is not allowed,
     *                                   the event is not trading, the trader is blocked, or they are
     *                                   offering shares they do not hold
     */
    List<TradeDto> submitOrder(int eventNumber, String userName, int optionNumber, OrderSide side,
                               long quantity, double price);
}
