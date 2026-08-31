# LuckPerms Forge virtual dependency

## Readiness gate

The `LuckPerms` capability becomes available only after all four conditions are
true:

1. Forge reports the official LuckPerms mod;
2. `LuckPermsProvider#get()` returns the public API;
3. the Atlas Player permission provider is bound;
4. the same API instance is registered in Bukkit `ServicesManager`.

Only then does Atlas register:

```text
name=LuckPerms
state=AVAILABLE
owner=AtlasHybridCompatibility
version=5.4.46
```

If API discovery, permission binding or service registration fails, cleanup
removes partial bindings and the capability remains unavailable.

## Startup order

LuckPerms Forge completes its server bootstrap before Forge emits the
`ServerStartingEvent` used by the Atlas Bukkit loader. Atlas now refreshes the
bridge at the beginning of that event, then resolves and loads Bukkit plugins.
The proven order is:

```text
LuckPerms Forge enabled
  -> public API discovered
  -> Atlas permission provider bound
  -> Bukkit service registered
  -> LuckPerms capability AVAILABLE
  -> depend: [LuckPerms] resolved
  -> dependent plugin constructed and onLoad called
  -> dependent plugin onEnable called
```

The internal `LuckPermsDependentProbe` obtains the API exclusively through
`ServicesManager` during `onEnable` and verifies UserManager availability.
Its API dependency is `compileOnly`; its JAR contains no LuckPerms API classes.

## Positive and negative behavior

With the original LuckPerms Forge 5.4.46 artifact, the production proof logs:

- `LUCKPERMS_VIRTUAL_DEPENDENCY_AVAILABLE`;
- `VIRTUAL_DEPENDENCY_RESOLVED plugin=LuckPermsDependentProbe dependency=LuckPerms`;
- `LUCKPERMS_DEPENDENT_PLUGIN_ENABLE_PASS`.

Without LuckPerms Forge, no API, service or capability exists. The same probe is
rejected with the normal missing hard dependency error and is never loaded or
enabled.

`lp reloadconfig` retains one capability because the public API/service identity
does not change. Shutdown unregisters the capability before removing the
service/provider and before LuckPerms closes storage. Restart registers one
fresh capability.

## Not a Bukkit LuckPerms plugin

Even after dependency resolution:

- `PluginManager#getPlugin("LuckPerms") == null`;
- `PluginManager#isPluginEnabled("LuckPerms") == false`;
- `PluginManager#getPlugins()` contains no LuckPerms entry;
- no LuckPerms `PluginEnableEvent` or `PluginDisableEvent` is emitted;
- no fake description, commands or plugin class exists.

Plugins requiring a concrete `LPBukkitPlugin`, Bukkit PlayerAdapter, Vault, or
other Bukkit implementation identity remain unsupported. Overall integration
status therefore remains **PARTIAL / PASS**.

## Phase validation

- automated tests: 142/142 PASS;
- standard Atlas integration proof: PASS;
- positive and negative virtual-dependency production proofs: PASS;
- reload, restart and shutdown cleanup: PASS;
- reproducible Build A/B output: PASS for runtime, test plugin, test mod and
  dependent probe;
- dependent probe JAR LuckPerms API entries: zero.
