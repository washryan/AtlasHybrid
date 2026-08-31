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

## Phase 9.12A raw boot #12

Phase 9.12A adds the public synchronous `PlayerLoginEvent` contract and a
version-pinned pre-placement gate. A real protocol-760 proof verified
`AsyncPlayerPreLoginEvent -> PlayerLoginEvent -> PlayerJoinEvent`, stable
CONNECTING-to-ONLINE adapter identity, hostname/address data, and a real
login-stage denial with the requested disconnect reason and no join, online
entry, world placement or session residue. Both new markers occurred once. The
suite passed `101/101`; integration, WelcomeMessage, WarpPlugin and both
reproducible builds passed.

The unchanged LuckPerms 5.5.81 artifact resolved both `PlayerLoginEvent`
handlers and completed registration of `BukkitConnectionListener`. The next
`registerPlatformListeners` call attempted to reflect
`BukkitPlatformListener` and failed at:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.event.server.ServerCommandEvent
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoClassDefFoundError: org/bukkit/event/server/ServerCommandEvent
    at java.lang.Class.getDeclaredMethods0(Native Method)
    at dev.atlashybrid.runtime.event.AtlasPluginManager.registerEvents(AtlasPluginManager.java:57)
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.registerPlatformListeners(LPBukkitPlugin.java:137)
```

Classification: **CORE_API**. `ServerCommandEvent` is a public Bukkit event
type. Correct command-pipeline dispatch is a separate phase; it was not stubbed
or implemented here. The overall `registerPlatformListeners` method and
LuckPerms `onEnable` did not complete.

Best-effort disable again hit the known suppressed partial-initialization null
dereference. AtlasHybrid rolled back to zero permission providers/services.
After normal Minecraft stop saved all dimensions, a thread dump showed eight
daemon `luckperms-worker-*` threads, daemon Okio/OkHttp threads, and the
non-daemon `OkHttp metadata.luckperms.net Writer`; the latter kept the launcher
alive, so it was interrupted after the server had stopped. LuckPerms remains
**BLOCKED** and unsupported.

## Phase 9.13 raw boot #13

Phase 9.13 implements the exact public `ServerCommandEvent` and
`RemoteServerCommandEvent` contracts. The production bridge uses Forge's
pre-execution command event: local console input receives the stable AtlasHybrid
console sender, real vanilla RCON input receives a remote-console sender,
mutation replaces the parse result, and cancellation prevents execution. The
dedicated-server proof exercised both paths and emitted
`SERVER_COMMAND_EVENT_OK` and `REMOTE_SERVER_COMMAND_EVENT_OK` exactly once.

The unchanged LuckPerms artifact no longer fails on either server command event
type. Reflection of the same `BukkitPlatformListener` then stopped at the next
declared handler type:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.event.player.PlayerCommandPreprocessEvent
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoClassDefFoundError:
org/bukkit/event/player/PlayerCommandPreprocessEvent
    at java.lang.Class.getDeclaredMethods0(Native Method)
    at dev.atlashybrid.runtime.event.AtlasPluginManager.registerEvents(AtlasPluginManager.java:57)
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.registerPlatformListeners(LPBukkitPlugin.java:137)
```

Classification: **CORE_API**. This is Bukkit's synchronous, cancellable,
mutable player-command preprocessing event. It was audited but is outside the
server/RCON event implementation and was not added after the stop gate.
`BukkitConnectionListener` registration still passes; registration of
`BukkitPlatformListener`, the overall `registerPlatformListeners` method, and
LuckPerms `onEnable` remain incomplete. `LUCKPERMS_PLATFORM_LISTENERS_REGISTERED`
and `LUCKPERMS_ENABLE_REACHED` were therefore not recorded.

Before stop, the dump contained seven daemon `luckperms-worker-*` threads, four
OkHttp threads, one daemon Okio watchdog, and one non-daemon
`OkHttp metadata.luckperms.net Writer`. Normal `stop` saved all dimensions, but
those same threads remained and the writer retained the JVM. The launcher was
interrupted only after Minecraft had stopped; afterward no compatibility Java
process remained. The regression suite passed `106/106`, integration and the
WelcomeMessage/WarpPlugin probes passed, and both clean builds produced
byte-identical runtime, test-plugin and test-mod JARs. LuckPerms remains
**BLOCKED** and unsupported.

## Phase 9.14 raw boot #14

Phase 9.14 implements the public synchronous `PlayerCommandPreprocessEvent`
contract and connects it to the existing Forge pre-execution command event for
Minecraft player sources. The event exposes the leading slash, stable session
player, mutable message, cancellation, recipients, and normal priority and
exception behavior. Mutation reparses once and cancellation halts execution.
The dedicated proof emitted `PLAYER_COMMAND_PREPROCESS_OK` and
`PLAYER_COMMAND_PREPROCESS_CANCEL_OK` exactly once.

The unchanged LuckPerms artifact passed the former
`PlayerCommandPreprocessEvent` reflection boundary. Reflection of
`BukkitPlatformListener` then reached the next declared handler type:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.event.server.PluginEnableEvent
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoClassDefFoundError: org/bukkit/event/server/PluginEnableEvent
    at java.lang.Class.getDeclaredMethods0(Native Method)
    at dev.atlashybrid.runtime.event.AtlasPluginManager.registerEvents(AtlasPluginManager.java:57)
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.registerPlatformListeners(LPBukkitPlugin.java:137)
```

Classification: **CORE_API**. `PluginEnableEvent` is the ordinary synchronous,
non-cancellable Bukkit lifecycle event. It was fully audited but intentionally
not implemented in this phase. Consequently `BukkitPlatformListener`, the
overall `registerPlatformListeners` method, and LuckPerms `onEnable` remain
incomplete; neither `LUCKPERMS_PLATFORM_LISTENERS_REGISTERED` nor
`LUCKPERMS_ENABLE_REACHED` was recorded.

Before and after normal Minecraft stop, the process contained six daemon
`luckperms-worker-*` threads, four OkHttp threads, one daemon Okio watchdog, and
one non-daemon `OkHttp metadata.luckperms.net Writer`. All dimensions saved
cleanly, but the writer retained the JVM; the launcher was interrupted after
shutdown, leaving no compatibility server process. The suite passed `110/110`,
the complete integration/WelcomeMessage/WarpPlugin regressions passed, and two
clean builds produced byte-identical runtime, test-plugin and test-mod JARs.
LuckPerms remains **BLOCKED** and unsupported.

## Phase 9.15 raw boot #15

Phase 9.15 adds the generic synchronous, non-cancellable Bukkit plugin
lifecycle events. `PluginEnableEvent` is published exactly once after a
successful `onEnable`; `PluginDisableEvent` is published exactly once before
`onDisable` and resource cleanup. Failed enable attempts publish neither event.
The controlled Forge proof emitted `PLUGIN_ENABLE_EVENT_OK` and
`PLUGIN_DISABLE_EVENT_OK` exactly once while the failed-enable rollback and all
previous integration markers remained intact.

The unchanged LuckPerms artifact crossed the former lifecycle reflection
boundary. Both `BukkitConnectionListener` and `BukkitPlatformListener`
registered, so raw boot #15 records:

```text
LUCKPERMS_PLATFORM_LISTENERS_REGISTERED
```

`registerPlatformListeners` completed. LuckPerms then initialized its H2
storage and reached command registration. Reflection of
`BukkitCommandExecutor` stopped at the next public API type:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.entity.Entity
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoClassDefFoundError: org/bukkit/entity/Entity
    at java.lang.Class.getDeclaredMethods0(Native Method)
    at dev.atlashybrid.runtime.event.AtlasPluginManager.registerEvents(AtlasPluginManager.java:57)
    at me.lucko.luckperms.bukkit.BukkitCommandExecutor.register(BukkitCommandExecutor.java:75)
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.registerCommands(LPBukkitPlugin.java:159)
```

Classification: **CORE_API**. `Entity` is the public Bukkit base entity
contract. In this path it is linked by compiler-generated selector handling in
`BukkitCommandExecutor` while `registerEvents` reflects declared methods; it is
not evidence that entity selection executed. No stub or cascade fix was added.
LuckPerms `onEnable` did not complete, so `LUCKPERMS_ENABLE_REACHED` was not
recorded and no functional LuckPerms test was started.

Before stop the JVM contained ten daemon `luckperms-worker-*` threads, four
OkHttp threads (three daemon and one non-daemon metadata writer), one daemon
Okio watchdog, and one daemon H2 MVStore writer. Normal `stop` saved all
dimensions. After stop, nine daemon LuckPerms workers, three OkHttp threads
including the non-daemon writer, and the H2 writer remained; the Okio watchdog
had exited. The non-daemon writer retained the JVM. The Gradle launcher was
interrupted after this diagnosis, and no compatibility server process remained.

The full suite passed `113/113`, integration and the
WelcomeMessage/WarpPlugin regressions passed, and two clean builds produced
byte-identical runtime, test-plugin, and test-mod JARs. LuckPerms remains
**BLOCKED** and unsupported.

## Phase 9.16 raw boot #16

Phase 9.16 adds a connected `Entity -> LivingEntity -> HumanEntity -> Player`
foundation. The existing player session adapter now exposes the Minecraft UUID
and runtime entity ID plus its live Location and stable World adapter. No
second wrapper, generic entity registry, `EntityType` subset, Mixin, NMS hook,
or CraftBukkit facade was added. The complete scope audit is in
[`ENTITY_API.md`](../architecture/ENTITY_API.md).

The unchanged LuckPerms artifact crossed the former `Entity` reflection
boundary. `BukkitCommandExecutor` registered successfully, so command
registration completed. Startup then reached `setupContextManager`, where
loading `BukkitPlayerCalculator` stopped at:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.GameMode
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoClassDefFoundError: org/bukkit/GameMode
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.setupContextManager(LPBukkitPlugin.java:189)
    at me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin.enable(AbstractLuckPermsPlugin.java:233)
```

`BukkitPlayerCalculator` uses `GameMode.class`, `GameMode.values()`,
`Player#getGameMode`, and `PlayerGameModeChangeEvent`; it also has later World
environment/list APIs. This is a new **CORE_API** boundary, not an Entity
subtype and not CraftBukkit/NMS. Per the stop gate it was documented without a
cascade implementation. LuckPerms `onEnable` remains incomplete and
`LUCKPERMS_ENABLE_REACHED` was not recorded.

Before stop there were six daemon `luckperms-worker-*` threads, four OkHttp
threads (one non-daemon metadata writer), one daemon Okio watchdog, and one
daemon H2 MVStore writer. Normal `stop` saved all dimensions. After stop there
were five workers, three OkHttp threads including the non-daemon writer, zero
Okio threads, and the H2 writer. The retained writer kept the JVM alive, so the
launcher was interrupted after Minecraft shutdown; no compatibility server
process remained.

The suite passed `114/114`; integration, WelcomeMessage and WarpPlugin passed;
`ENTITY_API_OK` appeared exactly once; and two clean builds produced
byte-identical runtime, test-plugin and test-mod JARs. LuckPerms remains
**BLOCKED** and unsupported.

## Phase 9.17 raw boot #17

Phase 9.17 adds the exact Bukkit 1.19.2 `GameMode` enum contract and a
thread-safe snapshot of each real Minecraft player's `GameType`. Mapping is an
explicit switch, not an ordinal conversion; all four modes were exercised on a
real `ServerPlayer`. The setter and the wider player-context surface remain
deferred. See [`GAMEMODE_API.md`](../architecture/GAMEMODE_API.md).

The unchanged LuckPerms artifact crossed the former `GameMode` boundary and
entered `BukkitPlayerCalculator` initialization. Raw boot #17 then stopped at
the next public context type:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.World$Environment
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoClassDefFoundError: org/bukkit/World$Environment
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.setupContextManager(LPBukkitPlugin.java:189)
    at me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin.enable(AbstractLuckPermsPlugin.java:233)
```

This is a new **CORE_API** boundary. LuckPerms uses the enum together with
`World#getEnvironment()` when building player contexts. No world-environment
stub, change-event cascade, CraftBukkit facade or plugin-specific workaround
was added. LuckPerms `onEnable` remains incomplete and
`LUCKPERMS_ENABLE_REACHED` was not recorded. WarpPlugin and WelcomeMessage still
enabled normally after the failed-enable rollback.

Before stop, the JVM had eight daemon `luckperms-worker-*` threads, four OkHttp
threads, one daemon Okio watchdog and one daemon H2 MVStore writer. Normal
Minecraft stop saved every dimension. After stop, seven workers, three OkHttp
threads and the H2 writer remained; the watchdog exited. The non-daemon OkHttp
metadata writer retained the JVM, so the launcher was interrupted only after
Minecraft shutdown; no server process remained.

The suite passed `116/116`; integration, WelcomeMessage and WarpPlugin passed;
`GAMEMODE_API_OK` appeared exactly once; and two clean builds produced
byte-identical runtime, test-plugin and test-mod JARs. LuckPerms remains
**BLOCKED** and unsupported.

## Phase 9.18 raw boot #18

Phase 9.18 adds the exact Bukkit 1.19.2 `World.Environment` enum, including
`CUSTOM`, and exposes each stable World adapter's environment from its real
namespaced dimension key. Vanilla Overworld, Nether and End map explicitly;
every other Forge dimension maps to `CUSTOM`. Name and environment are
immutable adapter context while `Player#getWorld()` resolves the player's
current live level. See
[`WORLD_ENVIRONMENT_API.md`](../architecture/WORLD_ENVIRONMENT_API.md).

The unchanged LuckPerms artifact crossed the former environment boundary and
advanced one line further into `setupContextManager`, where listener reflection
for the now-constructible `BukkitPlayerCalculator` stopped at:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.event.player.PlayerChangedWorldEvent
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoClassDefFoundError: org/bukkit/event/player/PlayerChangedWorldEvent
    at java.lang.Class.getDeclaredMethods0(Native Method)
    at dev.atlashybrid.runtime.event.AtlasPluginManager.registerEvents(AtlasPluginManager.java:57)
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.setupContextManager(LPBukkitPlugin.java:190)
```

This is the synchronous Bukkit world-transition invalidation event and a new
**CORE_API** boundary. No event stub, dimension-transition hook,
`Server#getWorlds()` cascade or plugin-specific workaround was added. Command
registration remained complete and H2 initialized before the failure, but the
partial enable did not shut all resources down. LuckPerms `onEnable` remains
incomplete and `LUCKPERMS_ENABLE_REACHED` was not recorded. WarpPlugin and
WelcomeMessage enabled normally after rollback.

Before stop, the JVM had eight daemon `luckperms-worker-*` threads, four OkHttp
threads, one daemon Okio watchdog and one daemon H2 MVStore writer. Normal
Minecraft stop saved every dimension. After stop, seven workers, three OkHttp
threads, the Okio watchdog and the H2 writer remained. The non-daemon OkHttp
metadata writer retained the JVM, so the launcher was interrupted only after
Minecraft shutdown; no server process remained.

The suite passed `118/118`; integration, WelcomeMessage and WarpPlugin passed;
`WORLD_ENVIRONMENT_OK` appeared exactly once; and two clean builds produced
byte-identical runtime, test-plugin and test-mod JARs. LuckPerms remains
**BLOCKED** and unsupported.

## Phase 9.19 raw boot #19

Phase 9.19 adds the exact synchronous, non-cancellable Bukkit 1.19.2
`PlayerChangedWorldEvent` and bridges Forge's public post-transition
`PlayerChangedDimensionEvent`. A controlled real Overworld-to-Nether transfer
proved the previous world, destination-visible player context, stable adapter,
permission attachment and one-only dispatch. Same-world WarpPlugin teleport did
not produce a false transition. See
[`PLAYER_CHANGED_WORLD_EVENT_API.md`](../architecture/PLAYER_CHANGED_WORLD_EVENT_API.md).

The unchanged official LuckPerms artifact crossed the former listener symbol
and stopped at the next declared method of the same calculator:

```text
[AtlasHybrid Compatibility]
Plugin: LuckPerms
Missing API: org.bukkit.event.player.PlayerGameModeChangeEvent
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha

java.lang.NoClassDefFoundError: org/bukkit/event/player/PlayerGameModeChangeEvent
    at java.lang.Class.getDeclaredMethods0(Native Method)
    at dev.atlashybrid.runtime.event.AtlasPluginManager.registerEvents(AtlasPluginManager.java:57)
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.setupContextManager(LPBukkitPlugin.java:190)
```

This is the next **CORE_API** context invalidation boundary. The calculator's
world-change handler conditionally calls
`BukkitContextManager#signalContextUpdate(event.getPlayer())` when world or
dimension-type contexts are enabled; recalculation then reads the already
consistent `Player#getGameMode()`, `Player#getWorld()`, environment and world
name. No additional API was implemented in cascade.

Command registration remained complete and H2/storage initialized. Overall
`setupContextManager` and LuckPerms `onEnable` remain incomplete, so
`LUCKPERMS_ENABLE_REACHED` was not recorded and functional LuckPerms testing did
not begin. The pre-stop dump contained eight daemon `luckperms-worker-*`
threads, four OkHttp threads, one daemon Okio watchdog and one daemon MVStore
writer. Minecraft `stop` saved all dimensions. The post-stop dump retained the
same counts and no Server thread; the non-daemon OkHttp metadata writer kept the
JVM alive, so the launcher was interrupted only after server shutdown. No
Forge server process remained.

Final regression was `120/120`; integration, WelcomeMessage and WarpPlugin
passed; `PLAYER_CHANGED_WORLD_OK` appeared exactly once; and clean builds A/B
produced byte-identical runtime, test-plugin and test-mod JARs.

## Phase 9.20 raw boot #20

Phase 9.20 adds the exact cancellable Bukkit 1.19.2
`PlayerGameModeChangeEvent` and bridges Forge's public pre-transition
`PlayerChangeGameModeEvent`. Allowed changes update the real player and the
existing adapter snapshot exactly once; cancellation preserves both old
states. `Player#setGameMode` enters the same pipeline. See
[`PLAYER_GAMEMODE_CHANGE_EVENT_API.md`](../architecture/PLAYER_GAMEMODE_CHANGE_EVENT_API.md).

LuckPerms' `BukkitPlayerCalculator` registers its `MONITOR`,
`ignoreCancelled=true` handler. Allowed changes signal a player-context update;
cancelled changes are ignored. Raw boot #20 crossed listener reflection and
completed context-manager registration. Storage/H2 and commands remained
healthy. The next failure occurred later in `setupPlatformHooks`:

```text
java.lang.NoClassDefFoundError: org/bukkit/plugin/SimplePluginManager
    at me.lucko.luckperms.bukkit.inject.server.InjectorSubscriptionMap.<clinit>(InjectorSubscriptionMap.java:46)
    at me.lucko.luckperms.bukkit.LPBukkitPlugin.setupPlatformHooks(LPBukkitPlugin.java:197)
```

`LUCKPERMS_CONTEXT_MANAGER_REGISTERED` is therefore reached, while
`LUCKPERMS_ENABLE_REACHED` is not. This is an **ARCHITECTURAL**
CraftBukkit/plugin-manager injection boundary, not a permanent incompatibility
classification. No `SimplePluginManager` shape, private reflection workaround,
permission injection or plugin-specific runtime code was added.

Before stop, the failed enable reported one daemon LuckPerms worker, three
OkHttp threads including one non-daemon metadata writer, one daemon Okio
watchdog and one daemon H2 MVStore writer. Normal Minecraft stop saved every
dimension and removed the Server thread. Plugin resources remained because the
partial LuckPerms shutdown was incomplete; the non-daemon OkHttp writer retained
the JVM. The launcher was interrupted only after server shutdown, and no Forge
server process remained.

## Phase 9.21 architectural decision

The full 5.5.81 source audit confirms that context manager, listeners, commands
and storage are complete before the blocker. `setupPlatformHooks` then replaces
the private `SimplePluginManager` fields `permSubs`, `permissions` and
`defaultPerms`; a separate optional workaround touches `dependencyGraph`.
Player login later replaces private `CraftHumanEntity#perm`. The stock Bukkit
backend has no supported configuration flag that disables these hooks while
preserving permission correctness, and injection failure has no Player fallback.

The official API provides the correct contextual query through
`LuckPerms#getPlayerAdapter(Player.class)`, `getPermissionData(player)` and
`checkPermission(node)`. AtlasHybrid's existing `PermissionProvider` can carry
that result without replacing Player state. However, the LuckPerms API singleton
and Bukkit service are both published only after `setupPlatformHooks`, producing
a bootstrap chicken-and-egg for the unchanged 5.5.81 Bukkit artifact.

Decision: reject a fake `SimplePluginManager`, private-field emulation,
CraftPlayer/CraftBukkit shapes, reflection bypasses and instrumentation. An
isolated public-API LuckPerms provider adapter is acceptable only with a
supported bootstrap/platform cooperation point. Details and Phase 9.22 proposal
are in
[`ADR-009-LUCKPERMS-PERMISSION-BRIDGE.md`](../architecture/ADR-009-LUCKPERMS-PERMISSION-BRIDGE.md).
Status remains **BLOCKED — ARCHITECTURAL DECISION**, not permanently
incompatible.

## Phase 9.22 supported bootstrap result

The prototype gate is **NO** for the unchanged Bukkit 5.5.81 JAR. The audited
backend has no official injector strategy, factory, service-provider hook,
capability branch or configuration mode for an external permission authority.
`LuckPermsProvider`, Bukkit `ServicesManager` publication and the extension
manager all occur after the failing `setupPlatformHooks` call. Extensions are
therefore too late, and a post-enable public API adapter cannot bootstrap.

Other official LuckPerms artifacts validate that permission interception is a
platform responsibility, but they are complete platform implementations rather
than pluggable Bukkit backends. Forge 1.19.2 existed in the 5.4 source history;
the exact 5.5.81 Forge module targets Minecraft 26.2 and cannot be installed as
the tested AtlasHybrid 1.19.2 target. Building a custom AtlasHybrid platform
module would mean maintaining LuckPerms implementation internals and a separate
distribution, not merely compiling against the public API.

No `compat-luckperms` code was created because the service it would consume is
never published. The recommended path is an upstream-supported Bukkit
permission-platform contract, followed by a small public-API Player provider.
The proposal is in
[`LUCKPERMS_UPSTREAM_INTEGRATION_PROPOSAL.md`](../architecture/LUCKPERMS_UPSTREAM_INTEGRATION_PROPOSAL.md).
Status remains **BLOCKED — ARCHITECTURAL DECISION**.

## Phase 9.24A Forge feasibility result

The Bukkit `5.5.81` result above is unchanged: it remains **BLOCKED** at the
CraftBukkit-specific platform hook. A separate experiment tested the official,
unchanged LuckPerms Forge `5.4.46` artifact for Minecraft 1.19.2.

On the official Forge `43.5.0` production server it completed enable, selected
`luckperms:permission_handler`, initialized H2, registered `/lp` and
`/luckperms`, loaded a real protocol player with world/dimension/game-mode
contexts, exposed the public API across Atlas' plugin classloader, completed
`UNDEFINED -> TRUE -> FALSE -> UNDEFINED`, and shut down without retaining the
JVM. This is **RESEARCH / POC**, not `PARTIAL` or `FULL`, because no
`AtlasPermissible` provider bridge exists yet.

See [`LUCKPERMS_FORGE_1_19_2.md`](LUCKPERMS_FORGE_1_19_2.md) and
[`LUCKPERMS_FORGE_BRIDGE_FEASIBILITY.md`](../architecture/LUCKPERMS_FORGE_BRIDGE_FEASIBILITY.md).

## Phase 9.24B Forge permission bridge result

The Bukkit `5.5.81` status remains **BLOCKED**. Separately, AtlasHybrid now has
a `PARTIAL / PASS` bridge for the official LuckPerms Forge `5.4.46` backend.
Using only `net.luckperms.api`, it delegates online Bukkit Player permission
queries by UUID and live `QueryOptions`; TRUE and FALSE are authoritative, while
UNDEFINED returns to Atlas defaults. A real Forge server proof covered negative
nodes, removal/fallback, survival/creative context changes, reconnect,
`reloadconfig` and clean unregistration/shutdown.

No Bukkit LuckPerms service, plugin identity, hard dependency, Vault adapter,
private reflection, CraftBukkit type, Mixin or external JAR was added. Full
scope and limitations are recorded in
[`LUCKPERMS_FORGE_PERMISSION_BRIDGE.md`](../architecture/LUCKPERMS_FORGE_PERMISSION_BRIDGE.md).
