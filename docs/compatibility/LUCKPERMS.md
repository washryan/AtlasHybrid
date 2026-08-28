# LuckPerms 5.5.81 compatibility report

## Result

**BLOCKED** on AtlasHybrid `0.1.0-alpha` / Minecraft `1.19.2` / Forge `43.5.0`.

This is a research and raw-boot result, not a support claim. The official plugin
artifact was tested unchanged in the isolated, ignored `run-compat/` profile and
was not added to the repository.

## Target

- Official Bukkit artifact: LuckPerms `5.5.81`
- Source commit: `32494e9f0ab14857b63650ab68a65222d1924a93`
- License: MIT
- SHA-256: `27e0030113bad0efc09ef75818e73573f6bcec2b1cc72f64000bc42160113918`
- AtlasHybrid baseline: `e6704bbd47810891a5d71b1e2841db90b5b5b6d9`

Full provenance and dependency classification are in
[`LUCKPERMS_API_AUDIT.md`](LUCKPERMS_API_AUDIT.md).

## Raw boot, before implementation

The compatibility profile contained only the official LuckPerms JAR as an active
external plugin. Forge, Minecraft and AtlasHybrid reached normal server startup.
AtlasHybrid discovered one plugin candidate, then preserved this first failure:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.command.ConsoleCommandSender
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha
```

The original exception followed the structured diagnostic:

```text
java.lang.reflect.InvocationTargetException
Caused by: java.lang.NoClassDefFoundError: org/bukkit/command/ConsoleCommandSender
Caused by: java.lang.ClassNotFoundException: org.bukkit.command.ConsoleCommandSender
    at me.lucko.luckperms.common.loader.JarInJarClassLoader.instantiatePlugin(...)
    at me.lucko.luckperms.bukkit.loader.BukkitLoaderPlugin.<init>(...)
```

The summary was `discovered: 1`, `loaded: 0`, `unsupported: 1`. Both
`luckperms` and `lp` were unknown because registration was never reached. The
server then saved all three dimensions and stopped cleanly. The expected
compatibility failure is the only `ERROR`; no `FATAL` was emitted.

## Why no incremental compatibility patch was made

`ConsoleCommandSender` is only the first linker-visible gap. The pinned LuckPerms
source shows that its primary Bukkit behavior reflects into private
`SimplePluginManager` permission maps and injects permissibles into CraftBukkit
entity and command-sender implementation fields. Those are not optional command
or presentation features.

The Phase 9 gate requires stopping before CraftBukkit source, NMS/remapping,
Mixins, broad patches or invasive instrumentation. Implementing a few ordinary
types first would move the exception without proving correct permission behavior.
Accordingly, no Bukkit API, runtime behavior or LuckPerms-specific workaround was
added.

## Planned runtime checks and disposition

| Check | Result |
|---|---|
| Official artifact identity | PASS |
| Source/dependency audit before changes | PASS |
| Isolated raw discovery | PASS |
| Structured first-error diagnostic | PASS |
| LuckPerms construction/load | BLOCKED |
| `onLoad` / `onEnable` | BLOCKED |
| `/luckperms` / `/lp`, console and player | BLOCKED; commands not registered |
| Local H2 storage | NOT REACHED; default confirmed by source |
| Persistence/restart | NOT REACHED |
| Player permission behavior | BLOCKED by permission injection architecture |
| LuckPerms thread creation/cleanup | NOT REACHED; construction failed before scheduler creation completed |
| AtlasHybrid/Forge shutdown | PASS |
| Duplicate lifecycle/event calls | PASS for observed run: none occurred because plugin loaded zero times |

## Phase 9.1 architecture decision

LuckPerms is not abandoned. Its current status remains **BLOCKED** because the
permission architecture has been designed but not implemented or integrated.
This is an architecture gate, not a permanent incompatibility declaration.

[`ADR-006`](../architecture/ADR-006-PERMISSION-SYSTEM.md) selects an
AtlasHybrid-owned permission core composed into player and console adapters,
plus a generic provider hook. It rejects a fake CraftBukkit hierarchy and
plugin-specific transformation as the primary design.

The key conclusion is precise: the LuckPerms Bukkit platform replaces
`CraftHumanEntity#perm` so subsequent `Player#hasPermission` calls execute its
`LuckPermsPermissible`. AtlasHybrid can provide equivalent observable behavior
without changing `ServerPlayer`, but the unmodified Bukkit artifact needs an
adapter or upstream support to install through the Atlas hook.

The proposed Phase 9.2 is **Permission Core and Services**: public Bukkit
permission types, `AtlasPermissible`, defaults/children/attachments, permission
registration, permission-capable player/console senders, a lifecycle-clean
services manager, a versioned generic provider SPI with a neutral fixture, and
behavioral tests. It must not claim LuckPerms support or add a hidden LuckPerms
workaround. LuckPerms adapter integration is a later, separately reviewed phase.

## AtlasHybrid regression evidence

The research/documentation change does not alter runtime code. Two fully clean
executions of `gradlew clean test proofArtifacts` passed all `14/14` tests with
zero failures, errors or skips. The resulting SHA-256 values were identical:

| Artifact | Build A | Build B | Result |
|---|---|---|---|
| `atlashybrid-1.19.2-0.1.0-alpha.jar` | `cd48b271fc0e08fb060591536387b4eee227c48f212c3583cb52ff94e6f31c5c` | `cd48b271fc0e08fb060591536387b4eee227c48f212c3583cb52ff94e6f31c5c` | PASS |
| `AtlasHybridTestPlugin-0.1.0-alpha.jar` | `b2210acf340fd04fa9b5614990d71c0879f3b844abd8669b8c560ee83dc98cf1` | `b2210acf340fd04fa9b5614990d71c0879f3b844abd8669b8c560ee83dc98cf1` | PASS |
| `atlashybrid-test-mod-1.19.2-0.1.0-alpha.jar` | `dfbbb6cf13d0e25f2117c7f7ca1394d31b277b7ed09018c1580fd6833ee33c69` | `dfbbb6cf13d0e25f2117c7f7ca1394d31b277b7ed09018c1580fd6833ee33c69` | PASS |

The existing `runServer` integration proof also passed: Forge `43.5.0`, the
AtlasHybrid runtime, test mod and test plugin loaded; commands, join/quit,
location/teleport, real block-break cancellation, immediate/delayed scheduling,
the expected unsupported-API diagnostic, lifecycle counts and clean shutdown
all completed. No `ERROR` or `FATAL` appeared in that proof log.
