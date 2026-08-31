# Plugin compatibility

Compatibility is assigned from observed behavior on AtlasHybrid, not from a percentage estimate.

- **FULL:** every advertised behavior within the tested plugin scope worked.
- **PARTIAL:** the plugin loaded, but at least one tested advertised behavior did not work.
- **INCOMPATIBLE:** the plugin could not complete startup or its primary behavior could not run.
- **BLOCKED:** research found a required architectural dependency outside the approved compatibility scope.
- **UNTESTED:** no controlled AtlasHybrid execution has been completed.

| Plugin | Version | MC target | Status | Validated behavior | Notes |
|---|---:|---:|---|---|---|
| AtlasHybridTestPlugin | 0.1.0-alpha | 1.19.2 | ✅ FULL | Constructor-safe logger, lifecycle, commands and tab completion, real async pre-login and synchronous login allow/deny, online-player registry/lookup, real dimension-change event/context, block-break cancellation, scheduler, permission defaults/attachments/provider/services, stable player identity and diagnostics | Internal integration fixture |
| WelcomeMessage | 1.0 | 1.18 API / tested on 1.19.2 | ✅ FULL | Discovery, config extraction/read, enable, real `PlayerJoinEvent`, configured chat message, clean stop and restart | Pinned external source; not bundled |
| WarpPlugin | 1.0 | 1.17 API / tested on 1.19.2 | ✅ FULL | Four commands and aliases, real position capture/teleport, colored feedback, YAML list/location persistence, deletion and clean restart | Pinned external source; not bundled |
| LuckPerms | 5.5.81 | Bukkit / tested on 1.19.2 | ⛔ BLOCKED — ARCHITECTURAL DECISION | Twenty official raw boots; context manager, listeners, commands and H2 complete before the platform-hook boundary | Phase 9.22 found no supported injector strategy, disable flag, pre-hook API/service or early extension point in the original Bukkit artifact. The public adapter remains the target only after upstream bootstrap cooperation; no prototype, fake CraftBukkit shape, patch or external JAR was added |
| LuckPerms Forge bridges | 5.4.46 | Forge / tested on 1.19.2 | 🟡 PARTIAL / PASS | LuckPerms TRUE/FALSE authority for Bukkit Player hasPermission; same API instance through ServicesManager; explicit virtual resolution of `depend: [LuckPerms]`; contexts, reload and lifecycle proven | No Bukkit plugin identity/getPlugin, Bukkit PlayerAdapter, Vault or subscription parity |
| ExtraContexts | 2.0-SNAPSHOT (Jenkins 21) | Bukkit / tested on 1.19.2 | 🟡 PARTIAL | Original hard dependency resolved; public LuckPerms service loaded; context calculator registered/reloaded; clean stop | Live player-side calculator invocation not yet proven |
| LPC | 3.7.2 | Bukkit / tested on 1.19.2 | ⛔ BLOCKED | Original hard dependency and public LuckPerms service resolved | First blocker is missing generic `AsyncPlayerChatEvent`; Bukkit PlayerAdapter path was not reached |
| LuckPermsGUI | 4.6 | Bukkit / tested on 1.19.2 | ⛔ BLOCKED | Original hard dependency resolved | First blocker is missing generic configuration `addDefault` |
| TAB | 3.1.5 | Bukkit / tested on 1.19.2 | ❌ INCOMPATIBLE | Raw boot completed through plugin load only | CraftBukkit package parsing/NMS fails before optional LuckPerms integration; also uses real-plugin identity for feature detection |
| Vault | 1.7.3-b131 | Bukkit / tested on 1.19.2 | ⛔ BLOCKED | Original plugin discovered and loaded at startup | First blocker is the same generic configuration `addDefault`; Vault provider behavior was not reached |

See the detailed reports for [WelcomeMessage](compatibility/WELCOME_MESSAGE.md), [WarpPlugin](compatibility/WARPPLUGIN.md), [LuckPerms Bukkit](compatibility/LUCKPERMS.md), the [LuckPerms Forge feasibility proof](compatibility/LUCKPERMS_FORGE_1_19_2.md), and the [real-world LuckPerms matrix](compatibility/LUCKPERMS_REAL_WORLD_MATRIX.md) for scope, provenance and evidence.

## Playable development milestone

AtlasHybrid `0.1.0-alpha` has reached a playable development milestone with a
persistent local Minecraft `1.19.2` / Forge `43.5.0` server and the unmodified
LuckPerms Forge `5.4.46` artifact. This is a development checkpoint, not an
official release or a claim of complete Bukkit compatibility.

The **PARTIAL / PASS** LuckPerms Forge scope includes `Player#hasPermission`
TRUE/FALSE/UNDEFINED handling, live contexts, login/logout, the public LuckPerms
API through Bukkit ServicesManager, virtual `depend: [LuckPerms]` resolution,
`reloadconfig`, groups, permission persistence, web-editor session generation
and clean shutdown.

It does not provide `PluginManager#getPlugin("LuckPerms")`, a Bukkit LuckPerms
Plugin identity, Bukkit `PlayerAdapter<Player>`, Vault, TAB's CraftBukkit/NMS
requirements, or the missing generic Bukkit APIs recorded in the real-world
matrix. See the [manual playtest guide](playtest/LUCKPERMS_MANUAL_PLAYTEST.md)
for the local server workflow and verified commands.
