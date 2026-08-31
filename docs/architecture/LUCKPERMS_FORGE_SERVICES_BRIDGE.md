# LuckPerms Forge Bukkit services bridge

## Decision and scope

AtlasHybrid publishes the same public `LuckPerms` API instance owned by the
official Forge 5.4.46 backend through Bukkit `ServicesManager`. This lets a
Bukkit plugin compiled against `net.luckperms:api:5.4` discover and use the API
without loading LuckPerms implementation classes.

This bridge does not create a Bukkit plugin named LuckPerms. It does not make
`PluginManager#getPlugin("LuckPerms")` succeed, satisfy `depend: [LuckPerms]`,
provide the Bukkit PlayerAdapter, or implement Vault.

## Discovery and registration

```text
LuckPerms Forge enable
  -> public LuckPermsProvider#get()
  -> Atlas permission provider registration
  -> ServicesManager#register(
       LuckPerms.class,
       the same API instance,
       AtlasHybridCompatibility owner,
       ServicePriority.Normal)
```

The bridge is constructed only when Forge reports the `luckperms` mod. Absence
therefore remains silent and does not load optional API-dependent classes.
Registration states are `ABSENT`, `DISCOVERED`,
`PERMISSION_PROVIDER_BOUND`, `BUKKIT_SERVICE_REGISTERED`,
`SERVICE_UNREGISTERED`, and `FAILED`.

Repeated discovery is idempotent for the Atlas-owned registration. If the
public provider disappears or changes identity, Atlas unregisters the old
service and permission provider before binding the replacement. No placeholder
or null service is ever published.

## Ownership

Bukkit service registrations require a non-null `Plugin` owner. The service is
owned by the internal `AtlasHybridCompatibility` identity. It represents the
Atlas runtime compatibility subsystem, is not named LuckPerms, and is never
registered or returned by the Bukkit PluginManager. Its only role is service
ownership and deterministic `unregisterAll(owner)` cleanup.

This is not a virtual LuckPerms plugin identity and cannot satisfy plugin
dependencies.

## Class identity

The Atlas Forge module and the proof plugin compile against the public API as
`compileOnly`. Neither JAR contains `net/luckperms/api/**`. In the production
proof, `LuckPerms.class` was loaded once by ModLauncher's shared
`TransformingClassLoader`; the Forge implementation remained in LuckPerms'
`JarInJarClassLoader`. A Bukkit plugin successfully invoked the implementation
through that shared interface without class or linkage errors.

The proof requires:

- `ServicesManager.load(LuckPerms.class) == LuckPermsProvider.get()`;
- exactly one Atlas service registration at `ServicePriority.Normal`;
- metadata version `5.4.46`;
- public UserManager, loaded User, primary group, contextual QueryOptions and
  cached permission query access.

The test plugin also compiles the API as `compileOnly`, and its archive is
checked to contain zero LuckPerms API classes.

## Query coherence

The ServicesManager API proof and the Phase 9.24B `Player#hasPermission` bridge
read the same loaded User and live QueryOptions. For LuckPerms `TRUE` and
`FALSE`, the API tristate and Bukkit boolean must agree. For `UNDEFINED`, the
API reports undefined while Bukkit deliberately continues to Atlas attachments,
providers and defaults.

Context is obtained from `ContextManager#getQueryOptions(User)`, never built by
Atlas. The production proof observed `world`, `dimension-type`, and `gamemode`
and demonstrated a contextual `gamemode=creative` FALSE coherently through
both paths. The earlier permission-bridge proof also covered the live survival
TRUE to creative FALSE transition.

## Lifecycle and shutdown

`lp reloadconfig` retains the same public provider in LuckPerms 5.4.46; service
queries remain valid after reload. A future replacement identity is handled by
the regular refresh/rebind path.

At `ServerStoppingEvent`, Atlas removes the Bukkit service first, then removes
its permission provider and disables Bukkit plugins. The internal owner also
receives explicit `unregisterAll` cleanup. Only afterward does Forge stop
LuckPerms and close H2, so no Bukkit lookup can retain a dead API registration.
A subsequent server start creates one fresh registration.

## Security and non-goals

The bridge uses only `net.luckperms.api.*` and existing public Bukkit service
APIs. It contains no reflection, LuckPerms internals, CraftBukkit compatibility,
Mixin, bytecode patch, shaded API, relocated API, external JAR, fake LuckPerms
Plugin, dependency resolution, or Vault integration.

Compatibility remains **PARTIAL**: public API service discovery and Player
permission authority pass, while Bukkit plugin identity, hard dependencies,
Bukkit PlayerAdapter, Vault, and unproven registry/subscription parity remain
unsupported.
