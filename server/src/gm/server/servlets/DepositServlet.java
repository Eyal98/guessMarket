package gm.server.servlets;

import gm.server.utils.Parameters;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Loads funds into the logged-in user's account: {@code POST /account/deposit?amount=500}. */
@WebServlet(name = "DepositServlet", urlPatterns = "/account/deposit")
public class DepositServlet extends MarketServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) ->
                engine().deposit(userName, Parameters.amount(asked, "amount")));
    }
}
