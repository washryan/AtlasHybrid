package dev.atlashybrid.runtime.command;

import dev.atlashybrid.diagnostics.CompatibilityRuntime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;

public final class CommandRegistry {
    private final Map<String, PluginCommand> commands = new LinkedHashMap<>();

    public synchronized PluginCommand register(String name, Plugin owner) {
        String key = key(name);
        PluginCommand command = new PluginCommand(key, owner);
        if (commands.putIfAbsent(key, command) != null) {
            throw new IllegalStateException("Duplicate plugin command: " + name);
        }
        return command;
    }

    public synchronized PluginCommand get(String name) {
        return commands.get(key(name));
    }

    public synchronized void registerAlias(String alias, PluginCommand command) {
        String key = key(alias);
        if (commands.putIfAbsent(key, command) != null) throw new IllegalStateException("Duplicate plugin command alias: " + alias);
    }

    public synchronized Set<String> names() {
        return Set.copyOf(commands.keySet());
    }

    public synchronized void unregister(Plugin plugin) {
        commands.values().removeIf(command -> command.getPlugin() == plugin);
    }

    public boolean dispatch(String name, CommandSender sender, String[] args) {
        PluginCommand command = get(name);
        if (command == null) return false;
        try (CompatibilityRuntime.Scope ignored = CompatibilityRuntime.enter(command.getPlugin().getName())) {
            try {
                return command.execute(sender, command.getName(), args);
            } catch (Throwable throwable) {
                CompatibilityRuntime.reportLinkageFailure(throwable);
                if (throwable instanceof RuntimeException exception) throw exception;
                if (throwable instanceof Error error) throw error;
                throw new IllegalStateException("Plugin command failed", throwable);
            }
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT).strip();
    }
}
