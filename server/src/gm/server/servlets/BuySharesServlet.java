package gm.server.servlets;

import gm.server.utils.Parameters;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Buys shares of one option of an LMSR event for the logged-in user:
 * {@code POST /trade/buy?event=1&option=1&quantity=10}.
 */
@WebServlet(name = "BuySharesServlet", urlPatterns = "/trade/buy")
public class BuySharesServlet extends MarketServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) -> engine().buyShares(
                Parameters.wholeNumber(asked, "event"), userName,
                Parameters.wholeNumber(asked, "option"), Parameters.count(asked, "quantity")));
    }
}
