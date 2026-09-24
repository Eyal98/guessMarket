package gm.client.http;

import gm.dto.ChatDto;
import gm.dto.ChatLineDto;
import gm.dto.EventInfoDto;
import gm.dto.LedgerDto;
import gm.dto.PurchaseResultDto;
import gm.dto.UploadResultDto;
import gm.dto.UserDetailDto;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The client's whole conversation with a real server, over real HTTP.
 * <p>
 * These run only when a server is named with {@code -Dgm.server=http://localhost:8081/guess-market},
 * because they need Tomcat running with the WAR deployed; without it they are skipped rather than
 * failed. A running server keeps everything it has ever been sent, so every test invents names of its
 * own and never collides with a previous run.
 */
@DisplayName("Talking to a running server")
class MarketServerTest {

    private static final double TOLERANCE = 0.0001;
    private static String address;

    @BeforeAll
    static void aServerMustBeNamed() {
        address = System.getProperty("gm.server");
        assumeTrue(address != null, "no server named with -Dgm.server, so there is nothing to talk to");
    }

    private static MarketServer client() {
        return new MarketServer(address, Runnable::run);
    }

    private static String fresh(String what) {
        return what + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** One call to the server, waiting to be given somewhere to send its answer. */
    @FunctionalInterface
    private interface Call<T> {
        void send(Consumer<T> done, Consumer<MarketServer.Failure> failed);
    }

    /** Waits for a call that is expected to succeed, and fails the test with the server's reason if not. */
    private static <T> T answer(Class<T> expected, Call<T> call) {
        CompletableFuture<T> result = new CompletableFuture<>();
        call.send(result::complete, failure -> result.completeExceptionally(
                new AssertionError("the server refused: " + failure.message())));
        return expected.cast(await(result));
    }

    /** Waits for a call that is expected to be refused, and hands back the refusal. */
    private static <T> MarketServer.Failure refusal(Class<T> wouldHaveBeen, Call<T> call) {
        CompletableFuture<MarketServer.Failure> result = new CompletableFuture<>();
        call.send(answer -> result.completeExceptionally(new AssertionError("it was accepted as a "
                + wouldHaveBeen.getSimpleName() + ": " + answer)), result::complete);
        return await(result);
    }

    private static <T> T await(CompletableFuture<T> result) {
        try {
            return result.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw (AssertionError) e.getCause();
        } catch (InterruptedException | TimeoutException e) {
            throw new AssertionError("no answer came", e);
        }
    }

    private static File eventsFile(Path folder, String eventName) throws IOException {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Guess-Market><GM-events><GM-event name="%s">
                  <description>Made up for one test.</description>
                  <commission type="on-purchase">10</commission>
                  <GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>
                  <GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method>
                </GM-event></GM-events></Guess-Market>
                """.formatted(eventName);
        Path file = folder.resolve("events.xml");
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return file.toFile();
    }

    @Test
    @DisplayName("Logging in names the user, and a second client cannot take the same name")
    void aNameCanBeHeldOnce() {
        String name = fresh("Avrum");
        UserDetailDto user = answer(UserDetailDto.class,
                (done, failed) -> client().logIn(name, done, failed));

        MarketServer.Failure taken = refusal(UserDetailDto.class,
                (done, failed) -> client().logIn(name.toUpperCase(), done, failed));

        assertEquals(name, user.name());
        assertEquals(0.0, user.balance(), TOLERANCE);
        assertEquals(409, taken.status());
        assertTrue(taken.message().contains("already logged in"), taken.message());
    }

    @Test
    @DisplayName("Asking anything before logging in is refused as a lost session")
    void strangersAreSentToLogIn() {
        MarketServer.Failure refused = refusal(UserDetailDto.class,
                (done, failed) -> client().account(done, failed));

        assertTrue(refused.sessionLost());
    }

    @Test
    @DisplayName("A server that is not there is reported as unreachable, with its address")
    void anAbsentServer() {
        MarketServer nowhere = new MarketServer("http://localhost:1/guess-market", Runnable::run);

        MarketServer.Failure refused = refusal(UserDetailDto.class,
                (done, failed) -> nowhere.logIn("Anybody", done, failed));

        assertEquals(MarketServer.Failure.UNREACHABLE, refused.status());
        assertTrue(refused.message().contains("localhost:1"), refused.message());
    }

    @Test
    @DisplayName("Upload, load funds, open, buy: the whole path, with the ledger fetched by delta")
    void theWholePath(@TempDir Path folder) throws IOException {
        MarketServer tikva = client();
        String tikvaName = fresh("Tikva");
        String event = fresh("Rain");
        answer(UserDetailDto.class, (done, failed) -> tikva.logIn(tikvaName, done, failed));

        File file = eventsFile(folder, event);
        UploadResultDto uploaded = answer(UploadResultDto.class,
                (done, failed) -> tikva.upload(file, done, failed));
        answer(UserDetailDto.class, (done, failed) -> tikva.deposit(500, done, failed));
        int number = numberOf(tikva, event);

        assertEquals(List.of(event), uploaded.eventNames());
        EventInfoDto opened = answer(EventInfoDto.class, (done, failed) -> tikva.open(number, done, failed));
        assertEquals("Active", opened.status());

        MarketServer menash = client();
        answer(UserDetailDto.class, (done, failed) -> menash.logIn(fresh("Menash"), done, failed));
        answer(UserDetailDto.class, (done, failed) -> menash.deposit(100, done, failed));
        answer(PurchaseResultDto.class, (done, failed) -> menash.buy(number, 1, 100, done, failed));

        LedgerDto rest = answer(LedgerDto.class, (done, failed) -> menash.ledger(1, done, failed));
        assertEquals(2, rest.lines().size(), "the purchase and its commission, and not the deposit before");
        assertTrue(rest.lines().get(1).description().startsWith("Commission to " + tikvaName));
        UserDetailDto me = answer(UserDetailDto.class, (done, failed) -> menash.account(done, failed));
        assertEquals(event, me.participations().get(0).event().name());
    }

    @Test
    @DisplayName("A faulty file comes back with each of its problems")
    void aFaultyUpload(@TempDir Path folder) throws IOException {
        MarketServer avrum = client();
        answer(UserDetailDto.class, (done, failed) -> avrum.logIn(fresh("Avrum"), done, failed));
        Path broken = folder.resolve("broken.xml");
        Files.writeString(broken, "<Guess-Market><GM-events><GM-event/></GM-events></Guess-Market>");

        MarketServer.Failure refused = refusal(UploadResultDto.class,
                (done, failed) -> avrum.upload(broken.toFile(), done, failed));

        assertEquals(400, refused.status());
        assertTrue(refused.problems().size() > 1, refused.message());
    }

    @Test
    @DisplayName("A chat message reaches everybody, signed by whoever sent it")
    void chatting() {
        MarketServer avrum = client();
        String name = fresh("Avrum");
        answer(UserDetailDto.class, (done, failed) -> avrum.logIn(name, done, failed));
        String text = fresh("hello, café");

        ChatLineDto sent = answer(ChatLineDto.class, (done, failed) -> avrum.say(text, done, failed));

        MarketServer tikva = client();
        answer(UserDetailDto.class, (done, failed) -> tikva.logIn(fresh("Tikva"), done, failed));
        List<ChatLineDto> seen = answer(ChatDto.class,
                (done, failed) -> tikva.chat(sent.number() - 1, done, failed)).lines();
        assertEquals(text, seen.get(0).text());
        assertEquals(name, seen.get(0).userName());
    }

    @Test
    @DisplayName("Logging out frees the name, and logging back in finds the account as it was")
    void comingBack() {
        String name = fresh("Menash");
        MarketServer first = client();
        answer(UserDetailDto.class, (done, failed) -> first.logIn(name, done, failed));
        answer(UserDetailDto.class, (done, failed) -> first.deposit(42, done, failed));
        CompletableFuture<Void> loggedOut = new CompletableFuture<>();
        first.logOut(() -> loggedOut.complete(null));
        await(loggedOut);

        UserDetailDto back = answer(UserDetailDto.class,
                (done, failed) -> client().logIn(name, done, failed));

        assertEquals(42.0, back.balance(), TOLERANCE);
        assertFalse(back.blocked());
    }

    /** The number the server gave the event of that name. */
    private static int numberOf(MarketServer server, String eventName) {
        CompletableFuture<List<EventInfoDto>> events = new CompletableFuture<>();
        server.events(events::complete, failure -> events.completeExceptionally(
                new AssertionError("the server refused: " + failure.message())));
        return await(events).stream().filter(event -> event.name().equals(eventName)).findFirst()
                .orElseThrow(() -> new AssertionError(eventName + " is not on the server"))
                .number();
    }
}
