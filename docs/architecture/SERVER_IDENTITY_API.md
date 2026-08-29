# Server identity and online-mode API

## Public contract

Spigot/Bukkit 1.19.2 exposes `Server#getOnlineMode()` and the static
`Bukkit#getOnlineMode()` delegate. Both mean “the server authenticates
clients”; they are not a plugin preference or an AtlasHybrid compatibility
flag. AtlasHybrid delegates the static method to its installed `Server`, so the
two access paths always observe the same runtime state.

## Source of truth

Minecraft 1.19.2 loads `online-mode` through
`DedicatedServerProperties.onlineMode`, then calls
`MinecraftServer#setUsesAuthentication(boolean)` during dedicated-server
initialization. The resulting public runtime accessor is
`MinecraftServer#usesAuthentication()`.

`ForgeServerAdapter#getOnlineMode()` reads that accessor directly. It does not
hardcode a value, parse `server.properties` a second time, cache a duplicate,
use reflection, or reach private server state. The integration proof compares
both Bukkit access paths directly with `MinecraftServer#usesAuthentication()`
and emits `SERVER_ONLINE_MODE_OK` once.

## Focused server identity audit

| Bukkit method | Current status | Classification | Runtime source / decision |
|---|---|---|---|
| `Server#getName()` | Supported | `SUPPORTED` | Stable AtlasHybrid implementation name |
| `Server#getVersion()` | Supported | `SUPPORTED` | AtlasHybrid, Minecraft and Forge versions already composed |
| `Server#getBukkitVersion()` | Supported | `SUPPORTED` | Target compatibility version `1.19.2-R0.1-ATLASHYBRID` |
| `Server#getOnlineMode()` | Supported in Phase 9.10 | `CORE_API` | Live `MinecraftServer#usesAuthentication()` state |
| `Server#getPort()` | Not exposed | `TRIVIAL` | Public `MinecraftServer#getPort()` exists; deferred because no current blocker needs it |
| `Server#getIp()` | Not exposed | `TRIVIAL` | Public `MinecraftServer#getLocalIp()` exists; deferred because no current blocker needs it |
| `Server#getMaxPlayers()` | Not exposed | `TRIVIAL` | Public `MinecraftServer#getMaxPlayers()` exists; deferred because no current blocker needs it |

No additional `Server` surface is added merely because adjacent values are
easy to bridge. Each deferred method remains subject to a real-plugin use audit
and focused tests.
