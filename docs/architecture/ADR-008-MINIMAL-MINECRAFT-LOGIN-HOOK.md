# ADR-008: Minimal Minecraft login hook

## Status

Accepted for the Minecraft 1.19.2 / Forge 43.5.0 platform adapter.

## Context

Bukkit `PlayerLoginEvent` requires a real player and must still be able to deny
login before placement. Forge negotiation lacks a player, while Forge
entity/logged-in events occur after the denial boundary.

## Decision

Inject in `ServerLoginPacketListenerImpl#handleAcceptedLogin` before the
login-success packet, construct and retain the future `ServerPlayer`, and
redirect the later vanilla factory call to that object. Use a second,
observational handshake injection only because Minecraft discards the requested
hostname. Isolate both hooks and all Minecraft types in the Forge module.

CONNECTING adapters stay outside public online lookup. ALLOW promotes the same
adapter at the existing Forge logged-in event; DENY cleans it and cancels before
placement. Unexpected bridge failures deny admission.

## Alternatives rejected

- `PlayerNegotiationEvent`: correct denial timing but no `ServerPlayer`.
- `EntityJoinLevelEvent` or `PlayerLoggedInEvent`: already too late.
- `PlayerList#placeNewPlayer`: larger placement surface and after login-success.
- overwrite/copy: excessive maintenance and conflict risk.
- invented hostname or IP-as-hostname: violates the public event contract.

## Consequences

The hook is semantically correct and small, but version-coupled and has MEDIUM
conflict risk with other login Mixins. Required injection counts make mapping
drift explicit. Future platform adapters require a fresh audit. No public NMS
API or runtime-core Minecraft dependency is introduced.
