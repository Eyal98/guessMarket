package gm.server.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gm.dto.ErrorDto;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/** Writes answers as JSON, which is what every client of this server reads. */
public final class Json {

    /**
     * Gson keeps no state between calls, so one instance serves every thread. HTML escaping is off:
     * nothing here is ever put into a web page, and it would turn every angle bracket in a message into
     * an escape sequence that anybody reading the answer in Postman would have to decode.
     */
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Json() {
    }

    public static void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().print(GSON.toJson(body));
    }

    /** A refusal, with the reason written for the person who made the request. */
    public static void error(HttpServletResponse response, int status, String message) throws IOException {
        error(response, status, message, List.of());
    }

    public static void error(HttpServletResponse response, int status, String message, List<String> problems)
            throws IOException {
        write(response, status, new ErrorDto(message, problems));
    }
}
