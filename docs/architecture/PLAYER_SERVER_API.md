# Player and server session API

AtlasHybrid implements a bounded Bukkit 1.19.2 server/player subset backed by
real Forge `ServerPlayer` sessions. It contains no plugin-specific branches.

## Public contract

The implemented `Server` methods are:

- `Collection<? extends Player> getOnlinePlayers()`;
- `Player getPlayer(UUID)`;
- `Player getPlayerExact(String)`.

The return type matches the pinned Bukkit 1.19.2 API. AtlasHybrid returns an
immutable snapshot in join order rather than Bukkit's dynamically changing
view. This keeps iteration safe while join/quit events mutate the registry.
`getPlayerExact` is case-insensitive, as required by Bukkit, and does not perform
partial matching. `getPlayer(String)` and offline-player APIs remain outside the
current subset.

## Session registry

Forge login creates one Bukkit adapter for the player's UUID and registers it
before `PlayerJoinEvent`. UUID lookup, exact-name lookup and online collection
iteration all return that same adapter for the lifetime of the session.

Forge logout publishes `PlayerQuitEvent` with the session adapter, then removes
it in `finally`. Removal closes the adapter's `AtlasPermissible`, including its
attachments and calculated state. Server shutdown clears and closes every
remaining session, so player state cannot leak into a later server lifecycle.

Registry operations are synchronized. Snapshots contain no duplicates, reject
conflicting simultaneous names and never retain disconnected players.

## Validation

Nine focused tests cover empty, single and multiple sessions, duplicate
registration, UUID and case-insensitive exact-name lookup, stable adapter and
permission identity, cleanup, shutdown clearing, and immutable snapshot
semantics. The integrated Forge proof posts fake login/logout events and emits
`ONLINE_PLAYERS_OK` exactly once only after all identity and cleanup assertions
pass.

Offline profiles, partial name matching, profile APIs and the wider `Player`
surface are deliberately deferred until a concrete compatibility path requires
them.
