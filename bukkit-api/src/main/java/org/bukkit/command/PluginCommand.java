package org.bukkit.command;

import java.util.Objects;
import org.bukkit.plugin.Plugin;

public final class PluginCommand extends Command {
    private final Plugin owner;
    private volatile CommandExecutor executor;

    public PluginCommand(String name, Plugin owner) {
        super(name);
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public Plugin getPlugin() {
        return owner;
    }

    public void setExecutor(CommandExecutor executor) {
        this.executor = executor;
    }

    public CommandExecutor getExecutor() {
        return executor;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        CommandExecutor current = executor;
        return current != null && testPermission(sender)
            && current.onCommand(sender, this, commandLabel, args.clone());
    }
}
