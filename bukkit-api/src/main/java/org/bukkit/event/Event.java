package org.bukkit.event;

public abstract class Event {
    private String name;
    private final boolean asynchronous;

    public Event() {
        this(false);
    }

    public Event(boolean asynchronous) {
        this.asynchronous = asynchronous;
    }

    public abstract HandlerList getHandlers();

    public String getEventName() {
        if (name == null) name = getClass().getSimpleName();
        return name;
    }

    public final boolean isAsynchronous() { return asynchronous; }
}
