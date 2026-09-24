package gm.client.http;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import gm.dto.ChatDto;
import gm.dto.ChatLineDto;
import gm.dto.ErrorDto;
import gm.dto.EventInfoDto;
import gm.dto.LedgerDto;
import gm.dto.MarketStateDto;
import gm.dto.OrderBookStateDto;
import gm.dto.PurchaseResultDto;
import gm.dto.TradeDto;
import gm.dto.UploadResultDto;
import gm.dto.UserDetailDto;
import gm.dto.UserDto;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The server, as the client sees it: every question and every command of the market, each one an
 * HTTP request.
 * <p>
 * This is where the exercise's "every call to the engine becomes an HTTP call" happens, and the only
 * place in the client that knows a URL, a status code or a line of JSON. Everything above it deals in
 * the same data objects the engine produces on the server, parsed back from the JSON they travelled as.
 * <p>
 * Every call is asynchronous, as the course's OkHttp examples are: the request goes out on OkHttp's own
 * threads, the answer is read and parsed there, and only the finished result is handed over through
 * {@code deliverOn}, which in the application is the screen thread. Nothing here ever makes the screen
 * wait for the network.
 */
public final class MarketServer {

    /**
     * Why a request did not succeed, in words fit to show.
     *
     * @param status   the HTTP status, or {@link #UNREACHABLE} if no answer came at all
     * @param message  the whole explanation
     * @param problems for a refused events file, each fault on its own; otherwise empty
     */
    public record Failure(int status, String message, List<String> problems) {

        /** The status given to a request that never reached the server. */
        public static final int UNREACHABLE = 0;

        /** Whether the server no longer knows this client, so it has to log in again. */
        public boolean sessionLost() {
            return status == 401;
        }
    }

    private static final Type EVENTS = new TypeToken<List<EventInfoDto>>() { }.getType();
    private static final Type USERS = new TypeToken<List<UserDto>>() { }.getType();
    private static final Type TRADES = new TypeToken<List<TradeDto>>() { }.getType();
    private static final MediaType XML = MediaType.parse("text/xml");
    private static final RequestBody NOTHING = RequestBody.create(new byte[0], null);

    private final String address;
    private final Executor deliverOn;
    private final SimpleCookieManager cookies = new SimpleCookieManager();
    private final OkHttpClient http;
    private final Gson gson = new Gson();

    /**
     * @param address   the server's address including the context path, without a trailing slash
     * @param deliverOn where answers are handed over; the screen thread, in the application
     */
    public MarketServer(String address, Executor deliverOn) {
        this.address = address;
        this.deliverOn = deliverOn;
        this.http = new OkHttpClient.Builder()
                .cookieJar(cookies)
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public String address() {
        return address;
    }

    public void logIn(String userName, Consumer<UserDetailDto> done, Consumer<Failure> failed) {
        send(post("login", "username", userName), UserDetailDto.class, done, failed);
    }

    /** Logs out and forgets the session, whether or not the server could be told. */
    public void logOut(Runnable done) {
        http.newCall(post("logout")).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException unreachable) {
                forgetSession(done);
            }

            @Override
            public void onResponse(Call call, Response response) {
                response.close();
                forgetSession(done);
            }
        });
    }

    public void events(Consumer<List<EventInfoDto>> done, Consumer<Failure> failed) {
        send(get("events"), EVENTS, done, failed);
    }

    public void users(Consumer<List<UserDto>> done, Consumer<Failure> failed) {
        send(get("users"), USERS, done, failed);
    }

    public void account(Consumer<UserDetailDto> done, Consumer<Failure> failed) {
        send(get("account"), UserDetailDto.class, done, failed);
    }

    public void ledger(int after, Consumer<LedgerDto> done, Consumer<Failure> failed) {
        send(get("account/ledger", "after", String.valueOf(after)), LedgerDto.class, done, failed);
    }

    public void deposit(double amount, Consumer<UserDetailDto> done, Consumer<Failure> failed) {
        send(post("account/deposit", "amount", String.valueOf(amount)), UserDetailDto.class, done, failed);
    }

    /** Sends an events file as a multipart upload, the way the course's example does. */
    public void upload(File file, Consumer<UploadResultDto> done, Consumer<Failure> failed) {
        RequestBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), RequestBody.create(file, XML))
                .build();
        send(new Request.Builder().url(url("events/upload")).post(body).build(), UploadResultDto.class,
                done, failed);
    }

    public void lmsrEvent(int event, Consumer<MarketStateDto> done, Consumer<Failure> failed) {
        send(get("events/lmsr", "event", String.valueOf(event)), MarketStateDto.class, done, failed);
    }

    public void orderBook(int event, Consumer<OrderBookStateDto> done, Consumer<Failure> failed) {
        send(get("events/book", "event", String.valueOf(event)), OrderBookStateDto.class, done, failed);
    }

    public void open(int event, Consumer<EventInfoDto> done, Consumer<Failure> failed) {
        send(post("events/open", "event", String.valueOf(event)), EventInfoDto.class, done, failed);
    }

    public void close(int event, int winner, Consumer<EventInfoDto> done, Consumer<Failure> failed) {
        send(post("events/close", "event", String.valueOf(event), "winner", String.valueOf(winner)),
                EventInfoDto.class, done, failed);
    }

    public void buy(int event, int option, long quantity, Consumer<PurchaseResultDto> done,
                    Consumer<Failure> failed) {
        send(post("trade/buy", "event", String.valueOf(event), "option", String.valueOf(option),
                "quantity", String.valueOf(quantity)), PurchaseResultDto.class, done, failed);
    }

    public void sell(int event, int option, long quantity, Consumer<PurchaseResultDto> done,
                     Consumer<Failure> failed) {
        send(post("trade/sell", "event", String.valueOf(event), "option", String.valueOf(option),
                "quantity", String.valueOf(quantity)), PurchaseResultDto.class, done, failed);
    }

    /** @param side "buy" or "sell" */
    public void order(int event, int option, String side, long quantity, double price,
                      Consumer<List<TradeDto>> done, Consumer<Failure> failed) {
        send(post("trade/order", "event", String.valueOf(event), "option", String.valueOf(option),
                "side", side, "quantity", String.valueOf(quantity), "price", String.valueOf(price)),
                TRADES, done, failed);
    }

    public void chat(int after, Consumer<ChatDto> done, Consumer<Failure> failed) {
        send(get("chat", "after", String.valueOf(after)), ChatDto.class, done, failed);
    }

    /** Sends a chat message in a form body rather than the address, since it is free text. */
    public void say(String message, Consumer<ChatLineDto> done, Consumer<Failure> failed) {
        RequestBody body = new FormBody.Builder().add("message", message).build();
        send(new Request.Builder().url(url("chat")).post(body).build(), ChatLineDto.class, done, failed);
    }

    /**
     * Logs out and waits a moment for it, then lets OkHttp's threads go. Used when the window closes:
     * the name should be free for somebody else at once rather than when the session lapses, and the
     * program cannot finish while OkHttp still has threads of its own running.
     */
    public void shutDown() {
        Call logout = http.newBuilder().callTimeout(2, TimeUnit.SECONDS).build().newCall(post("logout"));
        try {
            // The answer does not matter; only that the server was told.
            logout.execute().close();
        } catch (IOException unreachable) {
            // The server is gone; the session will lapse there on its own.
        }
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }

    private void forgetSession(Runnable done) {
        cookies.clear();
        deliverOn.execute(done);
    }

    private HttpUrl url(String path, String... namesAndValues) {
        HttpUrl base = HttpUrl.parse(address + "/" + path);
        if (base == null) {
            throw new IllegalStateException("\"" + address + "\" is not a web address.");
        }
        HttpUrl.Builder url = base.newBuilder();
        for (int i = 0; i + 1 < namesAndValues.length; i += 2) {
            url.addQueryParameter(namesAndValues[i], namesAndValues[i + 1]);
        }
        return url.build();
    }

    private Request get(String path, String... namesAndValues) {
        return new Request.Builder().url(url(path, namesAndValues)).get().build();
    }

    private Request post(String path, String... namesAndValues) {
        return new Request.Builder().url(url(path, namesAndValues)).post(NOTHING).build();
    }

    /** Sends a request, and hands over either what it answered or why it did not succeed. */
    private <T> void send(Request request, Type type, Consumer<T> done, Consumer<Failure> failed) {
        http.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException unreachable) {
                deliverOn.execute(() -> failed.accept(new Failure(Failure.UNREACHABLE,
                        "Cannot reach the Guess Market server at " + address + ". Please check that Tomcat"
                                + " is running with guess-market.war deployed.", List.of())));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (ResponseBody body = response.body()) {
                    String json = body == null ? "" : body.string();
                    if (response.isSuccessful()) {
                        T answer = gson.fromJson(json, type);
                        deliverOn.execute(() -> done.accept(answer));
                    } else {
                        Failure failure = failureOf(response.code(), json);
                        deliverOn.execute(() -> failed.accept(failure));
                    }
                } catch (IOException | JsonParseException garbled) {
                    deliverOn.execute(() -> failed.accept(new Failure(response.code(),
                            "The server's answer could not be read: " + garbled.getMessage(), List.of())));
                }
            }
        });
    }

    /** The server explains every refusal in an ErrorDto; anything else is described by its status. */
    private Failure failureOf(int status, String json) {
        try {
            ErrorDto error = gson.fromJson(json, ErrorDto.class);
            if (error != null && error.message() != null) {
                return new Failure(status, error.message(),
                        error.problems() == null ? List.of() : error.problems());
            }
        } catch (JsonParseException notAnExplanation) {
            // Not one of ours - an error page from Tomcat itself, for instance.
        }
        return new Failure(status, "The server answered with status " + status + ".", List.of());
    }
}
