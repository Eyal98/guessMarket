package gm.server.servlets;

import gm.server.utils.Parameters;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Decides an event on one of its options and pays the winners, which only its market maker may do:
 * {@code POST /events/close?event=1&winner=2}.
 */
@WebServlet(name = "CloseEventServlet", urlPatterns = "/events/close")
public class CloseEventServlet extends MarketServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) -> engine().closeEvent(
                Parameters.wholeNumber(asked, "event"), userName, Parameters.wholeNumber(asked, "winner")));
    }
}
