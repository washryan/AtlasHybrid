# ADR-009: LuckPerms permission bridge

Status: **Decision proposed; implementation deferred**

## Context

AtlasHybrid has a generic permission pipeline: `AtlasPermissible` evaluates
attachments, then priority-ordered `PermissionProvider`s, then the Bukkit core.
Providers are plugin-owned, can return true, false or abstain, and are removed
with their owner. The public `ServicesManager` is independent and supports
priority lookup and lifecycle cleanup.

Raw boot #20 of the unchanged LuckPerms Bukkit 5.5.81 artifact completes
storage, commands, platform listeners and the context manager. It then fails in
`LPBukkitPlugin#setupPlatformHooks` while linking
`InjectorSubscriptionMap`, because AtlasHybrid deliberately has no
`org.bukkit.plugin.SimplePluginManager` implementation.

## Problem

LuckPerms normally takes over `Player#hasPermission` by replacing the private
`CraftHumanEntity#perm` field with `LuckPermsPermissible`. Its Bukkit backend
also replaces private maps in `SimplePluginManager`. AtlasHybrid owns its
Player adapter and `AtlasPermissible`; reproducing CraftBukkit private layout
would violate the clean-room architecture and create a brittle compatibility
shape.

The desired query is instead:

```text
AtlasPermissible.hasPermission(node)
  -> PermissionProvider
  -> LuckPerms public PlayerAdapter / cached permission data
  -> contextual Tristate
  -> boolean result
```

## Evidence from raw boot #20

The observed order in `AbstractLuckPermsPlugin#enable` is:

1. platform listeners;
2. storage and messaging;
3. commands;
4. internal managers and calculator factory;
5. context manager and calculators;
6. `setupPlatformHooks`;
7. construct `LuckPermsApiProvider`;
8. register `LuckPermsProvider` singleton;
9. register `net.luckperms.api.LuckPerms` in Bukkit `ServicesManager`;
10. extensions, initial data load, final platform setup and running state.

Raw boot #20 stops at step 6. Consequently neither the static provider nor the
service is available. This is a real bootstrap chicken-and-egg: a public-API
adapter cannot be installed after service registration until LuckPerms first
passes the private platform hooks.

## CraftBukkit assumptions

| Class / method | Purpose | Dependency class | Required by 5.5.81 Bukkit backend |
|---|---|---|---|
| `InjectorSubscriptionMap#tryInject` | replace permission subscriptions | private `SimplePluginManager#permSubs` | required for Bukkit subscription/broadcast semantics; replacement map is the chosen optimization |
| `InjectorPermissionMap#tryInject` | observe registered permissions and children | private `SimplePluginManager#permissions`; private `Permission#children` | required by enabled child/default calculators |
| `InjectorDefaultsMap#tryInject` | observe op/non-op defaults | private `SimplePluginManager#defaultPerms` | required by enabled Bukkit-default calculator |
| `PluginManagerUtil#injectDependency` | suppress Vault load-order warning | private `SimplePluginManager#dependencyGraph` | optional workaround; silently skipped |
| `PermissibleMonitoringInjector` | verbose monitoring for console, command blocks and entities | CraftBukkit classes and `perm` fields | optional monitoring; failures are ignored |
| `PermissibleInjector#inject` | route player checks into LuckPerms | private `CraftHumanEntity#perm` or Glowstone `permissions`; private `PermissibleBase#attachments` | required by this backend for player correctness; failure denies login |

`InjectorSubscriptionMap` reads the `permSubs` object as
`Map<String, Map<Permissible, Boolean>>`, migrates non-player subscriptions,
and replaces it with `LuckPermsSubscriptionMap`. That map excludes stored
Player subscriptions and dynamically returns every online Player for which
`hasPermission` or `isPermissionSet` succeeds. LuckPerms documents this as the
faster/lower-memory alternative to subscribing every Player normally. The map
replacement itself is an implementation strategy; correct results from public
`getPermissionSubscriptions` consumers such as permission broadcasts are the
functional requirement.

Creating a class named `SimplePluginManager` without these exact private field
names would only move the failure. Emulating all fields would be a fake
CraftBukkit private shape and is rejected.

## Permissible injection

Player injection occurs after `setupPlatformHooks`: at `LOWEST`
`PlayerLoginEvent` in `BukkitConnectionListener`, and from
`performFinalSetup` for players already online during reload. It creates
`LuckPermsPermissible`, migrates attachments, clears the old permissible and
sets the private Player field. There is no fallback; injection failure denies
login. Quit later restores or replaces the injected object.

This mechanism is mandatory for the stock Bukkit backend's interception of
`Player#hasPermission`, but it is not inherently required on AtlasHybrid.
`ForgePlayerAdapter` already delegates all checks to its own
`AtlasPermissible`, so a provider can supply the same decision without
replacing a Player field.

## Public LuckPerms query

Once the API is registered, the supported online-player path is:

```text
LuckPerms#getPlayerAdapter(Player.class)
  -> PlayerAdapter#getPermissionData(player)
  -> User#getCachedData()
  -> ContextManager-derived QueryOptions for that Player
  -> CachedPermissionData#checkPermission(node)
  -> Tristate
```

This accounts for inheritance, server, world, dimension and game-mode contexts
through the already registered calculator. It is cached and synchronous. The
public API is sufficient for direct online-player permission decisions and
does not require LuckPerms internals. `UserManager#getUser(UUID)` is appropriate
only for loaded users; offline loading is asynchronous and lacks a live Player
context, so offline permissions remain future scope.

The Bukkit `ServicesManager` is the preferred discovery mechanism. A module
declaring a hard dependency on LuckPerms can resolve the API from the LuckPerms
plugin classloader and call `ServicesManager#load(LuckPerms.class)`. However,
the service is published only after the currently failing hooks, so discovery
does not solve bootstrap by itself.

## AtlasHybrid PermissionProvider assessment

The current SPI is sufficient for a first online-player adapter:

- `PermissionSubject` supplies type and UUID; the adapter can resolve the
  stable online `Player` through the captured public `Server`;
- `Optional<Boolean>` represents true, false and abstain;
- priority and deterministic registration order allow one authoritative
  external provider;
- provider calls are outside registry locks and failures fall through safely;
- registration is owner-scoped and plugin disable unregisters providers;
- LuckPerms cached queries are suitable for synchronous `hasPermission` calls.

No SPI change is required for the PARTIAL milestone. FULL compatibility may
need a generic way to keep provider-backed Players visible through Bukkit
permission subscriptions and to reconcile registered Bukkit permission
children/default changes with external calculators. AtlasHybrid already
implements the public registry and subscription methods, but its subscription
sets do not synthesize arbitrary provider results. That enhancement must remain
generic and must not expose LuckPerms types in runtime-core.

Attachments remain highest precedence in AtlasHybrid. A future adapter must
define the mapping of LuckPerms `UNDEFINED`: for authoritative player checks it
must preserve normal LuckPerms/Bukkit default behavior rather than accidentally
granting AtlasHybrid's unknown-node OP fallback.

Console is separate. LuckPerms' Bukkit command sender checks delegate to the
console's existing `hasPermission`; its CraftBukkit console injection is for
verbose monitoring, not a LuckPerms User. The first adapter should therefore
answer only `PermissionSubject.Type.PLAYER` and abstain for console/other.

## Options and tradeoffs

| Option | Correctness / generality | Maintenance and compatibility | Decision |
|---|---|---|---|
| A. Optional `compat-luckperms` provider using the public API | High for online contextual checks; isolated and removable | Low private-API risk, MIT API compile-only; cannot by itself cross current bootstrap ordering | **Recommended query adapter, contingent on supported bootstrap cooperation** |
| B. Generic service-backed permission bridge | Reusable for future systems; current SPI already covers the query | A service observer/lifecycle layer may be useful, but broad abstraction now risks overengineering | Use only the minimal lifecycle mechanism needed by A |
| C. Public Bukkit registry/subscription semantics | Correct generic requirement for broadcasts/defaults; useful beyond LuckPerms | Moderate work; does not make stock LuckPerms stop injecting private maps | Later FULL-compatibility phase, not the bootstrap fix |
| D. `SimplePluginManager` compatibility implementation | Would satisfy linkage only by reproducing private fields and behavior | High upgrade risk, CraftBukkit assumptions and misleading binary shape | **Rejected** |
| E. Mixin, bytecode patch or reflection bypass | Could skip/replace hooks | Invasive, version-specific, modifies expected control flow and violates policy | **Rejected** |

License impact of the recommended adapter is limited to a normal compile-only
dependency on the MIT-licensed public LuckPerms API. No LuckPerms or
CraftBukkit source/JAR is copied or bundled.

## Decision

Use AtlasHybrid's existing `AtlasPermissible` and `PermissionProvider` as the
authority boundary. Do not inject `LuckPermsPermissible`, create
`SimplePluginManager`, emulate private field names, patch the LuckPerms JAR, or
instrument bytecode.

An explicit optional LuckPerms adapter is architecturally acceptable, not a
hack, only if it:

- lives outside runtime-core;
- depends only on the official LuckPerms API;
- discovers the API through `ServicesManager` after publication;
- registers an owner-scoped AtlasHybrid `PermissionProvider`;
- queries the public contextual `PlayerAdapter` path;
- cleanly unregisters on disable/reload;
- does not pretend AtlasHybrid is CraftBukkit.

For unchanged LuckPerms Bukkit 5.5.81, an acceptable adapter cannot currently
start because the API is published after mandatory private hooks. The missing
piece must be an officially supported LuckPerms bootstrap/platform integration
point: for example an upstream-recognized platform capability that lets
LuckPerms delegate Player permission checks and public permission-registry
semantics instead of installing CraftBukkit hooks. Until that exists, status
remains **BLOCKED — ARCHITECTURAL DECISION**, not permanently incompatible.

## Rejected strategies

- copying CraftBukkit or creating `org.bukkit.craftbukkit` classes;
- a fake `SimplePluginManager` with `permSubs`, `permissions`, `defaultPerms`
  or `dependencyGraph` fields;
- reflection against LuckPerms private plugin state;
- modifying or repackaging the LuckPerms JAR;
- replacing `CraftHumanEntity#perm` or inventing a CraftPlayer;
- Mixins, agents, bytecode instrumentation or monkey patches.

## Implementation phases

Proposed next phase:

**FASE 9.22 — LuckPerms supported bootstrap contract and public API bridge
prototype**

1. specify the minimal generic AtlasHybrid platform capability exposed before
   plugin enable;
2. validate with LuckPerms maintainers/source whether it can be consumed through
   a supported extension or upstream platform hook, without private reflection;
3. prototype an isolated `compat-luckperms` module only if that supported entry
   point exists;
4. after API publication, obtain `LuckPerms` from `ServicesManager` and register
   a Player-only `PermissionProvider`;
5. stop if the only available bootstrap mechanism is patching or private-state
   emulation.

This phase must not silently implement a fork or patched artifact.

## Testing plan

The future bridge must prove: absent node false; `/lp user <player> permission
set example.test true` causes `Player#hasPermission("example.test")` true;
revoke restores false; a world-scoped node follows world changes; restart
persists; adapter registration is unique; reload/shutdown leaves no provider,
service, task, classloader or thread leak.

PARTIAL requires complete LuckPerms enable, working `/lp`, mutation,
Player `hasPermission` delegation and persistence. FULL additionally requires
context recalculation, lifecycle/events/commands/services, correct public
permission subscriptions and registered defaults/children, restart/reload and
no resource leaks or remaining blockers.
