package org.bukkit.event.player;

import java.net.InetAddress;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Stores the authenticated or offline profile data for an incoming connection. */
public class AsyncPlayerPreLoginEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private Result result = Result.ALLOWED;
    private String message = "";
    private final String name;
    private final InetAddress address;
    private final UUID uniqueId;

    @Deprecated
    public AsyncPlayerPreLoginEvent(String name, InetAddress address) {
        this(name, address, null);
    }

    public AsyncPlayerPreLoginEvent(String name, InetAddress address, UUID uniqueId) {
        super(true);
        this.name = name;
        this.address = address;
        this.uniqueId = uniqueId;
    }

    public Result getLoginResult() { return result; }

    @Deprecated
    public PlayerPreLoginEvent.Result getResult() {
        return result == null ? null : PlayerPreLoginEvent.Result.valueOf(result.name());
    }

    public void setLoginResult(Result result) { this.result = result; }

    @Deprecated
    public void setResult(PlayerPreLoginEvent.Result result) {
        this.result = result == null ? null : Result.valueOf(result.name());
    }

    public String getKickMessage() { return message; }
    public void setKickMessage(String message) { this.message = message; }
    public void allow() { result = Result.ALLOWED; message = ""; }
    public void disallow(Result result, String message) { this.result = result; this.message = message; }

    @Deprecated
    public void disallow(PlayerPreLoginEvent.Result result, String message) {
        this.result = result == null ? null : Result.valueOf(result.name());
        this.message = message;
    }

    public String getName() { return name; }
    public InetAddress getAddress() { return address; }
    public UUID getUniqueId() { return uniqueId; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }

    public enum Result {
        ALLOWED,
        KICK_FULL,
        KICK_BANNED,
        KICK_WHITELIST,
        KICK_OTHER
    }
}
