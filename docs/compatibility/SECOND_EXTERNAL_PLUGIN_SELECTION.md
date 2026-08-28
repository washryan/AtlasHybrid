# Second external plugin selection

## Candidate review

The second compatibility target must exercise a small, real Bukkit surface that AtlasHybrid does not already implement. Candidate source was inspected at the pinned revisions below before any runtime API change.

| Candidate | Revision | License | Approx. classes | Bukkit surface | External dependencies | Estimated difficulty | Likely AtlasHybrid gaps |
|---|---|---|---:|---|---|---|---|
| **WarpPlugin 1.0** | `1337ac40d99279374284dbd391ce84d2857ebf38` | MIT | 5 | Commands, `Location`, player position/teleport, YAML list and location persistence, legacy `ChatColor` | Spigot API 1.17.1 compile-only | Medium | `Location`, `Player#getLocation`, `Player#teleport`, location/list config values, `saveConfig`, `ChatColor` |
| oldGamemodePlugin 1.2 | `a3e25a80a200fb679a4e141e09fed40f40860f78` | MIT | 1 | Commands, game modes, gamerules, online-player lookup, entity selectors, tab completion | None beyond Bukkit/Spigot | Medium-high | `GameMode`, `GameRule`, `World`, player game-mode methods, online players, selectors and default JavaPlugin command behavior |
| SetSpawn 2.9 | tag `v2.9`, commit `b29a4b00f870a0c4b1c54c929d51784a7ebacaa5` | MIT | 4 | Commands, config, `Location`, teleport, permissions, join event and `ChatColor` | Paper API 1.20.1 compile-only | Medium | Location/config/teleport APIs; its synchronous countdown also blocks the server thread |

### Decision

**WarpPlugin 1.0** is selected. It has a clear MIT license, a conventional Spigot dependency, no NMS, CraftBukkit, Paper API, ProtocolLib, Vault, PlaceholderAPI, LuckPerms or external service. Its 1.17.1 API usage is conceptually stable on Minecraft 1.19.2. The four commands provide a compact end-to-end test of position capture, typed YAML persistence, lookup, deletion, player messaging and real teleportation.

oldGamemodePlugin is small but expands into selectors, gamerules, player lookup and tab completion before its main behavior can be treated as complete. SetSpawn was rejected because its build depends on Paper API and the pinned implementation sleeps on the server thread during its countdown.

## Selected source and artifact policy

- Repository: <https://github.com/Agentew04/WarpPlugin>
- Version: `1.0`
- Pinned commit: `1337ac40d99279374284dbd391ce84d2857ebf38`
- License: MIT, copyright Rodrigo Appelt (2021)
- Declared API: Spigot `1.17.1-R0.1-SNAPSHOT`, `api-version: 1.17`
- Main class: `io.github.agentew04.warpplugin.Warpplugin`
- Commands: `warp`, `warplist`, `setwarp`, `delwarp`

The source and built JAR are external test inputs. They must remain outside tracked AtlasHybrid source and must not be bundled or redistributed by this repository.

## Static Bukkit API audit

Classification is against AtlasHybrid commit `b8c0e18` before second-plugin compatibility changes. Unused imports are recorded separately because they do not produce runtime bytecode references.

| Symbol used by executable bytecode | Purpose | Initial status |
|---|---|---|
| `JavaPlugin` | Plugin base class | SUPPORTED |
| `JavaPlugin#onEnable()` / `onDisable()` | Lifecycle | SUPPORTED |
| `JavaPlugin#getCommand(String)` | Obtain declared commands | SUPPORTED |
| `JavaPlugin#getConfig()` | Obtain plugin configuration | SUPPORTED |
| `JavaPlugin#saveConfig()` | Persist configuration | MISSING |
| `PluginCommand#setExecutor(CommandExecutor)` | Install command executors | SUPPORTED |
| `CommandExecutor#onCommand(...)` | Execute commands | SUPPORTED |
| `Command#getName()` | Command identity | SUPPORTED |
| `CommandSender#sendMessage(String)` | Command feedback | SUPPORTED |
| `Player` | Player sender type | SUPPORTED |
| `Player#getLocation()` | Capture a real player position | MISSING |
| `Player#teleport(Location)` | Teleport the real player | MISSING |
| `Location` | World and position value | MISSING |
| `FileConfiguration#set(String,Object)` | Store/remove warp values and lists | SUPPORTED for scalars; UNKNOWN for typed values |
| `FileConfiguration#getStringList(String)` | Read the warp-name list | MISSING |
| `FileConfiguration#isSet(String)` | Test warp existence | MISSING |
| `FileConfiguration#getLocation(String)` | Reconstruct a stored location | MISSING |
| `ChatColor.RED/GREEN/YELLOW` | Legacy colored feedback strings | MISSING |

`org.bukkit.Bukkit` and `org.bukkit.World` occur only as unused source imports at the pinned revision and therefore are not required by the generated plugin bytecode.

The expected incompatibility set is eight cohesive API areas: `saveConfig`, `Location`, player position, player teleport, string-list config, config presence checks, typed location config and legacy chat colors. No AtlasHybrid implementation is changed before the raw boot.
