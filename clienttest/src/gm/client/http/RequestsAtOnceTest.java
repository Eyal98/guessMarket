package gm.client.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client keeps several pulls going at once - the events, the users, the account, its ledger, the
 * chat, and the event on screen - all to the same server. OkHttp allows only five requests to one
 * server at a time unless told otherwise, so a sixth pull, and any click the user made, would queue
 * behind the rest. This holds the client to letting every pull and a user's action through together.
 * <p>
 * A small server of the JDK's own stands in for Tomcat and keeps every request waiting, so the test
 * can count how many arrive at the same moment.
 */
class RequestsAtOnceTest {

    /** Six pulls, the click a user makes while they are out, and one to spare. */
    private static final int AT_ONCE = 8;

    @Test
    @DisplayName("Every pull and a user's own action reach the server together, none queued behind the rest")
    void nothingWaitsInTheClientsQueue() throws IOException, InterruptedException {
        CountDownLatch arrived = new CountDownLatch(AT_ONCE);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService threads = Executors.newCachedThreadPool();
        HttpServer standIn = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        standIn.setExecutor(threads);
        standIn.createContext("/gm", exchange -> {
            arrived.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] empty = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, empty.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(empty);
            }
        });
        standIn.start();
        try {
            MarketServer server = new MarketServer("http://localhost:" + standIn.getAddress().getPort() + "/gm",
                    Runnable::run);
            for (int request = 0; request < AT_ONCE; request++) {
                server.events(events -> { }, failure -> { });
            }

            assertTrue(arrived.await(5, TimeUnit.SECONDS),
                    "only " + (AT_ONCE - arrived.getCount()) + " of " + AT_ONCE + " requests reached the server;"
                            + " the rest were held back in the client");
        } finally {
            release.countDown();
            standIn.stop(0);
            threads.shutdownNow();
        }
    }
}
