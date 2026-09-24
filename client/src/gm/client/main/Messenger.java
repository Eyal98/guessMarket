package gm.client.main;

import gm.client.http.MarketServer;

/**
 * How any part of the screen tells the user what happened: a line in the status bar for what went
 * well, a dialogue for what the server refused. A refusal because the session is gone takes the user
 * back to the login screen instead, since nothing else can be done until they log in again.
 */
public interface Messenger {

    void status(String message);

    void refused(MarketServer.Failure failure);
}
