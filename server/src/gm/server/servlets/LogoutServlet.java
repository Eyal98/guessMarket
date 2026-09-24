package gm.server.servlets;

import gm.server.utils.Json;
import gm.server.utils.SessionUtils;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * Logs out: {@code POST /logout}. The name is free again at once, and whoever logs in with it next
 * finds the account exactly as it was left. Logging out when not logged in is not an error.
 */
@WebServlet(name = "LogoutServlet", urlPatterns = "/logout")
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SessionUtils.logOut(request);
        Json.write(response, HttpServletResponse.SC_OK, Map.of("loggedOut", true));
    }
}
