# Player command preprocess event API

AtlasHybrid implements the Bukkit 1.19.2 `PlayerCommandPreprocessEvent`
contract generically for commands executed with a Minecraft player source. It
does not contain LuckPerms-specific logic.

## Public contract

The event extends `PlayerEvent`, implements `Cancellable`, and is synchronous.
Its target constructors are `(Player, String)` and
`(Player, String, Set<Player>)`. It exposes the player, mutable message,
deprecated recipients set, cancellation state, and a static `HandlerList`.
`setMessage` rejects `null` and empty strings; `setPlayer` rejects `null`.

The message includes the command prefix, for example `/atlas info`. Bukkit's
contract says listeners must preserve whether that first character is present.
AtlasHybrid follows the CraftBukkit execution shape and removes exactly the
first character after listeners finish. Removing or replacing the slash can
therefore produce the same explicitly unspecified behavior warned about by the
public API. No normalization or fallback is invented for `/`, whitespace, or
invalid commands.

## Minecraft and Forge bridge

Minecraft 1.19.2 receives `ServerboundChatCommandPacket` without the leading
slash, validates it, schedules `performChatCommand` on the server executor,
parses it with Brigadier, and calls `Commands#performCommand`. Forge publishes
its cancellable `CommandEvent` immediately before dispatcher execution.

AtlasHybrid handles a Forge command event whose source entity is a
`ServerPlayer` as follows:

```text
player command source
-> PlayerCommandPreprocessEvent with "/" + dispatcher input
-> existing priority-ordered EventExecutor
-> cancel, or remove the first character and reparse once
-> one Brigadier execution
```

This point covers Bukkit plugin commands and aliases registered in Brigadier as
well as vanilla and Forge commands. Tab completion remains on the suggestion
path and does not dispatch the event. Replacing the current Forge event's parse
result does not publish another Forge event, so mutation cannot recurse or
execute both original and replacement commands.

The sender is the stable AtlasHybrid session adapter and retains Permission Core
state. If a listener replaces the event player with another AtlasHybrid-managed
player, reparsing uses that player's command source. A foreign player
implementation is rejected rather than converted to a fake sender.

## PluginEnableEvent audit

Spigot 1.19.2 `PluginEnableEvent` extends `PluginEvent`, has constructor
`(Plugin)`, returns that plugin through `getPlugin()`, owns a static
`HandlerList`, is non-cancellable, and is dispatched synchronously immediately
after a plugin has successfully enabled. Correct implementation will require a
lifecycle dispatch in `PluginManager`; it is intentionally not implemented in
Phase 9.14.

LuckPerms' normal-priority handler checks whether the enabled plugin is named
`Vault` or declares `Vault` in `PluginDescriptionFile#getProvides`, then calls
its Vault hook. It does not require CraftBukkit or NMS for the event contract.

## Validation

The dedicated-server proof uses the production Brigadier dispatcher with an
online session-backed player. One command is mutated and only its replacement
executes; a second command is cancelled and never reaches its executor. The
proof emits `PLAYER_COMMAND_PREPROCESS_OK` and
`PLAYER_COMMAND_PREPROCESS_CANCEL_OK` exactly once. No Mixin, NMS hook, Paper
API, CraftBukkit facade, instrumentation, or second event bus was added.
