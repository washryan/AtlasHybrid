package org.bukkit.command;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public abstract class Command {
    private final String name;
    private String description = "";
    private String permission;

    protected Command(String name) {
        this.name = Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
    }

    public final String getName() {
        return name;
    }

    public final String getDescription() {
        return description;
    }

    public final void setDescription(String description) {
        this.description = Objects.requireNonNullElse(description, "");
    }

    public final String getPermission() {
        return permission;
    }

    public final void setPermission(String permission) {
        this.permission = permission;
    }

    public final boolean testPermission(CommandSender sender) {
        return permission == null || permission.isBlank() || sender.hasPermission(permission);
    }

    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(args, "args");
        return Collections.emptyList();
    }

    public abstract boolean execute(CommandSender sender, String commandLabel, String[] args);
}
