package gm.engine.model;

/** Whether an event is still trading or has already been decided. */
public enum EventStatus {

    OPEN("Open"),
    CLOSED("Closed");

    private final String displayName;

    EventStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
