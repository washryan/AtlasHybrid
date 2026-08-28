# ADR-006: AtlasHybrid-owned permission system

- Status: **Accepted for phased implementation**
- Date: 2026-08-28
- Scope: architecture only; no permission implementation is included in Phase 9.1
- Compatibility target that triggered the decision: LuckPerms Bukkit `5.5.81`
  at `32494e9f0ab14857b63650ab68a65222d1924a93`

## Problem

AtlasHybrid currently answers `CommandSender#hasPermission` directly from a
Forge command-level check. That is sufficient for the original proof but cannot
represent Bukkit permissions, defaults, attachments, service providers, or an
external permission engine.

LuckPerms demonstrates both layers of the problem:

1. it consumes the public Bukkit permission and services contracts; and
2. its Bukkit platform replaces CraftBukkit private state so
   `Player#hasPermission` reaches `LuckPermsPermissible`.

AtlasHybrid needs a generic permission architecture for LuckPerms, Vault,
EssentialsX, and ordinary Bukkit plugins without copying CraftBukkit or adding a
LuckPerms-specific runtime hack.

## Decision

AtlasHybrid will own the permission state and compose it into every Bukkit-facing
subject. The runtime will expose a small, generic provider hook that can replace
or decorate resolution without modifying Minecraft classes.

```text
ForgePlayerAdapter / AtlasConsoleSender
                |
                v
        AtlasPermissible
        |             |
        v             v
 public Bukkit core   active provider delegate
 defaults/children/   (optional, lifecycle-owned)
 attachments/op
                |
                v
       AtlasPermissionRegistry
```

The default delegate implements Bukkit public semantics. A permission plugin may
atomically install a provider/delegate through an Atlas-owned compatibility SPI.
Player and console adapters always call the composed `AtlasPermissible`; no field
is injected into `ServerPlayer`, and no Minecraft/Forge Mixin is required.

This does not by itself make the unmodified LuckPerms Bukkit JAR compatible. Its
current Bukkit platform only knows the CraftBukkit/Glowstone field-replacement
path. LuckPerms must use the generic Atlas hook through an upstream platform
adapter or separately reviewed bridge. The hook is generic; an adapter is not to
be hidden inside AtlasHybrid runtime code.

## LuckPerms evidence

The exact evidence is catalogued in
[`LUCKPERMS_API_AUDIT.md`](../compatibility/LUCKPERMS_API_AUDIT.md). The decisive
path is:

1. `BukkitConnectionListener#onPlayerLogin` constructs
   `LuckPermsPermissible`.
2. `PermissibleInjector#inject` reads
   `CraftHumanEntity#perm`, transfers the existing `PermissibleBase#attachments`,
   and writes the LuckPerms instance into the player.
3. CraftBukkit's player permission methods subsequently delegate to that field.
4. On quit or disable, LuckPerms restores or replaces the old permissible.

Therefore the precise answer to the injection question is **B as the mechanism
for A**. On its Bukkit platform, LuckPerms literally replaces the internal
permissible; that replacement is how Bukkit calls are intercepted. It is not an
optional observation-only hook. Console/entity replacement is different: it is
only used by verbose monitoring and each failure is swallowed.

The three `SimplePluginManager` map replacements also catch and log injection
failure, so they do not necessarily abort enable immediately. They are still
required for correct default/child/subscription semantics with the default
LuckPerms configuration. The permission calculator dereferences the injected
maps when Bukkit defaults are enabled; treating those failures as harmless would
produce delayed errors or incorrect decisions.

## Public API versus implementation shape

AtlasHybrid can implement these contracts independently from public
documentation and behavioral tests:

- `ServerOperator`, `Permissible`, and `PermissibleBase` behavior;
- `Permission`, `PermissionDefault`, `PermissionAttachment`,
  `PermissionAttachmentInfo`, and `PermissionRemovedExecutor`;
- permission registration/subscription methods on `PluginManager`;
- permission-capable `CommandSender`, `ConsoleCommandSender`, and `Player`;
- `ServicesManager`, `RegisteredServiceProvider`, and `ServicePriority`.

The following are not public contracts and will not define AtlasHybrid's core:

- private `SimplePluginManager` fields `permissions`, `defaultPerms`,
  `permSubs`, `commandMap`, and `dependencyGraph`;
- private `PermissibleBase` fields `attachments` and `permissions`;
- private `Permission#children` and `PermissionAttachment#permissions` fields;
- `org.bukkit.craftbukkit.*` class names and fields;
- `PluginClassLoader#getPlugin` reflection.

A clean-room `SimplePluginManager` with matching private field names is
technically possible, and would satisfy the corresponding `instanceof` and
reflection checks. It would nevertheless turn private upstream layout into an
AtlasHybrid compatibility ABI. It also does not solve player injection unless
AtlasHybrid creates a fake `CraftHumanEntity` inheritance hierarchy. This option
is rejected as the primary architecture.

## Permission core semantics

Phase 9.2 must define these behaviors with tests before integrating a provider:

| Concern | Decision |
|---|---|
| Ownership | The permission registry belongs to the server. Each subject owns one `AtlasPermissible`. Attachments and service/provider registrations retain their owning plugin. |
| Names | Normalize permission lookup keys with `Locale.ROOT`; preserve the declared name for API reporting. Reject null or empty names. |
| Defaults | Resolve registered `PermissionDefault` against the subject's current op state. Unknown permission behavior follows the public `Permission` contract and must be pinned by behavioral tests. |
| Children | Expand explicit `Permission#getChildren` relationships recursively with cycle detection. Parent false values invert child values as required by Bukkit behavior. |
| Attachments | Maintain ordered, plugin-owned attachments. Mutations trigger recalculation. Exact conflict precedence must be established by black-box behavioral tests before release. |
| Recalculation | Publish an immutable effective-permission snapshot after registry, op, child, provider, or attachment changes. Notify subscribed permissibles once per completed change. |
| Op changes | Recalculate defaults and provider context; never equate op with unconditional permission success. |
| Wildcards | Bukkit core will not invent implicit `x.*` matching. Explicit children are core behavior; a provider such as LuckPerms may implement its own wildcard rules. |
| Lifecycle | Disabling a plugin removes its attachments, service registrations, provider registration, tasks, and subscriptions even if `onDisable` throws. Subjects fall back atomically to the default resolver. |
| Thread safety | Reads use immutable snapshots. Registry/provider/attachment mutations are serialized and publish a new snapshot atomically. No plugin callback executes while an internal lock is held. |

Temporary attachments use AtlasHybrid's main-thread scheduler and are rejected
when the owner is disabled. Removing an attachment calls its removal callback at
most once.

## Command senders

`CommandSender` must extend the public permissible contract. Player and console
adapters delegate all of these methods to their own `AtlasPermissible`:

- `hasPermission` and `isPermissionSet`, by name and `Permission`;
- all `addAttachment` overloads;
- `removeAttachment`;
- `recalculatePermissions`;
- `getEffectivePermissions`;
- `isOp` and `setOp` where the underlying subject supports mutation.

The console is modeled as a real op subject, not as a hard-coded “all permissions
true” value. Player op state comes from the Minecraft server operator list. The
permission object is composed beside the Minecraft adapter:

```text
ForgePlayerAdapter
|- ServerPlayer transport/state
`- AtlasPermissible permission state
```

## Services manager

AtlasHybrid will implement the public services registry independently of the
permission-provider SPI. Registrations contain the service class, provider,
owner plugin, and priority. Queries return the highest-priority provider and
returned collections are immutable snapshots. Equal-priority ordering and
service registration/unregistration events must be pinned by behavioral tests
before the implementation is treated as compatible.

The registry must support register, load, registration lookup, service/plugin
enumeration, targeted unregister, provider unregister, and `unregisterAll`.
Plugin shutdown always calls `unregisterAll(owner)`. LuckPerms uses this registry
to publish `net.luckperms.api.LuckPerms` at `Normal`; its optional Vault hook
publishes Vault permission/chat providers at `High` and explicitly unregisters
them.

Services publication lets consumers find the LuckPerms API, but it does not make
`Player#hasPermission` delegate to LuckPerms. These are separate integration
paths.

## Bootstrap sequence

```text
AtlasHybrid boot
  -> permission registry and default provider
  -> services manager
  -> console/player adapter factories
  -> plugin manager and loader
  -> plugin onLoad
  -> plugin onEnable
  -> external provider installs through generic Atlas hook
  -> existing and future subjects atomically observe provider
  -> disable removes provider/services/attachments and restores default core
```

For a future LuckPerms adapter, user data must be ready before the player becomes
available to plugins, and provider removal must preserve permission behavior for
quit listeners until their documented event phase completes.

## Considered strategies

| Strategy | Compatibility | Complexity / maintenance | Forge conflict risk | Generality | License / version risk | Decision |
|---|---|---|---|---|---|---|
| A. Atlas-owned permissible composition | Requires providers to use Atlas hook | Moderate; stable because Atlas owns the seam | Low; no Minecraft mutation | High | Clean-room, low upstream-layout fragility | **Foundation selected** |
| B. Binary-compatible Bukkit/CraftBukkit shape | Best chance for unmodified reflection-heavy Bukkit artifacts | High; freezes private fields and fake inheritance | Medium | Medium; favors CraftBukkit assumptions | Clean-room code is possible, but names/layout are version-fragile and require careful notices | Rejected as primary; may be separately researched as a compatibility profile |
| C. Targeted Mixin/instrumentation | Could redirect a specific check or plugin injector | High and collision-prone | High with coremods/mods and plugin bytecode | Low when targeted | Version- and artifact-fragile; risks becoming plugin-specific | Rejected; not authorized |
| D. Atlas compatibility hook/SPI | Needs provider cooperation or adapter | Low-to-moderate after A | Low | High across permission engines | Atlas-owned API; versionable | **Selected with A** |

## Mixin gate

No Mixin is required for the selected architecture because AtlasHybrid owns
`ForgePlayerAdapter` and command sender adapters. No Mixin is authorized or
implemented in Phase 9.1.

If the project later requires the unmodified LuckPerms Bukkit artifact with no
provider adaptation, the unresolved target is not `ServerPlayer`; it is
LuckPerms' assumption that `PermissibleInjector` can replace
`CraftHumanEntity#perm`. Redirecting that behavior would require transforming or
adapting plugin code, or emulating the CraftBukkit class shape. A hypothetical
plugin transformation would target
`me.lucko.luckperms.bukkit.inject.permissible.PermissibleInjector#inject` and
`#uninject` to call the Atlas hook. Its collision risk is high, it is tied to the
LuckPerms artifact, and it is therefore rejected as a runtime strategy.

## Clean-room boundary

Implementation will use Bukkit's public contracts, AtlasHybrid-owned types, and
behavioral tests. No CraftBukkit, Spigot, Paper, Arclight, Mohist, or Magma source
will be copied. Private names recorded in the audit are compatibility evidence,
not implementation templates.

## Prototype decision

No public API prototype is implemented in Phase 9.1. Although the individual
type names are generic, implementing only `ConsoleCommandSender` would merely
move the first linkage error. Implementing permission classes with private field
shape chosen for LuckPerms would prematurely select Strategy B. The safe public
surface is instead the first deliverable of Phase 9.2, accompanied by behavioral
tests and the provider SPI.

Consequently there is no second raw boot in Phase 9.1. The next static boundary
after the public types is already known: unmodified LuckPerms must replace a
player permissible through `CraftHumanEntity#perm`, with only a Glowstone field
fallback. A raw boot cannot resolve that architectural fact.

## Implementation phases

### Phase 9.2: Permission Core and Services

1. Add the public permission types and expand `CommandSender`, `Player`,
   `PluginManager`, and `Server` contracts.
2. Implement `AtlasPermissionRegistry`, `AtlasPermissible`, console/player
   composition, and plugin-owned attachment cleanup.
3. Define and version the generic provider SPI, with atomic install/remove and
   fallback to the Atlas resolver; validate it with a neutral conformance fixture.
4. Implement the generic services manager with priority and lifecycle cleanup.
5. Add behavioral tests for defaults, op defaults, explicit children,
   attachment add/override/remove/recalculate, service priority, and unregister.
6. Keep the existing Forge permission behavior as an explicit fallback adapter,
   not a hard-coded answer in senders.
7. Run the full unit, integration, and reproducibility suite.

Phase 9.2 must not claim LuckPerms support and must not add a LuckPerms-specific
bridge.

### Later reviewed phase: provider integration

Use the versioned Atlas provider hook and its neutral conformance fixture to
pursue an upstream LuckPerms Atlas platform adapter or separately reviewed
external bridge. Only after that should the official LuckPerms artifact path be
tested for load, login, commands, storage, service publication, permission
decisions, restart, and shutdown.
