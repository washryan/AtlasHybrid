package dev.atlashybrid.runtime.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class PlayerSessionRegistryTest {
    private static final UUID FIRST_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID SECOND_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");

    @Test
    void startsWithNoOnlinePlayers() {
        assertTrue(registry().onlinePlayers().isEmpty());
    }

    @Test
    void joinAddsOneRealAdapterAndPreservesIdentity() {
        PlayerSessionRegistry registry = registry();
        FakePlayer player = new FakePlayer(FIRST_ID, "Alice");
        Player registered = registry.getOrRegister(FIRST_ID, "Alice", () -> player);
        assertSame(player, registered);
        assertSame(player, registry.getOrRegister(FIRST_ID, "Alice", () -> new FakePlayer(FIRST_ID, "Alice")));
        assertEquals(1, registry.onlinePlayers().size());
        assertTrue(registered instanceof Entity);
        Entity entity = registered;
        assertEquals(FIRST_ID, entity.getUniqueId());
        assertEquals(player.getEntityId(), entity.getEntityId());
        assertSame(player.getWorld(), entity.getWorld());
        assertEquals(player.getLocation(), entity.getLocation());
    }

    @Test
    void multiplePlayersHaveNoDuplicatesAndKeepJoinOrder() {
        PlayerSessionRegistry registry = registry();
        FakePlayer first = register(registry, FIRST_ID, "Alice");
        FakePlayer second = register(registry, SECOND_ID, "Bob");
        registry.getOrRegister(FIRST_ID, "Alice", () -> new FakePlayer(FIRST_ID, "Alice"));
        assertEquals(java.util.List.of(first, second), registry.onlinePlayers());
    }

    @Test
    void lookupByUuidAndExactNameUsesTheSameAdapter() {
        PlayerSessionRegistry registry = registry();
        FakePlayer player = register(registry, FIRST_ID, "Alice");
        assertSame(player, registry.getPlayer(FIRST_ID));
        assertSame(player, registry.getPlayerExact("alice"));
        assertSame(player, registry.getPlayerExact("ALICE"));
        assertNull(registry.getPlayer(SECOND_ID));
        assertNull(registry.getPlayerExact("Ali"));
    }

    @Test
    void permissionStateRemainsAttachedToTheSessionAdapter() {
        PlayerSessionRegistry registry = registry();
        FakePlayer player = register(registry, FIRST_ID, "Alice");
        player.permission = true;
        assertTrue(registry.getPlayer(FIRST_ID).hasPermission("atlas.session"));
        assertSame(player, registry.onlinePlayers().iterator().next());
    }

    @Test
    void quitRemovesLookupsAndRunsCleanupOnce() {
        AtomicInteger cleanups = new AtomicInteger();
        PlayerSessionRegistry registry = new PlayerSessionRegistry(player -> {
            ((FakePlayer) player).closed = true;
            cleanups.incrementAndGet();
        });
        FakePlayer player = register(registry, FIRST_ID, "Alice");
        registry.remove(FIRST_ID);
        registry.remove(FIRST_ID);
        assertTrue(player.closed);
        assertEquals(1, cleanups.get());
        assertTrue(registry.onlinePlayers().isEmpty());
        assertNull(registry.getPlayer(FIRST_ID));
        assertNull(registry.getPlayerExact("Alice"));
    }

    @Test
    void snapshotsAreReadOnlyAndUnaffectedByLaterJoins() {
        PlayerSessionRegistry registry = registry();
        FakePlayer first = register(registry, FIRST_ID, "Alice");
        Collection<? extends Player> snapshot = registry.onlinePlayers();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
        register(registry, SECOND_ID, "Bob");
        assertEquals(java.util.List.of(first), snapshot);
        assertEquals(2, registry.onlinePlayers().size());
    }

    @Test
    void clearRemovesAllSessionsAndCleansEachAdapter() {
        AtomicInteger cleanups = new AtomicInteger();
        PlayerSessionRegistry registry = new PlayerSessionRegistry(player -> cleanups.incrementAndGet());
        register(registry, FIRST_ID, "Alice");
        register(registry, SECOND_ID, "Bob");
        registry.clear();
        assertEquals(2, cleanups.get());
        assertTrue(registry.onlinePlayers().isEmpty());
    }

    @Test
    void rejectsConflictingOnlineNamesAndMismatchedFactories() {
        PlayerSessionRegistry registry = registry();
        register(registry, FIRST_ID, "Alice");
        assertThrows(IllegalStateException.class,
            () -> registry.getOrRegister(SECOND_ID, "alice", () -> new FakePlayer(SECOND_ID, "alice")));
        assertThrows(IllegalArgumentException.class,
            () -> registry().getOrRegister(FIRST_ID, "Alice", () -> new FakePlayer(SECOND_ID, "Bob")));
    }

    @Test
    void connectingPlayerIsHiddenUntilPromotedAndKeepsIdentity() {
        PlayerSessionRegistry registry = registry();
        FakePlayer player = new FakePlayer(FIRST_ID, "Alice");
        assertSame(player, registry.beginConnecting(FIRST_ID, "Alice", () -> player));
        assertTrue(registry.isConnecting(FIRST_ID));
        assertTrue(registry.onlinePlayers().isEmpty());
        assertNull(registry.getPlayer(FIRST_ID));
        assertNull(registry.getPlayerExact("Alice"));
        assertSame(player, registry.promote(FIRST_ID));
        assertFalse(registry.isConnecting(FIRST_ID));
        assertSame(player, registry.getPlayer(FIRST_ID));
        assertSame(player, registry.onlinePlayers().iterator().next());
    }

    @Test
    void deniedConnectingPlayerIsRemovedAndCleanedExactlyOnce() {
        AtomicInteger cleanups = new AtomicInteger();
        PlayerSessionRegistry registry = new PlayerSessionRegistry(player -> cleanups.incrementAndGet());
        registry.beginConnecting(FIRST_ID, "Alice", () -> new FakePlayer(FIRST_ID, "Alice"));
        registry.remove(FIRST_ID);
        registry.remove(FIRST_ID);
        assertFalse(registry.isConnecting(FIRST_ID));
        assertTrue(registry.onlinePlayers().isEmpty());
        assertEquals(1, cleanups.get());
    }

    @Test
    void connectingNameCannotCollideWithAnotherSession() {
        PlayerSessionRegistry registry = registry();
        registry.beginConnecting(FIRST_ID, "Alice", () -> new FakePlayer(FIRST_ID, "Alice"));
        assertThrows(IllegalStateException.class,
            () -> registry.beginConnecting(SECOND_ID, "alice", () -> new FakePlayer(SECOND_ID, "alice")));
        assertThrows(IllegalStateException.class,
            () -> registry.getOrRegister(SECOND_ID, "ALICE", () -> new FakePlayer(SECOND_ID, "ALICE")));
    }

    private static PlayerSessionRegistry registry() {
        return new PlayerSessionRegistry(player -> { });
    }

    private static FakePlayer register(PlayerSessionRegistry registry, UUID id, String name) {
        FakePlayer player = new FakePlayer(id, name);
        return (FakePlayer) registry.getOrRegister(id, name, () -> player);
    }

    private static final class FakePlayer implements Player {
        private final UUID id;
        private final String name;
        private final World world = () -> "world";
        private boolean permission;
        private boolean closed;

        private FakePlayer(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override public UUID getUniqueId() { return id; }
        @Override public int getEntityId() { return id.hashCode(); }
        @Override public World getWorld() { return world; }
        @Override public String getDisplayName() { return name; }
        @Override public Location getLocation() { return new Location(world, 1.0D, 2.0D, 3.0D, 4.0F, 5.0F); }
        @Override public boolean teleport(Location location) { return false; }
        @Override public String getName() { return name; }
        @Override public void sendMessage(String message) { }
        @Override public boolean isOp() { return false; }
        @Override public void setOp(boolean value) { }
        @Override public boolean isPermissionSet(String permission) { return this.permission; }
        @Override public boolean isPermissionSet(Permission permission) { return this.permission; }
        @Override public boolean hasPermission(String permission) { return this.permission; }
        @Override public boolean hasPermission(Permission permission) { return this.permission; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return null; }
        @Override public void removeAttachment(PermissionAttachment attachment) { }
        @Override public void recalculatePermissions() { }
        @Override public Set<PermissionAttachmentInfo> getEffectivePermissions() { return Set.of(); }
    }
}
