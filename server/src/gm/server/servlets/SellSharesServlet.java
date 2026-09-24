package gm.server.servlets;

import gm.server.utils.Parameters;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Sells shares of one option back to an LMSR event for the logged-in user:
 * {@code POST /trade/sell?event=1&option=1&quantity=10}.
 */
@WebServlet(name = "SellSharesServlet", urlPatterns = "/trade/sell")
public class SellSharesServlet extends MarketServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) -> engine().sellShares(
                Parameters.wholeNumber(asked, "event"), userName,
                Parameters.wholeNumber(asked, "option"), Parameters.count(asked, "quantity")));
    }
}
