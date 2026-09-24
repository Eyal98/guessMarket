package gm.server.servlets;

import gm.engine.api.FileLoadException;
import gm.engine.api.GuessMarketEngine;
import gm.engine.api.GuessMarketException;
import gm.server.utils.BadRequestException;
import gm.server.utils.Json;
import gm.server.utils.ServletUtils;
import gm.server.utils.SessionUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * What every servlet acting for a logged-in user has in common, written once: who is asking, and
 * what becomes of a refusal.
 * <p>
 * A request from somebody not logged in is answered 401, so a client knows to go back to its login
 * screen. Whatever the market refuses is answered 400 with the market's own explanation, which is
 * already written to be shown as it is; a refused file also carries each of its problems separately.
 */
public abstract class MarketServlet extends HttpServlet {

    /** What a servlet does for a request, once it is known who sent it. */
    @FunctionalInterface
    protected interface Action {
        /** @return what to answer with, which is written out as JSON */
        Object perform(HttpServletRequest request, String userName) throws IOException, ServletException;
    }

    protected GuessMarketEngine engine() {
        return ServletUtils.engine(getServletContext());
    }

    /** Carries out an action for whoever is logged in, and answers with what it returns. */
    protected void answer(HttpServletRequest request, HttpServletResponse response, Action action)
            throws IOException, ServletException {
        String userName = SessionUtils.userName(request);
        if (userName == null) {
            Json.error(response, HttpServletResponse.SC_UNAUTHORIZED, "You are not logged in. Please log in first.");
            return;
        }
        try {
            Json.write(response, HttpServletResponse.SC_OK, action.perform(request, userName));
        } catch (FileLoadException refusal) {
            Json.error(response, HttpServletResponse.SC_BAD_REQUEST, refusal.getMessage(), refusal.problems());
        } catch (GuessMarketException | BadRequestException refusal) {
            Json.error(response, HttpServletResponse.SC_BAD_REQUEST, refusal.getMessage());
        }
    }
}
