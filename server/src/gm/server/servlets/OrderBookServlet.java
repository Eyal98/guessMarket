package gm.server.servlets;

import gm.server.utils.Parameters;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Every book of one order book event, the statistics of each option, and where every participant
 * stands: {@code GET /events/book?event=2}.
 */
@WebServlet(name = "OrderBookServlet", urlPatterns = "/events/book")
public class OrderBookServlet extends MarketServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) ->
                engine().orderBookState(Parameters.wholeNumber(asked, "event")));
    }
}
