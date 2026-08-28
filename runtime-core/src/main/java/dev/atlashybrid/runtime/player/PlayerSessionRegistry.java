package dev.atlashybrid.runtime.player;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/** Thread-safe registry for the Bukkit adapters that belong to active player sessions. */
public final class PlayerSessionRegistry {
    private final Map<UUID, Player> playersById = new LinkedHashMap<>();
    private final Map<String, UUID> idsByName = new LinkedHashMap<>();
    private final Consumer<? super Player> cleanup;

    public PlayerSessionRegistry(Consumer<? super Player> cleanup) {
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
    }

    public synchronized Player getOrRegister(UUID id, String name, Supplier<? extends Player> factory) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(factory, "factory");
        Player current = playersById.get(id);
        if (current != null) return current;

        String nameKey = nameKey(name);
        UUID nameOwner = idsByName.get(nameKey);
        if (nameOwner != null && !nameOwner.equals(id)) {
            throw new IllegalStateException("Online player name is already registered: " + name);
        }

        Player created = Objects.requireNonNull(factory.get(), "factory returned null");
        if (!id.equals(created.getUniqueId()) || !name.equalsIgnoreCase(created.getName())) {
            cleanup.accept(created);
            throw new IllegalArgumentException("Player adapter identity does not match the session");
        }
        playersById.put(id, created);
        idsByName.put(nameKey, id);
        return created;
    }

    public synchronized Collection<? extends Player> onlinePlayers() {
        return List.copyOf(playersById.values());
    }

    public synchronized Player getPlayer(UUID id) {
        return id == null ? null : playersById.get(id);
    }

    public synchronized Player getPlayerExact(String name) {
        if (name == null) return null;
        UUID id = idsByName.get(nameKey(name));
        return id == null ? null : playersById.get(id);
    }

    public synchronized void remove(UUID id) {
        if (id == null) return;
        Player removed = playersById.remove(id);
        if (removed == null) return;
        idsByName.remove(nameKey(removed.getName()), id);
        cleanup.accept(removed);
    }

    public synchronized void clear() {
        List<Player> removed = List.copyOf(playersById.values());
        playersById.clear();
        idsByName.clear();
        removed.forEach(cleanup);
    }

    private static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
