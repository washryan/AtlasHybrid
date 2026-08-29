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

## Phase 9.2 raw boot #2

The generic Permission Core is now implemented, including Bukkit permission
defaults and attachments, stable player/console composition, provider priority
and failure fallback, services priority/lookup, and plugin-owned lifecycle
cleanup. This code has no LuckPerms-specific branch.

The official, unmodified LuckPerms artifact was booted again. The former first
missing symbol, `org.bukkit.command.ConsoleCommandSender`, is no longer the
blocker. The new first failure is:

```text
Caused by: java.lang.IllegalStateException:
Plugin has not been initialized by AtlasHybrid
    at org.bukkit.plugin.java.JavaPlugin.getLogger(...)
    at me.lucko.luckperms.bukkit.LPBukkitBootstrap.<init>(...:104)
```

Classification: **ARCHITECTURAL**. More exact source review established that
`LPBukkitBootstrap` is not a second `JavaPlugin`; it is a jar-in-jar helper that
receives the outer `BukkitLoaderPlugin` and calls that main plugin's
`getLogger()` during its constructor. AtlasHybrid initialized the main instance
only after its constructor returned. The observed failure was not a missing
public API symbol and occurred before CraftBukkit permission injection.

Per the Phase 9.2 stop gate, AtlasHybrid does not implement that next loader
architecture here. LuckPerms remains **BLOCKED** while compatibility continues
to be developed; the implemented Permission Core is not a LuckPerms support
claim. The original stack trace remains present in the raw-boot log.

## Phase 9.3 raw boot #3

Phase 9.3 added a generic, classloader-owned bootstrap context. The official,
unchanged LuckPerms artifact passed the previous construction boundary:
`LPBukkitBootstrap` successfully obtained both the logger (line 104) and server
facade (line 110) from its outer `BukkitLoaderPlugin` during construction.

The next first failure is:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.command.TabExecutor
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

Caused by: java.lang.NoClassDefFoundError: org/bukkit/command/TabExecutor
    at me.lucko.luckperms.bukkit.LPBukkitBootstrap.<init>(LPBukkitBootstrap.java:110)
Caused by: java.lang.ClassNotFoundException: org.bukkit.command.TabExecutor
```

`LPBukkitBootstrap` constructs `LPBukkitPlugin` at line 110; defining that class
links Bukkit's `TabExecutor`. Classification: **CORE_API**. This is a normal
public Bukkit command interface, not CraftBukkit/NMS. Per the raw-boot stop gate,
it was recorded but not implemented, and no later dependency was pursued. The
summary was one discovered plugin, zero loaded and one unsupported; the server
then shut down cleanly.

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

## Phase 9.4 raw boot #4

Phase 9.4 implemented the generic Bukkit command tab-completion contract and its
Forge/Brigadier bridge. The official, unchanged LuckPerms artifact passed the
former `TabExecutor` linkage blocker, completed construction, entered
`BukkitLoaderPlugin.onLoad`, and logged `Loading configuration...`.

The next first failure is:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.configuration.file.YamlConfiguration#loadConfiguration(java.io.File)
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoSuchMethodError:
org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(java.io.File)
    at me.lucko.luckperms.bukkit.BukkitConfigAdapter.reload(BukkitConfigAdapter.java:51)
    at me.lucko.luckperms.bukkit.BukkitConfigAdapter.<init>(BukkitConfigAdapter.java:46)
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.provideConfigurationAdapter(LPBukkitPlugin.java:130)
    at me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin.load(AbstractLuckPermsPlugin.java:144)
    at me.lucko.luckperms.bukkit.LPBukkitBootstrap.onLoad(LPBukkitBootstrap.java:152)
```

Classification: **TRIVIAL**. AtlasHybrid already has the equivalent deterministic
`Path` overload; the missing symbol is the normal public Bukkit `File` overload,
not CraftBukkit, NMS, Paper, Mixin or instrumentation. Per the Phase 9.4 stop
gate, it is documented but not implemented. The raw boot summary was one plugin
discovered, zero loaded and one unsupported call. The expected compatibility
`ERROR` was preserved, there was no `FATAL`, and Forge saved all dimensions on
shutdown.

The Phase 9.4 regression suite passed `53/53` tests, real Brigadier completion
for player and console with `TAB_COMPLETION_OK` exactly once, WelcomeMessage and
WarpPlugin `FULL` regressions, Permission Core and ServicesManager checks, and
two byte-identical clean builds. LuckPerms remains **BLOCKED** and unsupported;
this later public API boundary does not remove the already documented
CraftBukkit permission-injection architecture gate.

## Phase 9.5 raw boot #5

Phase 9.5 added the generic nested Configuration API and safe UTF-8 YAML loader.
The unchanged LuckPerms artifact passed `YamlConfiguration#loadConfiguration(File)`,
read its real 37 KB configuration, completed `BukkitLoaderPlugin.onLoad`, and
was recorded as loaded. Enable then began and reached the Adventure Bukkit
audience/sender factory.

The next first failure is:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.Server#getOnlinePlayers()
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoSuchMethodError:
java.util.Collection org.bukkit.Server.getOnlinePlayers()
    at me.lucko.luckperms.lib.adventure.platform.bukkit.BukkitAudiencesImpl.<init>(BukkitAudiencesImpl.java:93)
    at me.lucko.luckperms.bukkit.BukkitSenderFactory$AdventurePlatformBukkitSenderFactory.<init>(BukkitSenderFactory.java:142)
    at me.lucko.luckperms.bukkit.BukkitSenderFactory.create(BukkitSenderFactory.java:50)
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.setupSenderFactory(LPBukkitPlugin.java:113)
    at me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin.enable(AbstractLuckPermsPlugin.java:155)
    at me.lucko.luckperms.bukkit.LPBukkitBootstrap.onEnable(LPBukkitBootstrap.java:177)
```

Classification: **CORE_API**. This is a normal public server/player API method,
not another Configuration method and not CraftBukkit, NMS, Paper or Mixin. Per
the Phase 9.5 stop gate it is documented but not implemented. The raw-boot
summary was one plugin discovered, one loaded and one unsupported call; enable
did not complete. The expected compatibility `ERROR` was preserved, there was
no `FATAL`, and all dimensions were saved during shutdown.

The phase passed `59/59` tests, the Forge integration proof with `YAML_LOAD_OK`
and `TAB_COMPLETION_OK` exactly once, WelcomeMessage and WarpPlugin `FULL`
regressions, Permission Core, ServicesManager, JavaPlugin bootstrap and Command
API checks, and two byte-identical clean builds. LuckPerms remains **BLOCKED**
and unsupported pending later explicitly scoped work.

## Phase 9.6 raw boot #6

Phase 9.6 added a generic session-backed online-player collection plus UUID and
exact-name lookup. The unchanged LuckPerms artifact passed the former
`Server#getOnlinePlayers()` boundary. Adventure successfully iterated the empty
startup snapshot and advanced from audience construction into event
registration.

The next first failure is:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.plugin.EventExecutor
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoClassDefFoundError: org/bukkit/plugin/EventExecutor
    at me.lucko.luckperms.lib.adventure.platform.bukkit.BukkitAudiencesImpl.registerEvent(BukkitAudiencesImpl.java:218)
    at me.lucko.luckperms.lib.adventure.platform.bukkit.BukkitAudiencesImpl.<init>(BukkitAudiencesImpl.java:97)
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.setupSenderFactory(LPBukkitPlugin.java:113)
    at me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin.enable(AbstractLuckPermsPlugin.java:155)
    at me.lucko.luckperms.bukkit.LPBukkitBootstrap.onEnable(LPBukkitBootstrap.java:177)
```

Classification: **CORE_API**. `EventExecutor` is a normal public Bukkit event
dispatch interface, not CraftBukkit, NMS, Paper or Mixin. Per the Phase 9.6 stop
gate it is documented but not implemented. Offline-player lookup was audited
and deferred because the observed startup path still did not invoke it.

The phase passes `68/68` tests. The normal Forge proof emits
`ONLINE_PLAYERS_OK` exactly once alongside all prior single-shot markers and
zero `ERROR/FATAL`. WelcomeMessage and WarpPlugin remain `FULL`, including the
complete Warp command/persistence probe. Clean builds A and B produce identical
runtime, test-plugin and test-mod artifacts. The expected raw compatibility
failure is one `ERROR` with no `FATAL`; Minecraft accepted `stop` and saved all
dimensions, although LuckPerms-created worker state after failed enable kept the
Gradle launch alive until the already-stopped process was interrupted.

LuckPerms remains **BLOCKED** and unsupported. No `EventExecutor`, OfflinePlayer
API, CraftBukkit shape or permission-injection workaround was added in this
phase.

## Phase 9.7 raw boot #7

Phase 9.7 added generic explicit event registration, deterministic priority
dispatch, cancelled-event policy and failed-enable rollback. The unchanged
LuckPerms artifact passed `BukkitAudiencesImpl.registerEvent`, completed load,
entered `onEnable` and advanced into Adventure's Bukkit component serializer.

The next first failure is:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.Material
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoClassDefFoundError: org/bukkit/Material
    at me.lucko.luckperms.lib.adventure.platform.bukkit.BukkitComponentSerializer.<clinit>(BukkitComponentSerializer.java:50)
    at me.lucko.luckperms.lib.adventure.platform.bukkit.BukkitFacet$Message.createMessage(BukkitFacet.java:76)
    at me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin.enable(AbstractLuckPermsPlugin.java:158)
    at me.lucko.luckperms.bukkit.LPBukkitBootstrap.onEnable(LPBukkitBootstrap.java:177)
```

Classification: **CORE_API**. `Material` is a normal public Bukkit type, but a
substantial registry-backed API rather than a safe one-symbol stub. Per the
stop gate it was recorded and not implemented; the known later CraftBukkit
permission-injection boundary remains unchanged.

Atomic failure handling called LuckPerms' `onDisable` immediately. Because
enable had not initialized its web-editor store, that callback threw an internal
`NullPointerException`; AtlasHybrid retained it as a suppressed exception and
still completed listener, task, service, provider, attachment and command
rollback. `FAILED_ENABLE_ROLLBACK_OK` appeared once. No new LuckPerms-owned live
thread was diagnosed after cleanup. Minecraft accepted `stop`, saved all
dimensions, and Gradle exited normally without forced interruption.

The Phase 9.7 proof also emits `EVENT_EXECUTOR_OK` exactly once. WelcomeMessage
and WarpPlugin remain `FULL`. LuckPerms remains **BLOCKED** and unsupported; no
LuckPerms-specific code, CraftBukkit shape, NMS, Mixin or instrumentation was
added.

## Phase 9.8 raw boot #8

Phase 9.8 added a real Java `Material` enum backed by the complete modern
vanilla block/item identifier union for Minecraft 1.19.2, plus `NamespacedKey`,
`Keyed` and the Bukkit-compatible `Block#getType(): Material` bridge. The
unchanged LuckPerms artifact found both `BLUE_ICE` and `NETHERITE_PICKAXE`, so
`BukkitComponentSerializer` passed its Material version checks.

The next first failure is:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.Bukkit#getUnsafe()
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoSuchMethodError:
'org.bukkit.UnsafeValues org.bukkit.Bukkit.getUnsafe()'
    at me.lucko.luckperms.lib.adventure.platform.bukkit.BukkitComponentSerializer.<clinit>(BukkitComponentSerializer.java:66)
    at me.lucko.luckperms.lib.adventure.platform.bukkit.BukkitFacet$Message.createMessage(BukkitFacet.java:76)
    at me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin.enable(AbstractLuckPermsPlugin.java:158)
```

Classification: **CORE_API**. The observed call needs
`UnsafeValues#getDataVersion()` so Adventure can select the Minecraft JSON
serializer data version. It is not an `ItemStack`, inventory, Bukkit Registry,
CraftBukkit, NMS or Mixin boundary, but `UnsafeValues` is a separate public API
surface and was not implemented after the stop gate.

Failed-enable cleanup again completed despite LuckPerms' suppressed partial
shutdown NPE. No attributable live thread remained. Minecraft accepted `stop`,
saved every dimension, and the Gradle launcher exited normally. LuckPerms
remains **BLOCKED** and unsupported because its later permission injection still
depends on CraftBukkit implementation shape.

## Phase 9.9 raw boot #9

Phase 9.9 added the safe public `UnsafeValues` data-version subset. Both
`Bukkit#getUnsafe()` and `Server#getUnsafe()` return the same immutable
`AtlasUnsafeValues` instance. Its `getDataVersion()` result is `3120`, sourced
from the official Minecraft 1.19.2 server `version.json` `world_version` field;
it is not protocol version `760` or pack format `9`/`10`.

The unchanged LuckPerms artifact passed
`BukkitComponentSerializer.<clinit>`, constructed its Gson component serializer,
printed its enable banner and advanced into platform-listener registration. The
next first failure is:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.Server#getOnlineMode()
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoSuchMethodError: 'boolean org.bukkit.Server.getOnlineMode()'
    at me.lucko.luckperms.bukkit.listeners.BukkitConnectionListener.<init>(BukkitConnectionListener.java:71)
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.registerPlatformListeners(LPBukkitPlugin.java:135)
    at me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin.enable(AbstractLuckPermsPlugin.java:194)
    at me.lucko.luckperms.bukkit.LPBukkitBootstrap.onEnable(LPBukkitBootstrap.java:177)
    at me.lucko.luckperms.bukkit.loader.BukkitLoaderPlugin.onEnable(BukkitLoaderPlugin.java:50)
```

Classification: **CORE_API**. `Server#getOnlineMode()` is ordinary public
server-state API and can be backed by the Forge dedicated-server property; it
does not itself require CraftBukkit or NMS. The exact LuckPerms bytecode stores
the result, and only when it is false combines it with a regex match against a
CraftBukkit-style server version to enable a CraftBukkit offline-mode warning
and login guard. Per the phase stop gate it was recorded but not implemented.

Best-effort disable again threw a suppressed LuckPerms partial-initialization
`NullPointerException`, and AtlasHybrid rolled back its registrations. This
later enable boundary had already started `OkHttp metadata.luckperms.net` and
its writer thread; both remained alive after rollback. Minecraft accepted
`stop` and saved all dimensions, but those two LuckPerms-owned threads kept the
Gradle launcher alive until the test process was interrupted. This is recorded
as raw-boot behavior, not hidden as a clean shutdown.

LuckPerms remains **BLOCKED** and unsupported. No `getOnlineMode`, connection
events, ItemStack, Registry, CraftBukkit, NMS, Mixin or LuckPerms-specific code
was added after the blocker.

## Phase 9.10 raw boot #10

Phase 9.10 implements the public server identity contract with
`MinecraftServer#usesAuthentication()` as the runtime source of truth.
`Server#getOnlineMode()` and `Bukkit#getOnlineMode()` therefore agree with the
live dedicated server rather than reparsing `server.properties` or returning a
constant. The unchanged LuckPerms artifact passed
`BukkitConnectionListener.<init>` and advanced to registration of its platform
listeners.

The next first failure is:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.event.player.AsyncPlayerPreLoginEvent
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoClassDefFoundError: org/bukkit/event/player/AsyncPlayerPreLoginEvent
    at java.lang.Class.getDeclaredMethods0(Native Method)
    at dev.atlashybrid.runtime.event.AtlasPluginManager.registerEvents(AtlasPluginManager.java:57)
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.registerPlatformListeners(LPBukkitPlugin.java:136)
    at me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin.enable(AbstractLuckPermsPlugin.java:194)
```

Classification: **CORE_API**. `AsyncPlayerPreLoginEvent` is a public Bukkit
connection event, not a CraftBukkit, NMS, Paper or Mixin symbol. Implementing
its event class and correct asynchronous login lifecycle is a separate event
compatibility phase, so the raw boot stopped at this boundary without adding a
stub or cascading into later listeners.

Best-effort disable again reached LuckPerms' partial-state
`extensionManager.close()` null dereference, which AtlasHybrid retained as a
suppressed exception before completing its own rollback. Unlike raw boot #9,
no new live thread survived rollback long enough to be reported by the generic
resource monitor: the log contains no `AtlasHybrid Plugin Resource` block and
no OkHttp thread. Normal `stop` saved all dimensions and the Gradle launcher
exited by itself. This run therefore does not claim a leak or manufacture an
ownership result from thread names alone.

LuckPerms remains **BLOCKED** overall because its known later permission
injection relies on CraftBukkit implementation shape. The raw boot result is
not a support claim.

## Phase 9.11 raw boot #11

Phase 9.11 implements the exact public `AsyncPlayerPreLoginEvent` result and
data contract and dispatches it during Forge negotiation on a real worker
thread. A controlled protocol-760 proof verified deterministic offline UUID,
real loopback address, allow-after-disallow, rejection before session creation,
successful allow/join/quit, and clean session removal. `ASYNC_PRELOGIN_OK` and
`PRELOGIN_DENY_OK` each appeared once. The full suite passed `93/93`; the
integration proof, WelcomeMessage and WarpPlugin regressions passed; and both
clean builds produced identical runtime, test-plugin, and test-mod JARs.

The unchanged LuckPerms artifact crossed the former reflection boundary,
completed construction of `BukkitConnectionListener`, printed its enable
banner, and began registering that listener. Reflection then reached the next
declared handler type:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.event.player.PlayerLoginEvent
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoClassDefFoundError: org/bukkit/event/player/PlayerLoginEvent
    at java.lang.Class.getDeclaredMethods0(Native Method)
    at dev.atlashybrid.runtime.event.AtlasPluginManager.registerEvents(AtlasPluginManager.java:57)
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.registerPlatformListeners(LPBukkitPlugin.java:136)
    at me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin.enable(AbstractLuckPermsPlugin.java:194)
```

Classification: **CORE_API**. LuckPerms declares synchronous `PlayerLoginEvent`
handlers at `LOWEST` and `MONITOR`; they validate loaded state, inject/check its
permissible, update context, and can disallow login. This is a separate player
creation/login stage and was not implemented after the stop gate.

Best-effort disable again recorded the known suppressed
`extensionManager.close()` partial-state null dereference, after which
AtlasHybrid completed rollback with zero permission providers and services.
Before stop, a JVM thread dump showed nine daemon threads named
`luckperms-worker-0` through `luckperms-worker-8`, all parked in the same
ForkJoinPool; the explicit names and stacks give high ownership confidence,
but daemon state means they did not prevent exit. Normal `stop` saved all
dimensions, Gradle exited `0` with `BUILD SUCCESSFUL`, and no Forge server Java
process remained. LuckPerms remains **BLOCKED** and unsupported.
