package gm.server.servlets;

import gm.engine.model.orderbook.OrderSide;
import gm.server.utils.BadRequestException;
import gm.server.utils.Parameters;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;

/**
 * Places an order in one option's book for the logged-in user, and settles whatever it can at once:
 * {@code POST /trade/order?event=2&option=1&side=buy&quantity=10&price=0.58}.
 */
@WebServlet(name = "SubmitOrderServlet", urlPatterns = "/trade/order")
public class SubmitOrderServlet extends MarketServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) -> engine().submitOrder(
                Parameters.wholeNumber(asked, "event"), userName,
                Parameters.wholeNumber(asked, "option"), sideOf(asked),
                Parameters.count(asked, "quantity"), Parameters.amount(asked, "price")));
    }

    private static OrderSide sideOf(HttpServletRequest request) {
        String side = Parameters.text(request, "side");
        return switch (side.toLowerCase(Locale.ROOT)) {
            case "buy" -> OrderSide.BUY;
            case "sell" -> OrderSide.SELL;
            default -> throw new BadRequestException("The \"side\" parameter is \"" + side
                    + "\", but it must be buy or sell.");
        };
    }
}
