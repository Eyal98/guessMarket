package gm.client.http;

/**
 * Where the server is.
 * <p>
 * The exercise lets the client assume a Tomcat on localhost port 8080 with the submitted WAR under
 * its own name, so that is where it looks unless told otherwise. It can be told otherwise, as the
 * first argument on the command line or as the {@code gm.server} system property, for a Tomcat that
 * has to run on another port because something else already holds 8080.
 */
public final class ServerAddress {

    /** localhost:8080 and the context path Tomcat gives guess-market.war. */
    public static final String DEFAULT = "http://localhost:8080/guess-market";

    private static final String PROPERTY = "gm.server";

    private ServerAddress() {
    }

    /** The address to use: the first argument if there is one, then the property, then the default. */
    public static String from(String[] arguments) {
        String chosen = arguments.length > 0 && !arguments[0].isBlank()
                ? arguments[0]
                : System.getProperty(PROPERTY, DEFAULT);
        String trimmed = chosen.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
