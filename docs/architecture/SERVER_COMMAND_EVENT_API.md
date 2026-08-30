# Server command event API

AtlasHybrid implements the Bukkit 1.19.2 command-event contract for commands
that enter Minecraft through the dedicated-server console or the vanilla RCON
endpoint. The bridge is generic and contains no LuckPerms-specific behavior.

## Public contract

`ServerCommandEvent` extends `ServerEvent`, implements `Cancellable`, and exposes
the Bukkit 1.19.2 constructor `(CommandSender, String)`, mutable command text,
the original sender, cancellation state, and its static `HandlerList`.
`RemoteServerCommandEvent` extends it, inherits mutation and cancellation, and
owns a distinct `HandlerList`, as in Spigot 1.19.2. Both events are synchronous.

The adjacent APIs were audited but intentionally deferred:

- `PlayerCommandPreprocessEvent` is the cancellable, mutable player-command
  event and is not required to implement the server/RCON pipeline.
- `PluginEnableEvent` is a non-cancellable lifecycle event and is not part of
  command execution.

## Forge bridge and execution semantics

The existing Forge `CommandEvent` supplies the pre-execution interception point.
AtlasHybrid accepts only a vanilla dedicated-server source or a real
`RconConsoleSource`; player, command-block, function, and internal bridge
sources are not reclassified as server/RCON commands.

The execution flow is:

```text
Minecraft/Forge command parse
-> ServerCommandEvent or RemoteServerCommandEvent
-> existing AtlasHybrid EventExecutor
-> cancellation, or reparse of the mutated command with the same source
-> one Minecraft command execution
```

Local console events use the stable AtlasHybrid `ConsoleCommandSender`. RCON
events use a `RemoteConsoleCommandSender` backed by the active vanilla RCON
source while delegating permissions to the console permission core. A cancelled
event cancels the Forge event. A changed command replaces the original parse
result, so the original and replacement cannot both execute. Empty, whitespace,
invalid, or `null` mutations receive no fallback; downstream parser behavior is
preserved.

Forge strips one leading slash before its `CommandEvent` when the prefixed
command entrypoint is used. Consequently, AtlasHybrid exposes the normalized
dispatcher input at this bridge rather than reconstructing information already
discarded by Forge. Correcting that edge case would require another internal
Minecraft hook and is outside this phase.

## Lifecycle and validation

Listeners use the existing priority-ordered event bus, exception diagnostics,
plugin-disable cleanup, and failed-enable rollback. No second event bus, Mixin,
NMS hook, CraftBukkit facade, or Paper API was added.

The dedicated-server proof executes a real local command mutation, a real local
cancellation, and an authenticated vanilla RCON command mutation. It records
`SERVER_COMMAND_EVENT_OK` and `REMOTE_SERVER_COMMAND_EVENT_OK` only after the
replacement commands execute exactly once with their correct sender and the
original/cancelled commands do not execute.
