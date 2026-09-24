package gm.server.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * The logged-in user's own details, and nobody else's: {@code GET /account}. There is deliberately no
 * way to ask for another user's details; everybody else is seen only through {@code /users}.
 */
@WebServlet(name = "AccountServlet", urlPatterns = "/account")
public class AccountServlet extends MarketServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) -> engine().userDetail(userName));
    }
}
