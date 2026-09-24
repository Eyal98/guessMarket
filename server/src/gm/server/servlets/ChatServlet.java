package gm.server.servlets;

import gm.engine.chat.ChatRoom;
import gm.server.utils.BadRequestException;
import gm.server.utils.Parameters;
import gm.server.utils.ServletUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * The chat room of the bonus, where everybody sees what everybody writes.
 * <p>
 * {@code GET /chat?after=12} gives the messages after the ones the client already has, the delta
 * fetching of the course's chat example; {@code POST /chat} with a {@code message} parameter adds one,
 * signed with the name of whoever is logged in, so nobody can write in somebody else's name.
 */
@WebServlet(name = "ChatServlet", urlPatterns = "/chat")
public class ChatServlet extends MarketServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) -> {
            try {
                return room().after(Parameters.countAlreadyHeld(asked, "after"));
            } catch (IllegalArgumentException refusal) {
                throw new BadRequestException(refusal.getMessage());
            }
        });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) -> {
            try {
                return room().say(userName, asked.getParameter("message"));
            } catch (IllegalArgumentException refusal) {
                throw new BadRequestException(refusal.getMessage());
            }
        });
    }

    private ChatRoom room() {
        return ServletUtils.chatRoom(getServletContext());
    }
}
