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
    private final Map<UUID, Player> connectingById = new LinkedHashMap<>();
    private final Map<String, UUID> connectingIdsByName = new LinkedHashMap<>();
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
        if (connectingById.containsKey(id)) {
            throw new IllegalStateException("Player session is still connecting: " + name);
        }

        String nameKey = nameKey(name);
        UUID nameOwner = idsByName.get(nameKey);
        UUID connectingOwner = connectingIdsByName.get(nameKey);
        if ((nameOwner != null && !nameOwner.equals(id)) || connectingOwner != null) {
            throw new IllegalStateException("Online player name is already registered: " + name);
        }

        Player created = validated(id, name, factory);
        playersById.put(id, created);
        idsByName.put(nameKey, id);
        return created;
    }

    /** Creates an adapter for login admission without exposing it as online. */
    public synchronized Player beginConnecting(UUID id, String name, Supplier<? extends Player> factory) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(factory, "factory");
        Player current = connectingById.get(id);
        if (current != null) return current;
        if (playersById.containsKey(id)) {
            throw new IllegalStateException("Player session is already online: " + name);
        }

        String nameKey = nameKey(name);
        UUID connectingOwner = connectingIdsByName.get(nameKey);
        UUID onlineOwner = idsByName.get(nameKey);
        if ((connectingOwner != null && !connectingOwner.equals(id))
            || (onlineOwner != null && !onlineOwner.equals(id))) {
            throw new IllegalStateException("Player name is already registered: " + name);
        }

        Player created = validated(id, name, factory);
        connectingById.put(id, created);
        connectingIdsByName.put(nameKey, id);
        return created;
    }

    /** Moves a previously admitted adapter into the public online registry. */
    public synchronized Player promote(UUID id) {
        Objects.requireNonNull(id, "id");
        Player connecting = connectingById.remove(id);
        if (connecting == null) return playersById.get(id);
        connectingIdsByName.remove(nameKey(connecting.getName()), id);

        Player replaced = playersById.put(id, connecting);
        if (replaced != null && replaced != connecting) {
            idsByName.remove(nameKey(replaced.getName()), id);
            cleanup.accept(replaced);
        }
        idsByName.put(nameKey(connecting.getName()), id);
        return connecting;
    }

    public synchronized boolean isConnecting(UUID id) {
        return id != null && connectingById.containsKey(id);
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
        Player connecting = connectingById.remove(id);
        if (connecting != null) {
            connectingIdsByName.remove(nameKey(connecting.getName()), id);
            cleanup.accept(connecting);
        }
        Player removed = playersById.remove(id);
        if (removed == null) return;
        idsByName.remove(nameKey(removed.getName()), id);
        cleanup.accept(removed);
    }

    public synchronized void clear() {
        List<Player> removed = List.copyOf(playersById.values());
        List<Player> connecting = List.copyOf(connectingById.values());
        playersById.clear();
        idsByName.clear();
        connectingById.clear();
        connectingIdsByName.clear();
        removed.forEach(cleanup);
        connecting.forEach(cleanup);
    }

    private Player validated(UUID id, String name, Supplier<? extends Player> factory) {
        Player created = Objects.requireNonNull(factory.get(), "factory returned null");
        if (!id.equals(created.getUniqueId()) || !name.equalsIgnoreCase(created.getName())) {
            cleanup.accept(created);
            throw new IllegalArgumentException("Player adapter identity does not match the session");
        }
        return created;
    }

    private static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
