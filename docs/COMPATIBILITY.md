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
| LuckPerms Forge permission bridge | 5.4.46 | Forge / tested on 1.19.2 | 🟡 PARTIAL / PASS | Optional public-API bridge makes LuckPerms TRUE/FALSE authoritative for Bukkit Player hasPermission; UNDEFINED returns to Atlas fallback; live gamemode context and lifecycle proven | Player permission-query authority only. No Bukkit service, plugin identity/depend, Bukkit PlayerAdapter, Vault or subscription parity |

See the detailed reports for [WelcomeMessage](compatibility/WELCOME_MESSAGE.md), [WarpPlugin](compatibility/WARPPLUGIN.md), [LuckPerms Bukkit](compatibility/LUCKPERMS.md), and the [LuckPerms Forge feasibility proof](compatibility/LUCKPERMS_FORGE_1_19_2.md) for scope, provenance and evidence.
