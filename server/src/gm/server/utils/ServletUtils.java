package gm.server.utils;

import gm.engine.api.GuessMarketEngine;
import gm.engine.chat.ChatRoom;
import gm.engine.impl.GuessMarketEngineImpl;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Creates the market, the chat room and the register of names in use once, when the application
 * starts, and hands them to every servlet.
 * <p>
 * Servlets are shared by every request and must hold no state of their own, so whatever the whole
 * server shares lives in the servlet context. Creating it here, before the first request can arrive,
 * means no servlet ever has to race another to create it.
 */
@WebListener
public final class ServletUtils implements ServletContextListener {

    private static final String ENGINE = "gm.engine";
    private static final String CHAT_ROOM = "gm.chatRoom";
    private static final String LOGGED_IN = "gm.loggedInUsers";

    @Override
    public void contextInitialized(ServletContextEvent startup) {
        ServletContext context = startup.getServletContext();
        // The engine is written for one caller at a time; Tomcat calls it from many threads at once, so
        // the server - the layer that brings the threads - guards it before any servlet can reach it.
        context.setAttribute(ENGINE, new ThreadSafeEngine(new GuessMarketEngineImpl()));
        context.setAttribute(CHAT_ROOM, new ChatRoom());
        context.setAttribute(LOGGED_IN, new LoggedInUsers());
    }

    public static GuessMarketEngine engine(ServletContext context) {
        return (GuessMarketEngine) context.getAttribute(ENGINE);
    }

    public static ChatRoom chatRoom(ServletContext context) {
        return (ChatRoom) context.getAttribute(CHAT_ROOM);
    }

    public static LoggedInUsers loggedInUsers(ServletContext context) {
        return (LoggedInUsers) context.getAttribute(LOGGED_IN);
    }
}
