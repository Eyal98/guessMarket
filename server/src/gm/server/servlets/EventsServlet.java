package gm.server.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** Every event on the server, of every kind and in every state: {@code GET /events}. */
@WebServlet(name = "EventsServlet", urlPatterns = "/events")
public class EventsServlet extends MarketServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) -> engine().listEvents());
    }
}
