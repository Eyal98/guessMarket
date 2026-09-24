package gm.server.servlets;

import gm.dto.UserDetailDto;
import gm.engine.api.GuessMarketEngine;
import gm.engine.api.GuessMarketException;
import gm.server.utils.Json;
import gm.server.utils.LoggedInUsers;
import gm.server.utils.ServletUtils;
import gm.server.utils.SessionUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Logs a user in by name: {@code POST /login?username=Avrum}.
 * <p>
 * No password and no sign-up, as the exercise asks. A name nobody is using right now is accepted: the
 * first time it is seen it becomes a new user with an empty account, and after that it is the same
 * person coming back. A name somebody else is logged in with is refused with 409, and the client asks
 * for another.
 */
@WebServlet(name = "LoginServlet", urlPatterns = "/login")
public class LoginServlet extends HttpServlet {

    /** Long enough for any real name, short enough to fit a column. */
    private static final int LONGEST_NAME = 30;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String wanted = request.getParameter("username");
        if (wanted == null || wanted.isBlank()) {
            Json.error(response, HttpServletResponse.SC_BAD_REQUEST, "Please enter a user name.");
            return;
        }
        String name = wanted.trim();
        if (name.length() > LONGEST_NAME) {
            Json.error(response, HttpServletResponse.SC_BAD_REQUEST,
                    "A user name can be at most " + LONGEST_NAME + " characters long.");
            return;
        }

        String current = SessionUtils.userName(request);
        if (current != null) {
            answerForExistingSession(response, current, name);
            return;
        }

        LoggedInUsers loggedIn = ServletUtils.loggedInUsers(getServletContext());
        if (!loggedIn.claim(name)) {
            Json.error(response, HttpServletResponse.SC_CONFLICT, "Somebody is already logged in as \"" + name
                    + "\". Please choose another name.");
            return;
        }
        try {
            UserDetailDto user = engine().enterMarket(name);
            SessionUtils.logIn(request, user.name());
            Json.write(response, HttpServletResponse.SC_OK, user);
        } catch (GuessMarketException refusal) {
            loggedIn.release(name);
            Json.error(response, HttpServletResponse.SC_BAD_REQUEST, refusal.getMessage());
        }
    }

    /** Logging in again as oneself changes nothing; logging in as somebody else needs a logout first. */
    private void answerForExistingSession(HttpServletResponse response, String current, String wanted)
            throws IOException {
        if (current.equalsIgnoreCase(wanted)) {
            Json.write(response, HttpServletResponse.SC_OK, engine().userDetail(current));
        } else {
            Json.error(response, HttpServletResponse.SC_CONFLICT, "This session is already logged in as \""
                    + current + "\". Please log out first.");
        }
    }

    private GuessMarketEngine engine() {
        return ServletUtils.engine(getServletContext());
    }
}
