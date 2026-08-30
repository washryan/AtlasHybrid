# Minecraft login hook

## Decision and scope

Forge 43.5.0 has no event between construction of `ServerPlayer` and placement
in player collections and the level. `PlayerNegotiationEvent` is too early and
has no player; `EntityJoinLevelEvent` and `PlayerLoggedInEvent` are too late to
deny admission. AtlasHybrid therefore uses two narrowly scoped Mixins in
`platform-forge-1.19.2`. Runtime Core has no Minecraft dependency.

This implementation is pinned to Minecraft 1.19.2, Forge 43.5.0 and Mojang
mappings. A future platform version must audit and provide its own adapter.

## Main admission hook

| Property | Value |
|---|---|
| Target | `net.minecraft.server.network.ServerLoginPacketListenerImpl` |
| Method | `handleAcceptedLogin()V` |
| Injection | cancellable `@Inject` before semantic construction of `ClientboundGameProfilePacket` |
| Companion | exact `@Redirect` of `PlayerList#getPlayerForLogin(GameProfile, ProfilePublicKey)` |
| Purpose | construct the future vanilla `ServerPlayer` once, dispatch admission, then reuse it for ALLOW |
| ALLOW | return to the unmodified method; login-success, placement and Forge lifecycle proceed normally |
| DENY | cancel before login-success/placement, send `ClientboundLoginDisconnectPacket`, close after send, clean CONNECTING state |

The anchor is a semantic packet-construction point, not a line number or
ordinal. Profile completion, secure-profile validation and vanilla
`canPlayerLogin` have completed; no login-success packet was sent and
`PlayerList#placeNewPlayer` has not run. No method is overwritten or copied.
An `onDisconnect(Component)V` cleanup injection removes a transient adapter if
another failure aborts login.

## Handshake hostname hook

`Connection` and the login listener do not retain the requested hostname. A
second observational Mixin is necessary at the head of
`ServerHandshakePacketListenerImpl#handleIntention(ClientIntentionPacket)V`.
It stores `getHostName() + ":" + getPort()` in a weak connection-keyed map and
the admission gate consumes it. It neither cancels nor changes the handshake.

Without proxy forwarding, both Bukkit address fields contain the real
transport peer `InetAddress`. Proxy forwarding is outside this scope.

## Lifecycle and failure policy

```text
Forge negotiation -> AsyncPlayerPreLoginEvent (worker)
vanilla profile validation -> construct ServerPlayer
CONNECTING adapter -> PlayerLoginEvent (Server thread)
  ALLOW -> same adapter promoted by Forge PlayerLoggedInEvent -> PlayerJoinEvent
  DENY  -> adapter/permissions cleanup -> login disconnect -> no placement/join
disconnect after online -> PlayerQuitEvent -> session cleanup
```

The CONNECTING adapter is never visible through online-player collections or
lookups. Any unexpected bridge exception fails closed. The hooks do not post
Forge events, suppress negotiation, or replace `placeNewPlayer`; Forge event
counts and ordering remain vanilla.

## Conflict risk

Risk is **MEDIUM**. The main Mixin targets a central login method also useful to
security/login mods, and contains an injection plus a narrow redirect. The
semantic NEW anchor and exact invocation descriptor avoid fragile ordinals;
the absence of overwrite limits conflict surface. The handshake hook is
observational and low-impact. `required=true` and `defaultRequire=1` make an
incompatible transformation fail visibly instead of silently bypassing the
gate.

## Clean-room basis

The design derives from the public Bukkit API, Mojang-mapped Minecraft
development sources, Forge APIs and observed protocol behavior. It does not
copy CraftBukkit, Spigot implementation, Paper, Arclight, Mohist or Magma code.
