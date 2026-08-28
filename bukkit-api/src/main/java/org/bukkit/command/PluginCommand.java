package org.bukkit.command;

import java.util.Objects;
import org.bukkit.plugin.Plugin;

public final class PluginCommand extends Command {
    private final Plugin owner;
    private volatile CommandExecutor executor;
    private volatile TabCompleter completer;

    public PluginCommand(String name, Plugin owner) {
        super(name);
        this.owner = Objects.requireNonNull(owner, "owner");
        this.executor = owner;
    }

    public Plugin getPlugin() {
        return owner;
    }

    public void setExecutor(CommandExecutor executor) {
        this.executor = executor == null ? owner : executor;
    }

    public CommandExecutor getExecutor() {
        return executor;
    }

    public void setTabCompleter(TabCompleter completer) {
        this.completer = completer;
    }

    public TabCompleter getTabCompleter() {
        return completer;
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        return testPermission(sender) && executor.onCommand(sender, this, commandLabel, args.clone());
    }

    @Override
    public java.util.List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(args, "args");
        TabCompleter current = completer;
        if (current == null && executor instanceof TabCompleter executorCompleter) {
            current = executorCompleter;
        }
        if (current != null) {
            java.util.List<String> suggestions = current.onTabComplete(sender, this, alias, args.clone());
            if (suggestions != null) return suggestions;
        }
        return super.tabComplete(sender, alias, args);
    }
}
