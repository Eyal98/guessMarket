package gm.server.utils;

import gm.dto.EventInfoDto;
import gm.dto.LedgerDto;
import gm.dto.MarketStateDto;
import gm.dto.OrderBookStateDto;
import gm.dto.PurchaseResultDto;
import gm.dto.TradeDto;
import gm.dto.UploadResultDto;
import gm.dto.UserDetailDto;
import gm.dto.UserDto;
import gm.engine.api.GuessMarketEngine;
import gm.engine.model.orderbook.OrderSide;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * The engine as the servlets see it: the same engine, made safe to call from many threads at once.
 * <p>
 * The engine is written for one caller at a time, as it was for the screens of exercises 1 and 2. It is
 * the server that brings the threads — Tomcat answers every request on a thread of its own — so it is
 * here, in the layer that introduces them, that they are kept from trampling each other, and the
 * engine stays free of a cost only one of its users has to pay.
 * <p>
 * One read-write lock guards the whole market. Most requests are the clients' pulls, which only read,
 * and any number of those run together; a change waits for them to finish and then has the market to
 * itself. Every answer is built completely while the lock is held, so what leaves is a consistent
 * picture of one moment, never half of one trade. A lock per event would let trades on different
 * events run side by side, but every change here is a few arithmetic steps, and one lock is far
 * harder to get wrong.
 */
public final class ThreadSafeEngine implements GuessMarketEngine {

    private final GuessMarketEngine engine;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public ThreadSafeEngine(GuessMarketEngine engine) {
        this.engine = engine;
    }

    @Override
    public UserDetailDto enterMarket(String userName) {
        return changing(() -> engine.enterMarket(userName));
    }

    @Override
    public List<UserDto> listUsers() {
        return reading(engine::listUsers);
    }

    @Override
    public UserDetailDto userDetail(String userName) {
        return reading(() -> engine.userDetail(userName));
    }

    @Override
    public LedgerDto ledger(String userName, int after) {
        return reading(() -> engine.ledger(userName, after));
    }

    @Override
    public UserDetailDto deposit(String userName, double amount) {
        return changing(() -> engine.deposit(userName, amount));
    }

    @Override
    public UploadResultDto uploadEvents(String uploaderName, String fileName, InputStream content) {
        return changing(() -> engine.uploadEvents(uploaderName, fileName, content));
    }

    @Override
    public List<EventInfoDto> listEvents() {
        return reading(engine::listEvents);
    }

    @Override
    public MarketStateDto marketState(int eventNumber) {
        return reading(() -> engine.marketState(eventNumber));
    }

    @Override
    public OrderBookStateDto orderBookState(int eventNumber) {
        return reading(() -> engine.orderBookState(eventNumber));
    }

    @Override
    public EventInfoDto openEvent(int eventNumber, String userName) {
        return changing(() -> engine.openEvent(eventNumber, userName));
    }

    @Override
    public EventInfoDto closeEvent(int eventNumber, String userName, int winningOptionNumber) {
        return changing(() -> engine.closeEvent(eventNumber, userName, winningOptionNumber));
    }

    @Override
    public PurchaseResultDto buyShares(int eventNumber, String userName, int optionNumber, long quantity) {
        return changing(() -> engine.buyShares(eventNumber, userName, optionNumber, quantity));
    }

    @Override
    public PurchaseResultDto sellShares(int eventNumber, String userName, int optionNumber, long quantity) {
        return changing(() -> engine.sellShares(eventNumber, userName, optionNumber, quantity));
    }

    @Override
    public List<TradeDto> submitOrder(int eventNumber, String userName, int optionNumber, OrderSide side,
                                      long quantity, double price) {
        return changing(() -> engine.submitOrder(eventNumber, userName, optionNumber, side, quantity, price));
    }

    private <T> T reading(Supplier<T> question) {
        return holding(lock.readLock(), question);
    }

    private <T> T changing(Supplier<T> command) {
        return holding(lock.writeLock(), command);
    }

    private static <T> T holding(Lock held, Supplier<T> work) {
        held.lock();
        try {
            return work.get();
        } finally {
            held.unlock();
        }
    }
}
