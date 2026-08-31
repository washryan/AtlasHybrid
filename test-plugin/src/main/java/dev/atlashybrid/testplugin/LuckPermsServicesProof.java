package dev.atlashybrid.testplugin;

import java.util.Collection;
import java.util.Optional;
import java.util.logging.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

final class LuckPermsServicesProof {
    private LuckPermsServicesProof() { }

    static void verifyRegistration(Server server, Logger logger) {
        RegisteredServiceProvider<LuckPerms> registration =
            server.getServicesManager().getRegistration(LuckPerms.class);
        LuckPerms service = server.getServicesManager().load(LuckPerms.class);
        Collection<RegisteredServiceProvider<LuckPerms>> registrations =
            server.getServicesManager().getRegistrations(LuckPerms.class);
        LuckPerms provider = LuckPermsProvider.get();
        if (registration == null || service == null || service != provider
            || registration.getProvider() != service || registrations.size() != 1
            || registration.getPriority() != ServicePriority.Normal
            || !registration.getPlugin().getName().equals("AtlasHybridCompatibility")
            || registration.getPlugin().getName().equals("LuckPerms")) {
            throw new IllegalStateException("LuckPerms Bukkit service registration/identity proof failed");
        }
        String version = service.getPluginMetadata().getVersion();
        if (!version.equals("5.4.46")) {
            throw new IllegalStateException("Unexpected LuckPerms Forge version: " + version);
        }
        logger.info("[AtlasHybrid LuckPerms] LUCKPERMS_BUKKIT_SERVICE_IDENTITY_PASS"
            + " version=" + version
            + " apiClassLoader=" + String.valueOf(LuckPerms.class.getClassLoader())
            + " implementationClass=" + service.getClass().getName()
            + " implementationClassLoader=" + String.valueOf(service.getClass().getClassLoader()));
    }

    static void verifyPlayerQuery(Server server, Player player, String node, Logger logger) {
        LuckPerms service = server.getServicesManager().load(LuckPerms.class);
        if (service == null || service != LuckPermsProvider.get()) {
            throw new IllegalStateException("LuckPerms Bukkit service disappeared or changed identity");
        }
        User user = service.getUserManager().getUser(player.getUniqueId());
        if (user == null || !user.getUniqueId().equals(player.getUniqueId())) {
            throw new IllegalStateException("LuckPerms online User unavailable through ServicesManager API");
        }
        Optional<QueryOptions> contextual = service.getContextManager().getQueryOptions(user);
        if (contextual.isEmpty()) {
            throw new IllegalStateException("LuckPerms contextual QueryOptions unavailable for online User");
        }
        Tristate apiResult = user.getCachedData().getPermissionData(contextual.get()).checkPermission(node);
        boolean bukkitResult = player.hasPermission(node);
        if (apiResult == Tristate.TRUE && !bukkitResult
            || apiResult == Tristate.FALSE && bukkitResult) {
            throw new IllegalStateException("LuckPerms API and Bukkit permission results diverged: "
                + apiResult + " / " + bukkitResult);
        }
        logger.info("[AtlasHybrid LuckPerms] LUCKPERMS_BUKKIT_SERVICE_QUERY_PASS"
            + " user=" + user.getUsername()
            + " primaryGroup=" + user.getPrimaryGroup()
            + " node=" + node
            + " api=" + apiResult
            + " bukkit=" + bukkitResult
            + " contexts=" + contextual.get().context().toFlattenedMap()
            + (apiResult == Tristate.UNDEFINED ? " fallback=Atlas" : ""));
    }
}
