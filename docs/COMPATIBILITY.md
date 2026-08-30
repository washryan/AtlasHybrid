# Plugin compatibility

Compatibility is assigned from observed behavior on AtlasHybrid, not from a percentage estimate.

- **FULL:** every advertised behavior within the tested plugin scope worked.
- **PARTIAL:** the plugin loaded, but at least one tested advertised behavior did not work.
- **INCOMPATIBLE:** the plugin could not complete startup or its primary behavior could not run.
- **BLOCKED:** research found a required architectural dependency outside the approved compatibility scope.
- **UNTESTED:** no controlled AtlasHybrid execution has been completed.

| Plugin | Version | MC target | Status | Validated behavior | Notes |
|---|---:|---:|---|---|---|
| AtlasHybridTestPlugin | 0.1.0-alpha | 1.19.2 | ✅ FULL | Constructor-safe logger, lifecycle, commands and tab completion, real async pre-login and synchronous login allow/deny, online-player registry/lookup, player events, block-break cancellation, scheduler, permission defaults/attachments/provider/services, stable player identity and diagnostics | Internal integration fixture |
| WelcomeMessage | 1.0 | 1.18 API / tested on 1.19.2 | ✅ FULL | Discovery, config extraction/read, enable, real `PlayerJoinEvent`, configured chat message, clean stop and restart | Pinned external source; not bundled |
| WarpPlugin | 1.0 | 1.17 API / tested on 1.19.2 | ✅ FULL | Four commands and aliases, real position capture/teleport, colored feedback, YAML list/location persistence, deletion and clean restart | Pinned external source; not bundled |
| LuckPerms | 5.5.81 | Bukkit / tested on 1.19.2 | ⛔ BLOCKED | Thirteen official raw boots; Permission Core, constructor-safe `JavaPlugin`, command/configuration/player/event APIs, real login gates, local/RCON server command events, failed-enable rollback, vanilla Material, safe data-version metadata and real online-mode state now present | Raw boot #13 passes `ServerCommandEvent` and `RemoteServerCommandEvent`, then stops at `PlayerCommandPreprocessEvent` (`CORE_API`) while reflecting the platform listener; `registerPlatformListeners` and `onEnable` do not complete, known later permissible injection remains architectural, and no external JAR is bundled |

See the detailed reports for [WelcomeMessage](compatibility/WELCOME_MESSAGE.md), [WarpPlugin](compatibility/WARPPLUGIN.md), and [LuckPerms](compatibility/LUCKPERMS.md) for scope, provenance and evidence.
