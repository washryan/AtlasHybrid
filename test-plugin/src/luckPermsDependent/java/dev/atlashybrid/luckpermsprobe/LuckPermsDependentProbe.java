package dev.atlashybrid.luckpermsprobe;

import java.util.Arrays;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuckPermsDependentProbe extends JavaPlugin {
    @Override
    public void onEnable() {
        LuckPerms luckPerms = getServer().getServicesManager().load(LuckPerms.class);
        if (luckPerms == null || luckPerms.getUserManager() == null) {
            throw new IllegalStateException("LuckPerms public service was unavailable during dependent enable");
        }
        if (getServer().getPluginManager().getPlugin("LuckPerms") != null
            || getServer().getPluginManager().isPluginEnabled("LuckPerms")
            || Arrays.stream(getServer().getPluginManager().getPlugins())
                .anyMatch(plugin -> plugin.getName().equalsIgnoreCase("LuckPerms"))) {
            throw new IllegalStateException("Virtual dependency leaked into Bukkit plugin identity APIs");
        }
        getLogger().info("[AtlasHybrid LuckPerms] LUCKPERMS_DEPENDENT_PLUGIN_ENABLE_PASS"
            + " version=" + luckPerms.getPluginMetadata().getVersion());
    }
}
