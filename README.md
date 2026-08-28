# AtlasHybrid

Minecraft 1.19.2 · Forge 43.x

> Experimental Forge + Bukkit compatibility runtime.

AtlasHybrid is an independent open-source experiment that runs a deliberately
small Bukkit-compatible plugin surface inside an official Forge dedicated server.
It is not a fork of Mohist, Arclight, Magma, CraftBukkit, Spigot, or Paper.

## Status

Version `0.1.0-alpha` is a narrow proof of architecture. The target runtime is
Minecraft `1.19.2` with the recommended Forge `43.5.0` on Java 17.

No claim of complete Bukkit, Spigot, or Paper compatibility is made.

## Goal

The first proof loads native Forge mods normally and, in the same server process,
loads a test plugin from `plugins/` with lifecycle callbacks, commands, three
events, synchronous delayed tasks, basic configuration, and explicit diagnostics.

## Installation

1. Install a clean Forge `1.19.2-43.5.0` dedicated server using the official Forge installer.
2. Build AtlasHybrid with `./gradlew proofArtifacts` (`gradlew.bat proofArtifacts` on Windows).
3. Copy `platform-forge-1.19.2/build/libs/atlashybrid-1.19.2-0.1.0-alpha.jar` to the server's `mods/` directory.
4. Copy `test-plugin/build/libs/AtlasHybridTestPlugin-0.1.0-alpha.jar` to `plugins/` when running the acceptance plugin.
5. Accept Mojang's EULA in the isolated server directory and start Forge normally.

The `AtlasHybridTestMod` is an automated proof harness, not a normal server or
client requirement. For a continuous local server without the proof harness,
run `run-manual-server.bat`; see
[`docs/MANUAL_TEST_0.1.0-alpha.md`](docs/MANUAL_TEST_0.1.0-alpha.md).

Never point the development `run/` directory at a personal or production server.

## Supported APIs

- `Plugin` and `JavaPlugin` lifecycle: `onLoad`, `onEnable`, `onDisable`
- basic plugin logger and deterministic YAML configuration for scalars, string lists and locations
- `Bukkit`, minimal `Server`, `PluginManager`
- `Command`, `PluginCommand`, `CommandSender`
- `Player`, `World`, `Location`, `ChatColor`, `Listener`, `Event`, `HandlerList`
- real player position reads and same-server/dimension teleportation
- `PlayerJoinEvent`, `PlayerQuitEvent`, `BlockBreakEvent`
- synchronous `runTask` and `runTaskLater`

## Known limitations

- Only the API required by the validated compatibility targets exists.
- No CraftBukkit/NMS, Spigot API, Paper API, plugin remapping, async scheduler, hot reload, or reload command.
- Plugin classloading is stricter than legacy Bukkit and only exposes declared dependencies.
- The first command bridge reserves `/atlas` and `/atlas info`.

## Compatibility

Compatibility is evidence-based: discovery alone is never treated as support.
The current matrix includes the internal acceptance fixture and pinned external
open-source plugins tested without source modification. External plugin
artifacts are not bundled. See the [compatibility matrix](docs/COMPATIBILITY.md)
and the detailed compatibility reports.

- AtlasHybridTestPlugin `0.1.0-alpha`: **FULL**
- WelcomeMessage `1.0`: **FULL**
- WarpPlugin `1.0`: **FULL**
- LuckPerms `5.5.81`: **BLOCKED** (research/raw boot only; not supported)

Unsupported linkage is reported under `[AtlasHybrid Compatibility]` with the
plugin, missing symbol, `NOT_IMPLEMENTED` status and runtime version before the
original exception is preserved in the log.

## Building

Requirements: JDK 17 and internet access for the first dependency resolution.

```bash
./gradlew clean test proofArtifacts
```

Artifacts remain separate because the Forge runtime mod, the Bukkit test plugin,
and the native Forge test mod have different loaders and classpaths.

Development runs use Forge's level-aware console highlighting: errors are red,
warnings yellow, informational messages green, and lower diagnostic levels use
distinct colors. The runtime also prints an original AtlasHybrid startup banner.
File logs remain plain text.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) and the
[architecture audit](docs/ARCHITECTURE_RESEARCH.md) before proposing compatibility
work. Each new API or patch needs a focused behavior test and provenance record.

## License

AtlasHybrid is licensed under [GPL-3.0-only](LICENSE). See the
[license notes](docs/LICENSE_NOTES.md) and
[third-party inventory](docs/THIRD_PARTY.md). Third-party components retain
their own terms.
