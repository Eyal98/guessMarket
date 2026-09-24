package gm.server.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Everybody in the market, with their balance and whether they run an event: {@code GET /users}.
 * Clients pull the whole list every time; it is short, and always being complete is worth more than
 * the bytes a delta would save.
 */
@WebServlet(name = "UsersServlet", urlPatterns = "/users")
public class UsersServlet extends MarketServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) -> engine().listUsers());
    }
}
