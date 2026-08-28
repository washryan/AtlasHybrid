package dev.atlashybrid.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.atlashybrid.runtime.command.CommandRegistry;
import dev.atlashybrid.runtime.event.AtlasPluginManager;
import dev.atlashybrid.runtime.scheduler.AtlasScheduler;
import dev.atlashybrid.runtime.permission.AtlasPermissionRegistry;
import dev.atlashybrid.runtime.permission.PermissionProviderRegistry;
import dev.atlashybrid.runtime.service.AtlasServicesManager;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.junit.jupiter.api.Test;

class RuntimeCoreTest {
    @Test
    void tabExecutorComposesCommandExecutorAndTabCompleter() {
        assertTrue(CommandExecutor.class.isAssignableFrom(TabExecutor.class));
        assertTrue(TabCompleter.class.isAssignableFrom(TabExecutor.class));
    }

    @Test
    void pluginCommandRegistersExplicitTabCompleter() {
        FakePlugin owner = new FakePlugin();
        PluginCommand command = new PluginCommand("atlas", owner);
        TabCompleter completer = (sender, ignored, alias, args) -> List.of("alpha");
        assertNull(command.getTabCompleter());
        command.setTabCompleter(completer);
        assertSame(completer, command.getTabCompleter());
        assertEquals(List.of("alpha"), command.tabComplete(new FakeSender(), "atlas", new String[] { "" }));
        command.setExecutor((sender, ignored, label, args) -> true);
        command.setExecutor(null);
        assertSame(owner, command.getExecutor());
    }

    @Test
    void explicitCompleterOverridesTabExecutorAndEmptyListStopsFallback() {
        PluginCommand command = new PluginCommand("atlas", new FakePlugin());
        AtomicInteger executorCompletions = new AtomicInteger();
        command.setExecutor(new TabExecutor() {
            @Override public boolean onCommand(CommandSender sender, Command ignored, String label, String[] args) { return true; }
            @Override public List<String> onTabComplete(CommandSender sender, Command ignored, String alias, String[] args) {
                executorCompletions.incrementAndGet();
                return List.of("executor");
            }
        });
        command.setTabCompleter((sender, ignored, alias, args) -> List.of());
        assertEquals(List.of(), command.tabComplete(new FakeSender(), "atlas", new String[] { "" }));
        assertEquals(0, executorCompletions.get());
    }

    @Test
    void nullResultFallsBackToCommandDefaultWithoutCallingExecutorCompleter() {
        PluginCommand command = new PluginCommand("atlas", new FakePlugin());
        AtomicInteger executorCompletions = new AtomicInteger();
        command.setExecutor(new TabExecutor() {
            @Override public boolean onCommand(CommandSender sender, Command ignored, String label, String[] args) { return true; }
            @Override public List<String> onTabComplete(CommandSender sender, Command ignored, String alias, String[] args) {
                executorCompletions.incrementAndGet();
                return null;
            }
        });
        command.setTabCompleter((sender, ignored, alias, args) -> null);
        assertEquals(List.of(), command.tabComplete(new FakeSender(), "atlas", new String[] { "" }));
        assertEquals(0, executorCompletions.get());
    }

    @Test
    void executorTabCompleterReceivesClonedArguments() {
        PluginCommand command = new PluginCommand("atlas", new FakePlugin());
        String[] supplied = { "one", "two" };
        command.setExecutor(new TabExecutor() {
            @Override public boolean onCommand(CommandSender sender, Command ignored, String label, String[] args) { return true; }
            @Override public List<String> onTabComplete(CommandSender sender, Command ignored, String alias, String[] args) {
                args[0] = "changed";
                return List.of(alias, args[1]);
            }
        });
        assertEquals(List.of("atlas", "two"), command.tabComplete(new FakeSender(), "atlas", supplied));
        assertEquals("one", supplied[0]);
    }

    @Test
    void registryPreservesAliasesAndPlayerAndConsoleSendersForTabCompletion() {
        CommandRegistry registry = new CommandRegistry();
        PluginCommand command = registry.register("luckperms", new FakePlugin());
        registry.registerAlias("lp", command);
        command.setTabCompleter((sender, ignored, alias, args) ->
            List.of(alias, sender instanceof Player ? "player" : "console", args[0]));
        assertEquals(List.of("lp", "player", "p"), registry.tabComplete("LP", new FakePlayer(), new String[] { "p" }));
        assertEquals(List.of("luckperms", "console", "c"),
            registry.tabComplete("luckperms", new FakeConsole(), new String[] { "c" }));
    }

    @Test
    void commandBridgeDispatchesArgumentsAndResponse() {
        CommandRegistry registry = new CommandRegistry();
        FakePlugin plugin = new FakePlugin();
        var command = registry.register("atlas", plugin);
        AtomicInteger calls = new AtomicInteger();
        command.setExecutor((sender, ignored, label, args) -> { calls.incrementAndGet(); sender.sendMessage(label + ":" + args[0]); return true; });
        FakeSender sender = new FakeSender();
        assertTrue(registry.dispatch("ATLAS", sender, new String[] { "info" }));
        assertEquals(1, calls.get());
        assertEquals("atlas:info", sender.message);
    }

    @Test
    void commandAliasesDispatchToTheOwnedCommand() {
        CommandRegistry registry = new CommandRegistry();
        FakePlugin plugin = new FakePlugin();
        var command = registry.register("warplist", plugin);
        command.setExecutor((sender, ignored, label, args) -> true);
        registry.registerAlias("warps", command);
        assertTrue(registry.dispatch("warps", new FakeSender(), new String[0]));
        assertEquals(command, registry.get("warps"));
        assertTrue(registry.names().containsAll(Set.of("warplist", "warps")));
    }

    @Test
    void eventDispatchPropagatesCancellation() {
        Logger logger = Logger.getAnonymousLogger();
        AtlasPluginManager manager = new AtlasPluginManager(logger, new AtlasPermissionRegistry(),
            new PermissionProviderRegistry(logger), new AtlasServicesManager());
        FakePlugin plugin = new FakePlugin();
        manager.addPlugin(plugin);
        manager.registerEvents(new CancelListener(), plugin);
        BlockBreakEvent event = new BlockBreakEvent(new FakeBlock(), new FakePlayer());
        manager.callEvent(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void schedulerRunsOnDueTickAndCancelsOwnedTasks() {
        AtlasScheduler scheduler = new AtlasScheduler(Logger.getAnonymousLogger());
        FakePlugin plugin = new FakePlugin();
        AtomicInteger calls = new AtomicInteger();
        scheduler.runTaskLater(plugin, calls::incrementAndGet, 2);
        scheduler.tick();
        assertEquals(0, calls.get());
        scheduler.tick();
        assertEquals(1, calls.get());
        scheduler.runTask(plugin, calls::incrementAndGet);
        scheduler.cancelTasks(plugin);
        scheduler.tick();
        assertEquals(1, calls.get());
    }

    private static final class CancelListener implements Listener {
        @EventHandler public void onBreak(BlockBreakEvent event) { event.setCancelled(true); }
    }

    private static final class FakePlugin implements Plugin {
        private final PluginDescriptionFile description = new PluginDescriptionFile("Test", "1", "example.Test", null, null, List.of(), List.of(), List.of(), Set.of());
        @Override public String getName() { return "Test"; }
        @Override public PluginDescriptionFile getDescription() { return description; }
        @Override public Server getServer() { return null; }
        @Override public Logger getLogger() { return Logger.getAnonymousLogger(); }
        @Override public File getDataFolder() { return new File("."); }
        @Override public FileConfiguration getConfig() { return null; }
        @Override public void reloadConfig() { }
        @Override public void saveDefaultConfig() { }
        @Override public boolean isEnabled() { return true; }
        @Override public void onLoad() { }
        @Override public void onEnable() { }
        @Override public void onDisable() { }
        @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) { return false; }
        @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { return null; }
    }

    private static class FakeSender implements CommandSender {
        String message;
        @Override public String getName() { return "sender"; }
        @Override public void sendMessage(String message) { this.message = message; }
        @Override public boolean isOp() { return true; }
        @Override public void setOp(boolean value) { }
        @Override public boolean isPermissionSet(String permission) { return true; }
        @Override public boolean isPermissionSet(Permission permission) { return true; }
        @Override public boolean hasPermission(String permission) { return true; }
        @Override public boolean hasPermission(Permission permission) { return true; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return null; }
        @Override public void removeAttachment(PermissionAttachment attachment) { }
        @Override public void recalculatePermissions() { }
        @Override public Set<PermissionAttachmentInfo> getEffectivePermissions() { return Set.of(); }
    }

    private static final class FakePlayer extends FakeSender implements Player {
        @Override public UUID getUniqueId() { return UUID.fromString("00000000-0000-0000-0000-000000000001"); }
        @Override public String getDisplayName() { return getName(); }
        @Override public Location getLocation() { return null; }
        @Override public boolean teleport(Location location) { return false; }
    }

    private static final class FakeConsole extends FakeSender implements ConsoleCommandSender {
    }

    private static final class FakeBlock implements Block {
        @Override public int getX() { return 1; }
        @Override public int getY() { return 2; }
        @Override public int getZ() { return 3; }
        @Override public org.bukkit.Material getType() { return org.bukkit.Material.STONE; }
    }
}
