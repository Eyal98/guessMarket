package gm.engine.impl;

import gm.engine.TestFiles;
import gm.engine.api.GuessMarketEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Many people at once, which is the ordinary state of a server: Tomcat answers every request on a
 * thread of its own, so while one person buys, another is loading funds and a dozen clients are
 * pulling the lists every half second.
 * <p>
 * Nothing here can prove the absence of a race, but a market that is not guarded would lose money in
 * these tests far more often than not, and would sooner or later throw from a list being changed
 * while it is read.
 */
class ConcurrentEngineTest {

    private static final double TOLERANCE = 0.0001;
    private static final int PEOPLE = 8;
    private static final int ROUNDS = 200;

    private final GuessMarketEngine engine = new GuessMarketEngineImpl();

    @Test
    @DisplayName("Deposits made at the same time are all counted, and readers never trip over writers")
    void simultaneousDepositsAreAllCounted() throws Exception {
        for (int person = 0; person < PEOPLE; person++) {
            engine.enterMarket("Person " + person);
        }

        List<Callable<Void>> work = new ArrayList<>();
        for (int person = 0; person < PEOPLE; person++) {
            String name = "Person " + person;
            work.add(() -> {
                for (int round = 0; round < ROUNDS; round++) {
                    engine.deposit(name, 1);
                }
                return null;
            });
            work.add(() -> {
                for (int round = 0; round < ROUNDS; round++) {
                    engine.listUsers();
                    engine.ledger(name, 0);
                }
                return null;
            });
        }
        runTogether(work);

        for (int person = 0; person < PEOPLE; person++) {
            assertEquals(ROUNDS, engine.userDetail("Person " + person).balance(), TOLERANCE);
            assertEquals(ROUNDS, engine.ledger("Person " + person, 0).lines().size());
        }
    }

    @Test
    @DisplayName("Purchases made at the same time leave the event account exactly what the buyers paid in")
    void simultaneousPurchasesKeepTheBooksStraight() throws Exception {
        engine.enterMarket("Tikva");
        engine.deposit("Tikva", 1000);
        engine.uploadEvents("Tikva", "small.xml", TestFiles.open("ex3/small.xml"));
        engine.openEvent(1, "Tikva");
        for (int person = 0; person < PEOPLE; person++) {
            engine.enterMarket("Buyer " + person);
            engine.deposit("Buyer " + person, 1_000_000);
        }

        List<Callable<Void>> work = new ArrayList<>();
        for (int person = 0; person < PEOPLE; person++) {
            String name = "Buyer " + person;
            int option = 1 + person % 2;
            work.add(() -> {
                for (int round = 0; round < ROUNDS / 4; round++) {
                    engine.buyShares(1, name, option, 1);
                    engine.marketState(1);
                    engine.listEvents();
                }
                return null;
            });
        }
        runTogether(work);

        double paidIn = 0;
        long sharesBought = 0;
        for (int person = 0; person < PEOPLE; person++) {
            paidIn += engine.userDetail("Buyer " + person).participations().get(0).options().stream()
                    .mapToDouble(option -> option.paidFor()).sum();
            sharesBought += engine.userDetail("Buyer " + person).participations().get(0).options().stream()
                    .mapToLong(option -> option.shares()).sum();
        }
        assertEquals((long) PEOPLE * (ROUNDS / 4), sharesBought, "no purchase was lost");
        assertEquals(69.3147 + paidIn, engine.marketState(1).eventAccountBalance(), 0.01,
                "the account holds the subsidy and every price paid, nothing more and nothing less");
    }

    private static void runTogether(List<Callable<Void>> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(work.size());
        try {
            List<Future<Void>> results = pool.invokeAll(work);
            for (Future<Void> result : results) {
                result.get();
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
    }
}
