package org.bukkit.event;

public class EventException extends Exception {
    private static final long serialVersionUID = 3532808232324183999L;

    public EventException() { }
    public EventException(Throwable cause) { super(cause); }
    public EventException(Throwable cause, String message) { super(message, cause); }
    public EventException(String message) { super(message); }
}
