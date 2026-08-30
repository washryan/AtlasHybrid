package org.bukkit.event.player;

import java.net.InetAddress;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

/** Stores details for a player at the synchronous login admission stage. */
public class PlayerLoginEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final InetAddress address;
    private final String hostname;
    private final InetAddress realAddress;
    private Result result = Result.ALLOWED;
    private String message = "";

    public PlayerLoginEvent(Player player, String hostname, InetAddress address, InetAddress realAddress) {
        super(player);
        this.hostname = hostname;
        this.address = address;
        this.realAddress = realAddress;
    }

    public PlayerLoginEvent(Player player, String hostname, InetAddress address) {
        this(player, hostname, address, address);
    }

    public PlayerLoginEvent(Player player, String hostname, InetAddress address,
                            Result result, String message, InetAddress realAddress) {
        this(player, hostname, address, realAddress);
        this.result = result;
        this.message = message;
    }

    public InetAddress getRealAddress() { return realAddress; }
    public Result getResult() { return result; }
    public void setResult(Result result) { this.result = result; }
    public String getKickMessage() { return message; }
    public void setKickMessage(String message) { this.message = message; }
    public String getHostname() { return hostname; }
    public void allow() { result = Result.ALLOWED; message = ""; }
    public void disallow(Result result, String message) { this.result = result; this.message = message; }
    public InetAddress getAddress() { return address; }
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
