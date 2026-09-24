package gm.server.utils;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/**
 * Who is asking. A session knows exactly one thing about its user, their name, and it is set only by
 * a successful login.
 * <p>
 * It also notices sessions ending on their own. A client that closes properly logs out, but one that
 * is killed simply stops asking, and its session lapses a couple of minutes later; the name it held is
 * freed then, rather than being locked away until the server restarts.
 */
@WebListener
public final class SessionUtils implements HttpSessionListener {

    private static final String USER_NAME = "gm.userName";

    /** The name logged in on this request's session, or null if there is none. */
    public static String userName(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(USER_NAME);
    }

    public static void logIn(HttpServletRequest request, String userName) {
        request.getSession(true).setAttribute(USER_NAME, userName);
    }

    /** Ends the session, which frees the name through {@link #sessionDestroyed(HttpSessionEvent)}. */
    public static void logOut(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent ending) {
        HttpSession session = ending.getSession();
        String userName = (String) session.getAttribute(USER_NAME);
        ServletUtils.loggedInUsers(session.getServletContext()).release(userName);
    }
}
