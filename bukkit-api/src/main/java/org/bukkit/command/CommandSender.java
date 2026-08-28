package org.bukkit.command;

import org.bukkit.permissions.Permissible;

public interface CommandSender extends Permissible {
    String getName();

    void sendMessage(String message);

}
