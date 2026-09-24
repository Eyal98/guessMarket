package gm.server.servlets;

import gm.server.utils.Parameters;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Everything about one LMSR event, the values of its options, its account and its history:
 * {@code GET /events/lmsr?event=1}.
 */
@WebServlet(name = "LmsrEventServlet", urlPatterns = "/events/lmsr")
public class LmsrEventServlet extends MarketServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) ->
                engine().marketState(Parameters.wholeNumber(asked, "event")));
    }
}
