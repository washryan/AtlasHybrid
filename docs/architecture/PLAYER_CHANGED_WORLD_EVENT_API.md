# Player changed world event and context refresh

Phase 9.19 implements the Bukkit 1.19.2
`org.bukkit.event.player.PlayerChangedWorldEvent` contract and dispatches it from
Forge's public `PlayerEvent.PlayerChangedDimensionEvent`. The bridge is generic;
it contains no LuckPerms-specific path.

## Public contract

The event extends `PlayerEvent`, has constructor `(Player, World)`, exposes the
previous world through `getFrom()`, and owns the usual static `HandlerList`.
It is synchronous by default and does not implement `Cancellable`: the world
change has already happened and the event is observational.

During listener execution:

- `getFrom()` is the stable adapter for the source dimension;
- `getPlayer().getWorld()` is the stable adapter for the destination;
- player identity, UUID, entity identity, session permission state and
  attachments are unchanged;
- location world, world environment and game mode already describe the
  destination context.

## Forge dispatch point

Forge 43.5.0 posts `PlayerChangedDimensionEvent` after successful placement in
the destination `ServerLevel`. Both the vanilla dimension-change path and
cross-level `ServerPlayer#teleportTo` use this public event. Same-level
teleports do not post it. AtlasHybrid accepts only an already-online player,
resolves source/destination through the existing world registry, verifies the
live player adapter points at the destination, and calls the existing Bukkit
event dispatcher inline on the server thread.

One Forge transition event produces one Bukkit transition event. There is no
secondary portal, teleport or packet hook, so a transition cannot be duplicated
by overlapping bridges. Listener priority, exception isolation, plugin
ownership and unregister cleanup remain properties of the existing
`AtlasPluginManager`/`RegisteredListener` path.

## Dimension and respawn policy

Overworld, Nether and End transitions follow their real dimension keys. Modded
to vanilla, vanilla to modded and modded to modded transitions follow the same
rule, provided both live `ServerLevel` instances are registered; modded worlds
retain the `CUSTOM` environment policy.

Forge's normal changed-dimension event is not emitted for the special End
credits return branch or for death respawn. Minecraft's respawn path constructs
a replacement `ServerPlayer` and Forge publishes `PlayerRespawnEvent` instead.
AtlasHybrid therefore does not fabricate `PlayerChangedWorldEvent` for those
paths in Phase 9.19. Correct respawn session rebinding and
`PlayerRespawnEvent` require a dedicated phase. No Mixin, NMS hook or
CraftBukkit facade was added.

## Verification

API tests cover construction, player/source access, synchronous status,
non-cancellability and shared static handler identity. The integration proof
performs a real controlled Overworld-to-Nether `ServerPlayer` transfer and
checks source/destination worlds, `NETHER`, location/world coherence, survival
game mode, stable player adapter, UUID/session lookup, preserved attachment,
server thread and exactly one listener call before emitting
`PLAYER_CHANGED_WORLD_OK` once. The WarpPlugin same-world teleport regression
continues to pass without emitting another world-change event.
