# Player game-mode change event API

Phase 9.20 adds the Bukkit 1.19.2 `PlayerGameModeChangeEvent` contract and a
single real transition pipeline shared by vanilla, Forge and
`Player#setGameMode(GameMode)` callers.

## Public contract

The event extends `PlayerEvent`, implements `Cancellable`, and exposes the
requested destination through `getNewGameMode()`. It has the standard static
`HandlerList`, is synchronous, and is dispatched only when the requested mode
differs from the current mode.

During listener execution, `Player#getGameMode()` still reports the old mode
and `getNewGameMode()` reports the requested mode. Cancelling the event keeps
both the real `ServerPlayerGameMode` and the Bukkit snapshot at the old value.
An allowed transition updates the snapshot exactly once and Minecraft then
applies the real `GameType` change.

## Forge bridge

Forge 43.5.0 posts the cancellable `PlayerChangeGameModeEvent` from
`ForgeHooks#onChangeGameType` before `ServerPlayerGameMode` mutates its state.
AtlasHybrid observes that public event at `LOWEST`, after ordinary Forge
handlers can cancel or replace the target. It dispatches one Bukkit event for
an online `ServerPlayer`; Bukkit cancellation is propagated back to Forge.

No Mixin, NMS patch, CraftBukkit facade or duplicate setter path is used.
`ForgePlayerAdapter#setGameMode` requires the server thread and delegates to
the real `ServerPlayer#setGameMode`, which enters the same Forge/Bukkit
pipeline used by commands and internal server changes.

## State and duplicate guarantees

The existing session `Player` adapter is reused, preserving UUID, permission
attachments, world and lifecycle identity. A successful transition updates
its volatile game-mode snapshot once. A cancelled or same-mode request does
not update it and does not emit a second Bukkit event.

Listener ordering and `ignoreCancelled` behavior are provided by the generic
AtlasHybrid event executor for `LOWEST`, `LOW`, `NORMAL`, `HIGH`, `HIGHEST`
and `MONITOR`. Listener exceptions retain their stack trace and follow the
existing event policy; they do not introduce an alternate state mutation.

## Integration proof

The proof performs a real Survival-to-Creative transition and verifies the
Minecraft `GameType`, stable Bukkit adapter and attachment before emitting
`PLAYER_GAMEMODE_CHANGE_OK`. It then cancels Creative-to-Survival, verifies
that both states remain Creative, and emits
`PLAYER_GAMEMODE_CHANGE_CANCEL_OK`. A same-mode request proves that no
duplicate event is dispatched.
