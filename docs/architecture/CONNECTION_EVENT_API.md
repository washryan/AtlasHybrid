# Connection event API

## Scope

Phase 9.11 implements the Bukkit 1.19.2
`AsyncPlayerPreLoginEvent` contract and bridges it to the real Forge login
pipeline. It does not implement `PlayerLoginEvent`, proxy forwarding, or a
CraftBukkit login facade.

The public contract was checked against Spigot API
`1.19.2-R0.1-20221207.161214-43`. The event exposes the two-argument deprecated
constructor and the `(String, InetAddress, UUID)` constructor; name, address,
UUID, kick message, `allow`, `disallow`, and current/legacy result accessors are
present. Its result values are, in API order, `ALLOWED`, `KICK_FULL`,
`KICK_BANNED`, `KICK_WHITELIST`, and `KICK_OTHER`. The event is constructed with
`Event(true)`, so `isAsynchronous()` reflects actual dispatch semantics.

`PlayerPreLoginEvent` remains deprecated in the target API. AtlasHybrid carries
its public data/result type because the deprecated compatibility overloads on
`AsyncPlayerPreLoginEvent` reference it, but AtlasHybrid does not dispatch a
second synchronous pre-login event. `PlayerLoginEvent` is deferred: it is a
distinct cancellable stage with a constructed player and is the next LuckPerms
boundary, not part of this phase. Existing `PlayerJoinEvent` and
`PlayerQuitEvent` dispatch remains unchanged.

## Pipeline and thread model

```text
TCP socket accepted
  -> Minecraft handshake and profile/authentication step
  -> Forge PlayerNegotiationEvent
  -> queued negotiation work on a real worker thread
  -> AsyncPlayerPreLoginEvent
       denied -> close the real connection; no player/session/join
       allowed -> negotiation completes
  -> vanilla player construction and registration
  -> Forge PlayerLoggedInEvent -> Bukkit PlayerJoinEvent
  ...
  -> Forge PlayerLoggedOutEvent -> Bukkit PlayerQuitEvent
  -> AtlasHybrid player-adapter/session cleanup
```

Forge waits for work enqueued on `PlayerNegotiationEvent` before accepting the
login. AtlasHybrid snapshots the already-available profile and remote socket
address, then dispatches through the existing `AtlasPluginManager` from Forge's
worker future, not the `Server thread`. No second event bus or spoofed thread is
used. Existing listener priority, executor exception isolation, unregister,
and plugin ownership rules therefore apply.

In online mode the event UUID is the authenticated `GameProfile` UUID. In
offline mode it is Minecraft's deterministic
`UUIDUtil.createOfflinePlayerUUID(name)` result. `InetAddress` comes from the
real `InetSocketAddress` on the connection; the event is skipped with a warning
if that address is unavailable instead of substituting fake data. Proxy
forwarding is not supported, so this is the transport peer address, not an
invented forwarded address.

Calling `disallow` closes the same Minecraft connection before player creation.
The selected result and reason are logged, the rejected identity never appears
in the online-player registry, no `PlayerJoinEvent` fires for it, and no player
adapter, attachment, provider, service, or session state is created. Depending
on the exact negotiation close timing the raw client may receive the login
disconnect frame or observe transport EOF; the authoritative server log retains
the requested kick reason in both cases.

## LuckPerms 5.5.81 listener audit

The unchanged embedded Bukkit implementation declares these connection
handlers in `BukkitConnectionListener`. None specifies `ignoreCancelled`, so
the Bukkit annotation default (`false`) applies.

| Event | Handler | Priority | Thread | Purpose | Need |
|---|---|---|---|---|---|
| `AsyncPlayerPreLoginEvent` | `onPlayerPreLogin` | `LOW` | Async | Wait for enable, respect an earlier denial, load user data, and deny with `KICK_OTHER` on load failure | Required |
| `AsyncPlayerPreLoginEvent` | `onPlayerPreLoginMonitor` | `MONITOR` | Async | Prevent another listener from re-allowing a login whose data load failed | Required |
| `PlayerLoginEvent` | `onPlayerLogin` | `LOWEST` | Sync | Resolve preloaded user, reject invalid loading state, inject the player permissible, and update context | Required for LuckPerms' Bukkit player integration; deferred |
| `PlayerLoginEvent` | `onPlayerLoginMonitor` | `MONITOR` | Sync | Prevent re-allow after a LuckPerms denial and check permissible injection | Required for LuckPerms' Bukkit player integration; deferred |
| `PlayerQuitEvent` | `onPlayerQuit` | `MONITOR` | Sync | Disconnect user state and schedule permissible uninjection | Required; type already present |

LuckPerms does not declare `PlayerPreLoginEvent` in this listener. Its other
platform listener classes also declare command, server-command, plugin,
world/game-mode, and related events; those are outside this connection-phase
implementation.

## Verification

The controlled proof opens two real Minecraft protocol 760 loopback sockets.
`AtlasDenied` is rejected with `KICK_OTHER` before a session exists;
`AtlasAllowed` is temporarily disallowed and then re-allowed, receives login
success, produces join and quit events, and leaves no online-player entry. The
event validates the real offline UUID, loopback address, and worker-thread
classification. `ASYNC_PRELOGIN_OK` and `PRELOGIN_DENY_OK` each occur exactly
once before the unchanged main integration proof and clean shutdown.

Unit coverage includes construction, fields, default result, current and
legacy result mappings, kick message, disallow/allow, asynchronous
classification, listener priority, and listener-exception continuation.
