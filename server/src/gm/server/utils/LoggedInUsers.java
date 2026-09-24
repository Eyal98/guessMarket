package gm.server.utils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The names somebody is using right now.
 * <p>
 * The exercise asks that a name already in use be refused at login. A name belongs to whoever holds a
 * live session with it: logging out, or letting the session lapse, frees it again, and whoever logs in
 * with it next finds that person's account exactly as it was left. There are no passwords, by the
 * exercise's own request, so a name is all an account has.
 * <p>
 * Names are compared without regard to case, like all English input in the system.
 */
public final class LoggedInUsers {

    private final Set<String> names = new HashSet<>();

    /**
     * Takes a name for a new session.
     *
     * @return false if somebody is already using it
     */
    public synchronized boolean claim(String name) {
        return names.add(key(name));
    }

    /** Gives a name back. Giving back a name nobody holds does nothing. */
    public synchronized void release(String name) {
        if (name != null) {
            names.remove(key(name));
        }
    }

    public synchronized boolean isInUse(String name) {
        return names.contains(key(name));
    }

    private static String key(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
