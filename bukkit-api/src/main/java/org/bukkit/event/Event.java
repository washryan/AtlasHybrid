package org.bukkit.event;

public abstract class Event {
    public abstract HandlerList getHandlers();

    public String getEventName() {
        return getClass().getSimpleName();
    }
}
