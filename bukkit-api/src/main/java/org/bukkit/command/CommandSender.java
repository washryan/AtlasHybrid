package org.bukkit.command;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.permissions.Permissible;

public interface CommandSender extends Permissible {
    String getName();

    void sendMessage(String message);

    default Server getServer() { return Bukkit.getServer(); }

}
