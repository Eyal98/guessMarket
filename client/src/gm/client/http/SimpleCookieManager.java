package gm.client.http;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the cookies the server sets and sends them back with every request, which is all that makes
 * the requests of one client a single session on the server. Without it every request would arrive
 * as a stranger and be refused as not logged in.
 * <p>
 * The same shape as the course's own cookie manager: cookies are kept per host, and a cookie sent
 * again under a name already held replaces the old one.
 */
public final class SimpleCookieManager implements CookieJar {

    private final Map<String, Map<String, Cookie>> cookiesByHost = new HashMap<>();

    @Override
    public synchronized void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        Map<String, Cookie> held = cookiesByHost.computeIfAbsent(url.host(), ignored -> new HashMap<>());
        for (Cookie cookie : cookies) {
            held.put(cookie.name(), cookie);
        }
    }

    @Override
    public synchronized List<Cookie> loadForRequest(HttpUrl url) {
        Map<String, Cookie> held = cookiesByHost.get(url.host());
        if (held == null) {
            return List.of();
        }
        List<Cookie> matching = new ArrayList<>();
        for (Cookie cookie : held.values()) {
            if (cookie.matches(url)) {
                matching.add(cookie);
            }
        }
        return matching;
    }

    /** Forgets every cookie, which is how a client leaves its session behind after logging out. */
    public synchronized void clear() {
        cookiesByHost.clear();
    }
}
