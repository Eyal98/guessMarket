package gm.server.servlets;

import gm.server.utils.Parameters;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Starts an event trading, at the logged-in user's expense, which only its market maker may do:
 * {@code POST /events/open?event=1}.
 */
@WebServlet(name = "OpenEventServlet", urlPatterns = "/events/open")
public class OpenEventServlet extends MarketServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) ->
                engine().openEvent(Parameters.wholeNumber(asked, "event"), userName));
    }
}
