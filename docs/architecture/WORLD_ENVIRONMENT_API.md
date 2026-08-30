# World environment and player world context

Phase 9.18 adds the exact Bukkit 1.19.2 `World.Environment` contract and maps
each stable Forge world adapter from its real dimension key. It does not add
the wider Bukkit World API or synthetic dimension behavior.

## Public contract

The Spigot 1.19.2 declaration order and legacy identifiers are:

| Constant | Legacy ID | Meaning |
|---|---:|---|
| `NORMAL` | `0` | vanilla Overworld |
| `NETHER` | `-1` | vanilla Nether |
| `THE_END` | `1` | vanilla End |
| `CUSTOM` | `-999` | any non-vanilla dimension key |

`getId()` exposes the deprecated identifier. `getEnvironment(int)` returns the
matching constant or `null` for an unknown identifier. `World#getEnvironment()`
is part of the public interface and never returns a guessed normal environment.

## Forge mapping and identity

`ForgeWorldEnvironmentMapper` consumes the `ResourceLocation` from the real
`ServerLevel#dimension()` key. The three exact vanilla keys map to their Bukkit
counterparts; every other Forge/modded key maps to `CUSTOM`. Folder names,
display names and dimension-type heuristics are not used.

`ForgeServerAdapter` keeps one `ForgeWorldAdapter` per live `ServerLevel` using
identity keys. The adapter snapshots only immutable context data: its Bukkit
name and environment. Repeated server, player, entity and location lookups
therefore return the same logical adapter and are safe for asynchronous reads.
`Player#getWorld()` reads the player's current level before resolving that
stable adapter, so a real dimension transition cannot leave the player pinned
to the old world. Shutdown clears the adapter registry.

The existing name policy remains unchanged: the world-data level name is used
for the Overworld, the standard `_nether`/`_the_end` suffixes are used for the
two vanilla companion worlds, and a modded dimension uses its full namespaced
dimension key. `World#getUID()` was audited but is not used by the current
LuckPerms calculator and remains deferred; no unstable UUID is generated.

## LuckPerms BukkitPlayerCalculator audit

The complete context path at raw boot #18 is:

| Status | API/use |
|---|---|
| Supported | `GameMode.class`, `GameMode.values()`, `Player#getGameMode()` |
| Supported | `Player#getWorld()`, stable World identity, `World#getName()` |
| Supported | `World.Environment`, `Environment.values()`, `World#getEnvironment()` |
| Supported | `PlayerJoinEvent` invalidation |
| Missing | `Server#getWorlds()` for potential world contexts |
| Missing | `PlayerChangedWorldEvent` for world/dimension invalidation |
| Missing | `PlayerGameModeChangeEvent` for game-mode invalidation |
| Deferred/unused | `World#getUID()`, Location, locale, address, client data, inventory, metadata and persistent data |

For a calculated subject, LuckPerms emits lower-case game mode, rewritten world
name and a dimension-type value. Vanilla dimension names are `overworld`,
`the_nether` and `the_end`; `CUSTOM` falls back to `custom`. Potential-context
estimation additionally enumerates `Server#getWorlds()`. Listener registration
will also reflect both missing change-event types. Raw boot #18 determines
which of those symbols is linked first; none is implemented in cascade here.

## Proof scope

Unit tests verify enum order, all legacy IDs, invalid lookup, all three vanilla
dimension keys, a modded key mapping to `CUSTOM`, and null rejection. The Forge
integration proof verifies real Overworld, Nether and End adapters, repeated
adapter identity, and player/entity/location world coherence, then emits
`WORLD_ENVIRONMENT_OK` exactly once. It deliberately does not teleport the
synthetic integration player between dimensions because that would add a
fragile portal/connection test unrelated to this API boundary.
