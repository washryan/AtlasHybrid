# Virtual plugin dependencies

## Purpose

AtlasHybrid can explicitly advertise that a compatibility integration satisfies
a Bukkit plugin dependency name even though no Bukkit plugin with that identity
exists. This is a loader capability, not a virtual plugin.

The first use is the reviewed LuckPerms Forge integration:

```text
depend: [LuckPerms]
  -> real Bukkit plugin named LuckPerms? use the real plugin
  -> explicit available virtual capability named LuckPerms? satisfy dependency
  -> otherwise reject the missing hard dependency
```

Forge mods do not automatically become capabilities. Each compatibility bridge
must register its name explicitly after its complete usable contract exists.

## Model and ownership

`VirtualPluginDependencyRegistry` stores `VirtualPluginDependency` records with:

- compatibility name;
- `AVAILABLE` state (absence is observed as `UNAVAILABLE`);
- owner identity;
- optional diagnostic version;
- description.

Names use the same case-insensitive `Locale.ROOT` matching already used by the
Atlas plugin dependency resolver. Version metadata is diagnostic only because
classic `plugin.yml` dependency lists do not express version constraints.

Registration is idempotent for the same name and owner. A second owner cannot
silently replace an existing capability. Entries support exact unregister and
owner-wide cleanup.

## Resolver semantics

Real plugin candidates always take precedence. Their normal graph node,
classloader relationship, load order and load-failure propagation remain
unchanged. If a real candidate and a capability have the same name, Atlas emits
a conflict diagnostic and does not silently substitute the capability if the
real plugin fails.

A virtual hard dependency permits the dependent candidate to load but creates
no lifecycle or classloader node. Shared APIs must already be visible through
the Atlas parent classloader. A missing virtual hard dependency retains the
normal `DependencyResolutionException` behavior.

`softdepend` remains non-blocking. A virtual soft dependency has no ordering
node because a capability has no plugin lifecycle. The current Atlas metadata
subset does not parse or implement `loadbefore`; therefore no virtual
`loadbefore` semantics are claimed.

## Lifecycle

Capabilities must be registered before dependent class construction, `onLoad`
or `onEnable`. When a capability disappears at runtime, Atlas makes the
capability unavailable first, then removes the underlying service/provider and
logs the loss. It does not dynamically disable already-enabled dependents:
Bukkit does not define a general hot-loss
dependency lifecycle, and inventing one would risk inconsistent plugin state.
Subsequent service lookups fail safely and the capability cannot satisfy a new
load cycle.

All capabilities are removed during owner cleanup and rebuilt once on restart.

## Explicit boundaries

Virtual dependency capability does not affect:

- `PluginManager#getPlugin`;
- `PluginManager#getPlugins`;
- `PluginManager#isPluginEnabled`;
- `PluginEnableEvent` or `PluginDisableEvent`;
- plugin commands or descriptions.

No `Plugin`, `PluginDescriptionFile`, plugin.yml, command or lifecycle event is
fabricated.
