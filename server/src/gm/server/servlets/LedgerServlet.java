package gm.server.servlets;

import gm.server.utils.Parameters;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * The lines of the logged-in user's account after the ones the client already has:
 * {@code GET /account/ledger?after=12}. Leaving {@code after} out asks for the whole account.
 */
@WebServlet(name = "LedgerServlet", urlPatterns = "/account/ledger")
public class LedgerServlet extends MarketServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) ->
                engine().ledger(userName, Parameters.countAlreadyHeld(asked, "after")));
    }
}
