package gm.engine;

import gm.engine.model.User;

/** Users with money in their pockets, which is how almost every test wants to start. */
public final class TestUsers {

    private TestUsers() {
    }

    /** A user who has already loaded {@code cash} into an otherwise empty account. */
    public static User funded(String name, double cash) {
        User user = new User(name);
        if (cash > 0) {
            user.deposit(cash);
        }
        return user;
    }
}
