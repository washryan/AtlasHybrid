# WarpPlugin 1.0 compatibility report

## Result

**FULL** for the complete advertised behavior of the pinned plugin source. AtlasHybrid discovered, loaded and enabled the plugin; all four commands and declared aliases were registered; a real player created, listed, used and deleted a warp; state survived a clean restart; and shutdown completed without plugin errors.

This result covers only WarpPlugin 1.0 at the pinned revision and does not imply general Spigot compatibility.

## Provenance

- **Plugin:** WarpPlugin
- **Repository:** <https://github.com/Agentew04/WarpPlugin>
- **Version:** `1.0`
- **Pinned commit:** `1337ac40d99279374284dbd391ce84d2857ebf38`
- **License:** MIT, copyright Rodrigo Appelt (2021)
- **Declared target:** Spigot API `1.17.1-R0.1-SNAPSHOT`, `api-version: 1.17`
- **Test runtime:** AtlasHybrid `0.1.0-alpha`, Minecraft `1.19.2`, Forge `43.5.0`, Java 17
- **Artifact SHA-256:** `79E64D1FB7BC4B4E7AF84D06A62D8D489FD5B71A1277ACC35D4DBF5E14D3977C`

The artifact was compiled from the pinned, unmodified Java source. Maven was unavailable locally, so the build used JDK 17 and the official Spigot 1.19.2 API compile classpath; the standard Maven resource-filter result was reproduced by resolving `${project.version}` to `1.0` in the packaged `plugin.yml`. No plugin source was edited. The source and JAR remain external ignored test inputs: **tested externally; not bundled or redistributed**.

## Initial raw boot

Before any second-plugin API work, plugin discovery, load and enable succeeded. `warplist` was not exposed through Forge's command dispatcher, and shutdown preserved the original failure:

```text
java.lang.NoSuchMethodError: 'void io.github.agentew04.warpplugin.Warpplugin.saveConfig()'
```

The static audit also identified missing location, player-position/teleport, typed configuration and legacy color APIs. No CraftBukkit, NMS, Paper, remapping, Mixins or broad Minecraft patches were required.

## Minimal APIs implemented

- `JavaPlugin#saveConfig()`
- `Location` and minimal `World`
- `Player#getLocation()` and `Player#teleport(Location)` backed by the real Forge player
- `Server#getWorld(String)` and `Bukkit#getWorld(String)`
- `FileConfiguration#isSet`, `getStringList` and `getLocation`
- deterministic YAML read/write for string lists and Bukkit-style locations
- `ChatColor.RED`, `GREEN` and `YELLOW`
- plugin command aliases and registration in the live Forge dispatcher
- structured linkage diagnostics that identify the plugin and missing symbol while preserving the original exception

The implementation is intentionally narrow. Location persistence covers the Bukkit representation used by this plugin, and teleportation accepts AtlasHybrid's Forge-backed worlds; it is not a complete Bukkit world or teleport API.

## Automated tests added

- YAML string-list and location round trip
- `ChatColor` codes
- `JavaPlugin#saveConfig()` persistence
- command alias dispatch
- structured linkage-failure diagnostic
- the internal integration plugin now verifies the real location/teleport bridge

The full unit suite passes `14/14` (the previous `9/9` plus five focused tests).

## Manual end-to-end validation

With only WarpPlugin enabled in the isolated `run-compat/` profile, a real player performed:

1. `/setwarp atlasproof`
2. moved more than ten blocks
3. `/warps`
4. `/warp atlasproof`
5. `/delwarp atlasproof`
6. `/warp atlasproof` again

Observed results were, in order: successful creation, exactly one listed warp, successful real teleport to the saved position, successful deletion, then the expected red “warp does not exist” response. Green, yellow and red legacy chat colors rendered correctly. The vanilla server emitted a “moved too quickly” warning for the deliberate long teleport; the client completed the teleport successfully.

Console validation also covered the primary `warplist` command, its `warps` alias, and rejection of player-only `setwarp` from the console. Lifecycle, discovery and enable markers occurred once per boot.

After clean shutdown, `plugins/WarpPlugin/config.yml` contained an empty `warplist`. A fresh server boot loaded `WarpPlugin v1.0` once, reported one discovered/loaded plugin and zero unsupported plugins, and `warps` returned “Não existe nenhum warp!”. The second shutdown saved all dimensions and Gradle completed successfully without `ERROR`, `FATAL`, linkage failure or duplicate lifecycle calls.

## Known limitations

- The compatibility grade applies only to the pinned WarpPlugin behavior and its one-word warp names.
- AtlasHybrid does not claim the rest of the Bukkit, Spigot or Paper APIs.
- External plugin source and artifacts are deliberately excluded from the repository.

## Final classification

**FULL** — every command, alias, persistence path, message color and teleport behavior advertised by the pinned WarpPlugin 1.0 source was validated on the stated test runtime.

## Phase 9.2 regression

After the Permission Core changed player and command-sender composition, the
pinned external artifact completed two additional clean boots. A Forge-backed
test player exercised `addwarp`, `warps`, `warp` and `remwarp`; each dispatch
returned success, real teleport coordinates matched the saved location, the
deleted warp produced the expected missing path, and `config.yml` persisted an
empty `warplist` before restart. The second boot repeated the command/teleport
proof successfully. No WarpPlugin `ERROR`/`FATAL` or unsupported diagnostic was
emitted. Status remains **FULL**.
