package gm.server.servlets;

import gm.server.utils.BadRequestException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.IOException;
import java.io.InputStream;

/**
 * Receives an events file from a user's computer: {@code POST /events/upload}, as a multipart body
 * whose part is named {@code file}, exactly as the course's upload example sends it.
 * <p>
 * The file is read straight from the request and never written anywhere. The exercise is emphatic
 * that the checker's server has no permission to write files, and a part larger than the container's
 * threshold would otherwise be spilled to a temporary file on disk; the threshold is therefore set as
 * high as the largest part accepted, so every upload stays in memory from start to finish.
 */
@WebServlet(name = "UploadEventsServlet", urlPatterns = "/events/upload")
@MultipartConfig(fileSizeThreshold = UploadEventsServlet.LARGEST_FILE,
        maxFileSize = UploadEventsServlet.LARGEST_FILE, maxRequestSize = UploadEventsServlet.LARGEST_FILE * 2L)
public class UploadEventsServlet extends MarketServlet {

    /** Far beyond any events file anybody would write, and still nothing to hold in memory. */
    static final int LARGEST_FILE = 2 * 1024 * 1024;

    private static final String FILE_PART = "file";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        answer(request, response, (asked, userName) -> {
            Part file = filePartOf(asked);
            try (InputStream content = file.getInputStream()) {
                return engine().uploadEvents(userName, file.getSubmittedFileName(), content);
            }
        });
    }

    private static Part filePartOf(HttpServletRequest request) throws IOException, ServletException {
        if (request.getContentType() == null || !request.getContentType().startsWith("multipart/")) {
            throw new BadRequestException("An events file must be sent as a multipart upload.");
        }
        Part file;
        try {
            file = request.getPart(FILE_PART);
        } catch (IllegalStateException tooLarge) {
            throw new BadRequestException("The file is too large. An events file may be at most "
                    + LARGEST_FILE / 1024 + " KB.");
        }
        if (file == null || file.getSubmittedFileName() == null) {
            throw new BadRequestException("The upload has no file in it. Send the file as a part named \""
                    + FILE_PART + "\".");
        }
        return file;
    }
}
