# GameMode and player context foundation

Phase 9.17 adds the Bukkit 1.19.2 game-mode enum and a thread-safe view of a
player's real Minecraft game type. It does not implement the broader player
context API.

## Public contract

The Spigot 1.19.2 enum declaration order is `CREATIVE`, `SURVIVAL`,
`ADVENTURE`, `SPECTATOR`. Its deprecated numeric values are respectively
`1`, `0`, `2`, `3`; therefore enum ordinals are not game-mode IDs.
`getByValue(int)` returns the matching constant or `null` for an unknown value.

The official `HumanEntity` contract declares both `getGameMode()` and
`setGameMode(GameMode)`. LuckPerms only calls the getter. Phase 9.17 exposes the
getter through the existing HumanEntity/Player hierarchy and deliberately
defers the setter rather than publishing an adapter-only mutation.

## Forge mapping and thread model

`ForgeGameModeMapper` uses an exhaustive switch between Minecraft `GameType`
and Bukkit `GameMode`; it never compares ordinals. Every connecting adapter is
initialized from `ServerPlayer.gameMode.getGameModeForPlayer()`.

LuckPerms' `QueryOptionsCache` recalculates on the calling thread after its
short expiry, so context calculation is not guaranteed to run on the Minecraft
server thread. `ServerPlayerGameMode#gameModeForPlayer` is neither volatile nor
an API documented for off-thread reads. `ForgePlayerAdapter#getGameMode`
therefore returns a volatile snapshot. The snapshot is initialized from the
real player and updated on the server thread at Forge's LOWEST
`PlayerChangeGameModeEvent` priority, after ordinary handlers have had an
opportunity to cancel or replace the target mode. No Mixin, polling loop or
parallel synthetic state is used.

Connecting players already have a real `ServerPlayerGameMode`, so their initial
snapshot is valid. Promotion reuses the same adapter. Disconnect removes and
closes the adapter through `PlayerSessionRegistry`; an externally retained
Player reference can read its last simple snapshot without touching mutable
Minecraft game-mode state.

## LuckPerms BukkitPlayerCalculator audit

The exact public API used by the calculator is:

| Purpose | API |
|---|---|
| Game-mode context | `GameMode.class`, `GameMode.values()`, `Player#getGameMode()` |
| World context | `Player#getWorld()`, `World#getName()` |
| Dimension context | `World#getEnvironment()`, `World.Environment` constants/values |
| Potential worlds | `Server#getWorlds()` |
| Invalidations | `PlayerJoinEvent`, `PlayerChangedWorldEvent`, `PlayerGameModeChangeEvent` |
| Manager identity/options | `Player#getUniqueId()`, `Player#isOp()`, permissible lookup |

It does not read location, locale, address, client brand, inventory, potion
effects, metadata or persistent data. AtlasHybrid already supplies UUID, op,
join, world identity/name and game mode. `World.Environment`,
`World#getEnvironment`, `Server#getWorlds`, `PlayerChangedWorldEvent` and
`PlayerGameModeChangeEvent` remain outside this phase. The expected next
linkage boundary is therefore a world/dimension or change-event symbol, but raw
boot #17 determines the actual order.

## Proof scope

Unit tests verify enum order, legacy values, invalid lookup behavior, all four
explicit Minecraft-to-Bukkit mappings and mapper null rejection. The
integration proof changes a real server player's Minecraft mode through all
four `GameType` values, verifies the same stable Bukkit adapter after each
change, restores Survival, and emits `GAMEMODE_API_OK` exactly once.
