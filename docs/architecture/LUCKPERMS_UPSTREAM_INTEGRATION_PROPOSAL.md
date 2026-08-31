# LuckPerms upstream integration proposal for Bukkit-like runtimes

Status: **Proposal only; no issue or pull request opened**

## Problem

LuckPerms Bukkit 5.5.81 assumes a CraftBukkit-shaped implementation during
`setupPlatformHooks`. It directly replaces the private
`SimplePluginManager#permSubs`, `#permissions` and `#defaultPerms` maps, and at
Player login replaces the private CraftBukkit Player permissible field.

AtlasHybrid intentionally implements the public Bukkit contract over Forge and
does not contain CraftBukkit private classes or field layouts. Its Player
permission authority is `AtlasPermissible`, with a generic
`PermissionProvider` delegation point. LuckPerms reaches a complete context
manager and healthy storage, but fails before publishing its API because the
private Bukkit hook is unconditional.

## Why a configuration-only skip is insufficient

The Bukkit calculator consumes the injected permission/default maps for child
and default semantics, and the stock Bukkit backend relies on Player permissible
replacement to intercept `Player#hasPermission`. Merely catching the injector
failure or adding a flag that skips it would let startup continue while silently
breaking correctness. A supported contract must replace both interception and
the registry/default semantics that are actually enabled.

## Desired extension point

LuckPerms Bukkit should retain its current CraftBukkit implementation as the
default, while allowing a Bukkit-compatible runtime to provide a supported
permission-platform implementation before `setupPlatformHooks` runs.

The contract should cover capabilities rather than private fields:

- bind and unbind contextual permission decisions for online Players;
- observe or query registered Bukkit permissions and their children;
- observe or query op/non-op default permissions;
- expose permission subscription results or invalidation notifications where
  required for Bukkit semantics;
- cleanly remove hooks on disable/reload.

The exact interface name is deliberately left to upstream maintainers. A
conceptual shape could be a `BukkitPermissionPlatform` selected by a small
factory. Its methods should use public Bukkit and LuckPerms API types only.

## Bootstrap and discovery

Discovery must occur before the current `setupPlatformHooks` call and must be
specified as part of the supported contract. Viable upstream designs include:

1. a platform capability implemented by the server runtime and visible through
   a mutually shared API classloader;
2. a stable provider SPI loaded through a documented shared `ServiceLoader`
   boundary; or
3. an official external mode selected in configuration, where LuckPerms uses
   public fallback registry semantics, completes API publication, and then
   requires an external Player query provider before reporting enabled.

The third option has the simplest deployment story for AtlasHybrid, but it must
fail closed with a clear diagnostic if no external provider registers. It must
not claim successful enable while `Player#hasPermission` is disconnected.

The discovery mechanism must explicitly define class ownership. A provider and
LuckPerms must see the same API class identity; shading the LuckPerms API into
an adapter is not acceptable.

## Backward compatibility

- Default configuration continues to install the existing CraftBukkit hooks.
- Paper/Spigot/CraftBukkit behavior and performance remain unchanged.
- The new mode is opt-in or capability-selected.
- Existing public LuckPerms API behavior is unchanged.
- Unsupported runtimes receive a deterministic startup error rather than a
  private-class linkage failure.

## AtlasHybrid consumer behavior

After LuckPerms publishes `net.luckperms.api.LuckPerms`, an isolated adapter
would register an AtlasHybrid `PermissionProvider` and query:

```text
LuckPerms#getPlayerAdapter(Player.class)
  -> PlayerAdapter#getPermissionData(player)
  -> CachedPermissionData#checkPermission(node)
```

Result mapping is `TRUE -> allow`, `FALSE -> deny`, and
`UNDEFINED -> abstain`. LuckPerms owns contextual calculation; AtlasHybrid does
not reconstruct world, server or game-mode contexts.

## Security and correctness

- The external mode must not expose or accept arbitrary private-field access.
- A provider is registered and removed with explicit lifecycle ownership.
- Permission checks must not call storage synchronously; only loaded cached
  Player data is used.
- Missing, disabled or failed providers must fail closed or cause LuckPerms
  enable to fail, according to the agreed contract.
- Reload must not retain a provider, API object, task, thread or classloader.

## Testing

Upstream tests should retain all existing Bukkit-hook coverage and add a fake
public runtime provider proving:

1. startup completes without CraftBukkit classes;
2. true, false and undefined results retain their distinct meanings;
3. world/server/game-mode context changes recalculate correctly;
4. permission registration, children, defaults and subscriptions remain
   correct;
5. login, logout, reload and shutdown bind/unbind exactly once;
6. absence or failure of the external provider produces a clear, safe failure.

AtlasHybrid acceptance would additionally prove `/lp` mutation, contextual
`Player#hasPermission`, persistence across restart and zero lifecycle leaks.

## Scope and viability

This is a viable upstream discussion/PR direction, but it is not a one-line
feature flag. The existing Bukkit calculator and login injection depend on the
current hooks, so the change is medium-sized and needs maintainer input on API
ownership, discovery and supported semantics. AtlasHybrid should first open a
design discussion with this evidence; it should not submit an implementation or
bundle a fork without agreement.
