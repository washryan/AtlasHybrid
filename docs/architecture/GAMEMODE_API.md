# GameMode and player context foundation

Phase 9.17 added the Bukkit 1.19.2 game-mode enum and a thread-safe view of a
player's real Minecraft game type. Phase 9.20 completes the mutation contract
with `Player#setGameMode(GameMode)` and the cancellable real-transition event.

## Public contract

The Spigot 1.19.2 enum declaration order is `CREATIVE`, `SURVIVAL`,
`ADVENTURE`, `SPECTATOR`. Its deprecated numeric values are respectively
`1`, `0`, `2`, `3`; therefore enum ordinals are not game-mode IDs.
`getByValue(int)` returns the matching constant or `null` for an unknown value.

The official `HumanEntity` contract declares both `getGameMode()` and
`setGameMode(GameMode)`. The setter now delegates to the real Minecraft player
on the server thread and uses the same event pipeline as vanilla, Forge and
internal changes. It never mutates only the adapter.

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
`PlayerChangeGameModeEvent` priority, after ordinary Forge handlers have had an
opportunity to cancel or replace the target mode. The Bukkit event runs while
the snapshot still contains the old mode. Successful transitions update it
exactly once; cancelled transitions leave it unchanged. No Mixin, polling loop
or parallel synthetic state is used.

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
effects, metadata or persistent data. AtlasHybrid now supplies UUID, op, join,
world identity/name/environment, game mode and both context-change events. Raw
boot #20 confirms that the complete calculator and its listeners register
successfully. The remaining `Server#getWorlds()` surface is used when
estimating potential contexts, but it was not the next bootstrap boundary.

## Proof scope

Unit tests verify enum order, legacy values, invalid lookup behavior, both
mapping directions, event construction, handler list and cancellation. The
integration proof verifies one allowed and one cancelled real transition,
same-mode duplicate suppression and the stable adapter. See
[`PLAYER_GAMEMODE_CHANGE_EVENT_API.md`](PLAYER_GAMEMODE_CHANGE_EVENT_API.md).
