# LuckPerms Bukkit API audit

Audit date: 2026-08-28. This is a source-first compatibility assessment against
AtlasHybrid `0.1.0-alpha` on Minecraft `1.19.2` / Forge `43.5.0` / Java 17.
No LuckPerms artifact or source is incorporated into AtlasHybrid.

## Pinned target and provenance

- Project: [LuckPerms](https://github.com/LuckPerms/LuckPerms)
- Platform artifact: Bukkit loader
- Version: `5.5.81`
- Source revision: [`32494e9f0ab14857b63650ab68a65222d1924a93`](https://github.com/LuckPerms/LuckPerms/tree/32494e9f0ab14857b63650ab68a65222d1924a93)
- Per-patch tag: none published; the exact commit above is the fixed source revision
- License: [MIT](https://github.com/LuckPerms/LuckPerms/blob/32494e9f0ab14857b63650ab68a65222d1924a93/LICENSE.txt)
- Official download: `https://download.luckperms.net/1668/bukkit/loader/LuckPerms-Bukkit-5.5.81.jar`
- Artifact size: `1,502,991` bytes
- SHA-256: `27e0030113bad0efc09ef75818e73573f6bcec2b1cc72f64000bc42160113918`
- Compatibility basis: the official Bukkit download page includes Minecraft
  `1.19.2` in its supported Bukkit-family version range.

The JAR declares `load: STARTUP`, `api-version: 1.13`, `main:
me.lucko.luckperms.bukkit.loader.BukkitLoaderPlugin`, command `luckperms` with
aliases `lp`, `perm`, `perms`, `permission`, and `permissions`, and optional
integration with Vault, LilyPad-Connect, and ViaVersion. It is a loader JAR with
the Bukkit implementation in `luckperms-bukkit.jarinjar`.

## Classification method

`SUPPORTED` means the required type and meaningful behavior already exist in
AtlasHybrid. `MISSING` means a normal Bukkit API can be implemented generically
but does not exist. `UNKNOWN` means runtime behavior would need a successful boot
to establish. `ARCHITECTURAL` means the dependency assumes Bukkit implementation
internals or a bridge outside the narrow public API.

Risk is classified as `TRIVIAL`, `CORE_API`, `ARCHITECTURAL`, or `BLOCKED`.
`BLOCKED` is used when the requested phase explicitly forbids the required work.

## API dependency graph

| Domain | Representative dependencies | AtlasHybrid | Risk | Finding |
|---|---|---:|---:|---|
| Plugin bootstrap/lifecycle | `JavaPlugin`, `Plugin`, `PluginDescriptionFile`, STARTUP load, `onLoad/onEnable/onDisable` | SUPPORTED for observed construction | CORE_API | Classloader-owned bootstrap context now provides stable metadata, server, data folder and logger before subclass construction; raw boot proceeds to command API linkage. |
| Console and sender hierarchy | `ConsoleCommandSender`, `RemoteConsoleCommandSender`, `BlockCommandSender`, `ProxiedCommandSender`, conversations, permission methods | MISSING | CORE_API | The first raw-boot failure is `ConsoleCommandSender`. Adding only this interface would not make startup viable. |
| Server facade | name/version/Bukkit version, online/offline player lookup, operators, scheduler, plugin manager, services, messenger | PARTIAL | CORE_API | AtlasHybrid has a narrow `Server`; offline players, operators, services and messenger are absent. |
| Commands | `PluginCommand`, `TabExecutor`, `CommandMap`, sender permission checks, server command events | PARTIAL | CORE_API | `TabCompleter`, `TabExecutor`, aliases and Forge/Brigadier player/console completion are implemented. Command events and the broader command-map contract remain absent. |
| Configuration | `YamlConfiguration`, `ConfigurationSection`, typed maps/lists, section traversal | SUPPORTED for observed adapter path | CORE_API | Safe UTF-8 File/Reader loading, nested dot paths, typed getters, scalar lists and section key traversal now cover `BukkitConfigAdapter`; defaults/options and full YAML remain outside the bounded subset. |
| Events | pre-login/login/quit, join, world/game-mode change, command, plugin enable/disable, server command | PARTIAL | CORE_API | Join/quit exist; most LuckPerms lifecycle, context and command events do not. Async pre-login also needs a real thread-safety contract. |
| Scheduler | Bukkit sync scheduling plus LuckPerms' own scheduled executor and `ForkJoinPool` | PARTIAL | CORE_API | Existing sync bridge is narrow. LuckPerms' async pool is real and has explicit termination, but a successful lifecycle cannot be reached to validate it. |
| Services | `ServicesManager.register`, `ServicePriority`, API provider registration and optional Vault services | MISSING | CORE_API | A real, generic service registry would be required, including ownership and cleanup on disable. |
| Permissions public API | `Permissible`, `PermissibleBase`, `Permission`, defaults, attachments, subscriptions, effective permissions | MISSING | CORE_API | This is the primary plugin behavior, not optional linkage. It requires substantial generic semantics and tests. |
| Permissions implementation hooks | reflection into `SimplePluginManager.permissions`, `defaultPerms`, `permSubs`; replacement maps | ARCHITECTURAL | BLOCKED | AtlasHybrid deliberately has no `SimplePluginManager` implementation or compatible private fields. Reproducing them for one plugin would be an invasive compatibility patch. |
| Player/server permissible injection | CraftBukkit `CraftHumanEntity.perm`, `CraftEntity.perm`, `ServerCommandSender.perm`; fallback Glowstone reflection | ARCHITECTURAL | BLOCKED | Core permission interception explicitly resolves CraftBukkit implementation classes and private fields. The phase requires stopping before CraftBukkit/source or broad instrumentation work. |
| Plugin manager internals | `SimplePluginManager.commandMap`, `dependencyGraph`, dynamic dependency injection | ARCHITECTURAL | BLOCKED | These are implementation details, not a small public Bukkit API addition. |
| Player/world context | `OfflinePlayer`, UUID/name lookup, world environment, game mode, locale, op state | PARTIAL | CORE_API | Player/world basics exist, but the broader identity and context model is missing. |
| Plugin messaging | Bukkit messenger registration, `PluginMessageListener`, player plugin messages | MISSING | CORE_API | Optional depending on messaging configuration; must be real and lifecycle-clean if implemented. |
| Paper/Folia | `AsyncTabCompleteEvent`, Adventure bridge, `RegionScheduler` | OPTIONAL/ABSENT | ARCHITECTURAL | Guarded by class-presence checks. Their absence on Forge is not the primary blocker and no Paper emulation is proposed. |
| Classloading | nested Jar-in-Jar loader; reflective `PluginClassLoader` identification | PARTIAL | ARCHITECTURAL | Nested loading begins correctly. Attribution expects Bukkit `PluginClassLoader`; AtlasHybrid uses its own isolated loader. |
| Storage | default `storage-method: h2`; optional SQL/NoSQL backends | UNKNOWN | CORE_API | The default is local and needs no credentials. It was not reached because construction failed before config/storage initialization. |
| Network/external services | optional messaging, update checks, web editor/HTTP | UNKNOWN | CORE_API | Not exercised. The test plan would keep local H2 and avoid external database credentials. |

## Architectural gate

The public types missing at the first linkage point are not, by themselves, an
architectural blocker. However, source inspection establishes that a successful
LuckPerms permission provider on Bukkit relies on both:

1. replacing private permission/default/subscription maps in
   `SimplePluginManager`; and
2. injecting LuckPerms permissibles into CraftBukkit entity and command-sender
   fields.

Those mechanisms are central to permission checks. Skipping them could make the
plugin appear enabled while leaving permissions semantically incorrect. Adding
surface types such as `ConsoleCommandSender` or `ServicesManager` first would
only mask the known architectural limit and produce a misleading partial boot.

**Decision: `BLOCKED`.** No AtlasHybrid runtime/API implementation was attempted.
Continuing requires an explicit architectural decision about a generic native
Forge permission bridge or an allowed CraftBukkit-compatible implementation.
The latter is outside this phase's authorized scope.

## Scheduler and shutdown research

`BukkitSchedulerAdapter` delegates sync execution to
`BukkitScheduler.scheduleSyncDelayedTask`. Async work uses LuckPerms'
`JavaSchedulerAdapter`: one `luckperms-scheduler` thread and a 16-way daemon
`ForkJoinPool` named `luckperms-worker-*`. Disable first stops scheduled work,
then removes hooks, closes messaging/storage/watchers, unregisters the API, and
finally shuts down and awaits the worker pool. This design is inspectably clean,
but AtlasHybrid cannot validate it dynamically until the architectural gate is
resolved.

## Phase 9.1 exact symbol audit

The tables below distinguish linker/API requirements from implementation-shape
assumptions. “Required” means required for the corresponding LuckPerms behavior,
not necessarily required for the JVM to finish `onEnable`.

### Public Bukkit API uses

| Class | Method or field used | Why LuckPerms uses it | Phase | Required? |
|---|---|---|---|---|
| `ConsoleCommandSender` | type identity; `Server#getConsoleSender`; `sendMessage`; permission and op methods | Construct a null-safe console wrapper, identify command context, send output, and evaluate command permissions | Constructor and runtime | Required for bootstrap linkage and console commands |
| `Permissible` | `hasPermission`, subscription identity | Public subject contract and permission subscription queries | Runtime | Required for generic Bukkit permission behavior |
| `PermissibleBase` | constructor and all overridable permissible methods | Base class for LuckPerms player permissible, dummy replacement, and verbose delegate | Class initialization and runtime | Required for player interception |
| `Permission` | constructors, name/default/children, registration | Register LuckPerms command nodes and incorporate Bukkit defaults/children | Enable and runtime | Required with normal configuration |
| `PermissionDefault` | `OP`, `FALSE`, `getValue(op)` | Configure command defaults and resolve op-dependent defaults | Enable and runtime | Required |
| `PermissionAttachment` | construct/copy; set/unset/get/remove; removal callback | Preserve existing plugin attachments and translate them into transient LuckPerms nodes | Login/runtime/quit | Required for attachment compatibility |
| `PermissionAttachmentInfo` | constructor | Return the effective permission snapshot from `LuckPermsPermissible` | Runtime | Required when queried |
| `PluginManager` | permission add/remove/default/subscription APIs; events; plugin lookup | Register command permissions, observe Bukkit permission changes, listeners, and optional Vault | Load, enable, runtime | Required; breadth varies by feature |
| `ServicesManager` | `register` and `unregister` | Publish the LuckPerms API and optional Vault providers | Enable/disable | LuckPerms API publication required; Vault optional |
| `RegisteredServiceProvider` / `ServicePriority` | registration metadata; `Normal` and `High` priorities | Allow consumers to select the winning service implementation | Runtime | Required for service interoperability |
| `Player` / `CommandSender` | identity, op, permission, messages, player UUID/name | Permission subject, command execution, and sender wrapping | Login and runtime | Required |

`LPBukkitPlugin#registerApiOnPlatform` publishes
`net.luckperms.api.LuckPerms` with `ServicePriority.Normal`. `VaultHookManager`
publishes Vault `Permission` and `Chat` at `High` only when Vault is present and
explicitly unregisters them. The common LuckPerms shutdown unregisters its static
API provider; normal Bukkit lifecycle is expected to remove plugin-owned service
registrations, so AtlasHybrid must call `ServicesManager#unregisterAll` during
plugin cleanup.

### Reflection and implementation-shape uses

| Class | Exact symbol and expected type | Purpose | When | Missing behavior | Required? |
|---|---|---|---|---|---|
| `PermissibleInjector` | `org.bukkit.craftbukkit[.version].entity.CraftHumanEntity#perm` as `PermissibleBase` | Read, replace, restore, and verify the player's active permissible | Static init; login/quit/disable | Falls back to `net.glowstone.entity.GlowHumanEntity#permissions`; if both paths fail, class initialization fails | **Yes for functional player permissions** |
| `PermissibleInjector` | `PermissibleBase#attachments` as `List<PermissionAttachment>` | Transfer existing attachments to `LuckPermsPermissible` before replacement | Static init/login | Missing field causes `ExceptionInInitializerError` | **Yes for this injector** |
| `LuckPermsPermissible` | `PermissibleBase#attachments` | Replace the inherited list with a proxy for reflective attachment users | Static init/constructor | Missing field causes `ExceptionInInitializerError` | Yes for this class shape |
| `DummyPermissibleBase` | `PermissibleBase#attachments`, `PermissibleBase#permissions` | Copy internal state into monitored wrappers and create a post-quit dummy | Static init/runtime | Missing field causes `ExceptionInInitializerError` | Required by monitoring/quit path |
| `LuckPermsPermissionAttachment` | `PermissionAttachment#permissions` as `Map<String,Boolean>` | Install a proxy map so reflective mutations still update LuckPerms transient nodes | Static init/attachment creation | Missing field causes `ExceptionInInitializerError` | Required by LuckPerms attachment implementation |
| `LuckPermsPermissionMap` | `Permission#children` as `Map<String,Boolean>` | Wrap child maps, invalidate caches on mutation, and pre-resolve child relationships | Map injection/runtime | Missing field causes `ExceptionInInitializerError` | Required when Bukkit child/default processing is enabled |
| `InjectorPermissionMap` | `SimplePluginManager#permissions` as `Map<String,Permission>` | Observe permission registration and child changes | Enable and scheduled reinjection | Exception is caught and logged; plugin field remains unset | Semantically required with default processing |
| `InjectorDefaultsMap` | `SimplePluginManager#defaultPerms` as `Map<Boolean,Set<Permission>>` | Observe op/non-op default sets and invalidate calculators | Enable and scheduled reinjection | Exception is caught and logged; plugin field remains unset | Semantically required with default processing |
| `InjectorSubscriptionMap` | `SimplePluginManager#permSubs` as `Map<String,Map<Permissible,Boolean>>` | Include LuckPerms subjects in Bukkit subscription queries without eagerly storing all player nodes | Enable and scheduled reinjection | Exception is caught and logged | Required for subscription compatibility; not basic direct checks |
| `CommandMapUtil` | `SimplePluginManager#commandMap` as `CommandMap` | Normalize console commands beginning with `/` | Class init/runtime command event | Missing field causes initializer/runtime failure on this path | Optional command normalization |
| `PluginManagerUtil` | `SimplePluginManager#dependencyGraph` as Guava `MutableGraph<String>` | Add a synthetic LuckPerms-to-Vault ordering edge | Enable | Missing/foreign manager fails silently | Optional workaround |
| `PermissibleMonitoringInjector` | `ServerCommandSender#perm`; static `#blockPermInst` | Wrap console/command-block checks for verbose monitoring | Enable/disable | Each operation catches and ignores all exceptions | Optional verbose feature |
| `PermissibleMonitoringInjector` | static `CraftEntity#getPermissibleBase()` and static `CraftEntity#perm` | Wrap non-player entity checks for verbose monitoring | Enable/disable | Exception ignored | Optional verbose feature |
| `LPBukkitBootstrap` | `PluginClassLoader#getPlugin()` | Attribute dependency/classloader activity to a Bukkit plugin | Runtime diagnostics/dependencies | Reflective exception propagates only to the caller of identification | Optional attribution |

`CraftBukkitImplementation` derives an optional version segment only when the
server class name matches
`org.bukkit.craftbukkit.<version>.CraftServer`; AtlasHybrid would produce the
unversioned lookup `org.bukkit.craftbukkit.<symbol>`. The class names and fields
are CraftBukkit implementation details and are version-fragile even though the
helper supports both versioned and unversioned package layouts.

## Failure and fallback conclusions

- The player fallback is **only Glowstone**, not a public Bukkit hook.
- Console, command-block, and generic-entity injection is optional verbose
  monitoring because every exception is swallowed.
- Player injection is mandatory for the Bukkit platform's basic permission
  behavior: login is denied when it fails.
- Map injection may allow enable to continue after logging, but normal default
  configuration later depends on the populated maps. It is not safe to call the
  failure semantically optional.
- `dependencyGraph` injection and slash-prefixed console normalization are not
  permission correctness requirements.

## Can AtlasHybrid supply `SimplePluginManager` shape?

Technically yes: an Atlas-owned public class could expose compatible private
fields and `AtlasPluginManager` could extend it. This would satisfy the
`instanceof` check and the five reflective field lookups if their types and
names matched. It would not solve `CraftHumanEntity#perm`, and it would make
private upstream layout part of the AtlasHybrid ABI. ADR-006 therefore rejects
this as the primary permission architecture.

## Injection answer

For the unmodified Bukkit platform of LuckPerms 5.5.81:

- **A — delegate `Player#hasPermission` to the provider:** this is the desired
  observable outcome;
- **B — replace the CraftPlayer permissible field:** this is the concrete
  mechanism LuckPerms uses to obtain A;
- **C — both:** accurate when “both” means outcome plus mechanism, not two
  independent alternatives.

AtlasHybrid-owned composition can provide A without Minecraft mutation. It
cannot make the current LuckPerms Bukkit artifact install its permissible unless
LuckPerms uses an Atlas provider hook, an adapter is supplied, or AtlasHybrid
emulates/transforms the private CraftBukkit path.

## Phase 9.2 observed loader boundary

After the public permission, console and services contracts were implemented,
raw boot #2 no longer failed on `ConsoleCommandSender`. LuckPerms' outer
`BukkitLoaderPlugin` constructor loaded and reflectively instantiated
`LPBukkitBootstrap`. This helper is not a second `JavaPlugin`; it called
`getLogger()` on the outer loader plugin at line 104 before AtlasHybrid's
post-construction `atlasInitialize` step, producing `IllegalStateException`.

| Item | Classification |
|---|---|
| Symbol/path | `LPBukkitBootstrap.<init>` -> `JavaPlugin#getLogger` |
| Need | Logger/server/plugin construction context must exist while the main `JavaPlugin` constructor executes jar-in-jar bootstrap code |
| Public API? | `JavaPlugin#getLogger` is public; the timing and classloader-driven initialization shape are implementation/lifecycle semantics |
| CraftBukkit internals? | No; failure occurs before `SimplePluginManager` maps or `CraftHumanEntity#perm` injection |
| Category | **ARCHITECTURAL** |
| Phase 9.2 action | Stop and document; no cascading loader or LuckPerms-specific implementation |

## Phase 9.3 observed loader boundary

AtlasHybrid now associates parsed metadata and stable runtime objects with the
plugin classloader before construction. A short thread-local activation is
owned and validated by that classloader and cleared in `finally`. This let raw
boot #3 pass both `loader.getLogger()` and `loader.getServer()` in
`LPBukkitBootstrap.<init>` without a LuckPerms-specific branch.

The next first failure occurs while line 110 creates `LPBukkitPlugin`:

| Item | Classification |
|---|---|
| Symbol/path | `LPBukkitBootstrap.<init>:110` -> define `LPBukkitPlugin` -> `org.bukkit.command.TabExecutor` |
| Diagnostic | `Missing API: org.bukkit.command.TabExecutor`, `Status: NOT_IMPLEMENTED` |
| Public API? | Yes, normal Bukkit command/tab-completion interface |
| CraftBukkit internals? | No; the later injection paths have not been reached |
| Category | **CORE_API** |
| Phase 9.3 action | Stop and document; do not implement the next symbol or continue the cascade |

## Phase 9.4 observed loader boundary

The generic command expansion let the unchanged artifact link `TabExecutor`,
finish construction and begin `LPBukkitBootstrap.onLoad`. Its configuration
adapter then invoked a public Bukkit overload that AtlasHybrid does not yet
provide:

| Item | Classification |
|---|---|
| Symbol/path | `BukkitConfigAdapter.reload:51` -> `YamlConfiguration#loadConfiguration(java.io.File)` |
| Diagnostic | `Missing API: org.bukkit.configuration.file.YamlConfiguration#loadConfiguration(java.io.File)`, `Status: NOT_IMPLEMENTED` |
| Lifecycle phase | `BukkitLoaderPlugin.onLoad` via `LPBukkitBootstrap.onLoad:152`; before enable and storage startup |
| Public API? | Yes; a simple overload alongside AtlasHybrid's existing `Path` method |
| CraftBukkit internals? | No; the known later permission-injection paths have not been reached |
| Category | **TRIVIAL** |
| Phase 9.4 action | Stop and document; do not implement the next API or continue the cascade |

## Phase 9.5 observed loader boundary

The unchanged artifact loaded its full configuration through the new public
Configuration API, completed `onLoad` and entered `onEnable`. Adventure's Bukkit
audience implementation then queried the server's online-player collection:

| Item | Classification |
|---|---|
| Symbol/path | `BukkitAudiencesImpl.<init>:93` -> `Server#getOnlinePlayers(): Collection` |
| Diagnostic | `Missing API: org.bukkit.Server#getOnlinePlayers()`, `Status: NOT_IMPLEMENTED` |
| Lifecycle phase | `BukkitLoaderPlugin.onEnable` via `LPBukkitBootstrap.onEnable:177`, during sender-factory setup |
| Previous boundary | `YamlConfiguration#loadConfiguration(File)` passed; real config values and nested sections were consumed |
| Public API? | Yes, normal Bukkit server/player collection API |
| CraftBukkit internals? | No; the known later permissible/map injection paths have not been reached |
| Category | **CORE_API** |
| Phase 9.5 action | Stop and document; do not implement `Server#getOnlinePlayers` or continue the cascade |

## Phase 9.6 observed loader boundary

The session registry implements the Bukkit 1.19.2
`Collection<? extends Player>` contract as immutable snapshots backed by real
Forge player adapters. UUID and case-insensitive exact-name lookup resolve the
same adapter. Registration occurs before join dispatch; removal and permission
cleanup occur after quit dispatch and during shutdown.

Source audit found direct LuckPerms uses of `getOnlinePlayers()` and
`getPlayer(UUID)`, plus `getPlayerExact(String)` in the optional Vault path.
`getOfflinePlayer(String/UUID)` is used by later username/UUID lookup methods,
but raw boot #6 did not reach it, so OfflinePlayer remains deferred.

| Item | Classification |
|---|---|
| Passed boundary | `BukkitAudiencesImpl.<init>:93` -> `Server#getOnlinePlayers()` |
| New symbol/path | `BukkitAudiencesImpl.registerEvent:218` -> `org.bukkit.plugin.EventExecutor` |
| Diagnostic | `Missing API: org.bukkit.plugin.EventExecutor`, `Status: NOT_IMPLEMENTED` |
| Lifecycle phase | `BukkitLoaderPlugin.onEnable`, during Adventure audience event registration |
| Public API? | Yes, normal Bukkit event dispatch interface |
| CraftBukkit internals? | No; known later permission injection remains unreached |
| Category | **CORE_API** |
| Phase 9.6 action | Stop and document; do not add `EventExecutor` or continue the cascade |

## Phase 9.7 observed event and lifecycle boundary

The generic Event API let Adventure register its explicit executors through
`PluginManager#registerEvent`; the former `EventExecutor` linker failure no
longer occurs. LuckPerms reached its first enable-time message, which initializes
the Adventure Bukkit component serializer.

| Item | Classification |
|---|---|
| Passed boundary | `BukkitAudiencesImpl.registerEvent:218` -> `EventExecutor` and public `registerEvent` |
| New symbol/path | `BukkitComponentSerializer.<clinit>:50` -> `org.bukkit.Material` |
| Diagnostic | `Missing API: org.bukkit.Material`, `Status: NOT_IMPLEMENTED` |
| Lifecycle phase | `BukkitLoaderPlugin.onEnable`, while sending the enable/startup message |
| Public API? | Yes; registry-backed Bukkit material enum/type surface |
| CraftBukkit internals? | No at this point; the known later permission injection remains unreached |
| Category | **CORE_API** |
| Phase 9.7 action | Stop and document; do not stub `Material` or continue the cascade |

The failed-enable audit found that the Spigot lifecycle marks a plugin enabled
before invoking `onEnable`, so a failed callback remains eligible for later
disable. AtlasHybrid performs that best-effort disable immediately, then rolls
back every Atlas-owned registration and returns the plugin to disabled state.
In raw boot #7, LuckPerms' partial `onDisable` threw because `webEditorStore` was
not initialized; the exception was suppressed onto the original failure and did
not block rollback. No attributable live threads remained, and normal `stop`
ended both Minecraft and the Gradle launcher without manual interruption.

## Phase 9.8 observed Material boundary

Bytecode inspection of the exact remapped Adventure platform library downloaded
by LuckPerms showed that `BukkitComponentSerializer` uses `Material` only to
look up enum constants `BLUE_ICE` and `NETHERITE_PICKAXE` through its generic
enum-reflection helper. It does not perform a Material registry lookup and does
not reference `ItemStack` in this initialization path.

AtlasHybrid nevertheless implements the complete vanilla 1.19.2 block/item
identifier union rather than a two-constant compatibility stub. Raw boot #8
passed both enum probes and reached the next bytecode instruction:

| Item | Classification |
|---|---|
| Passed boundary | `BukkitComponentSerializer.<clinit>:50` -> `Material.BLUE_ICE` / `NETHERITE_PICKAXE` enum lookup |
| New symbol/path | `BukkitComponentSerializer.<clinit>:66` -> `Bukkit#getUnsafe(): UnsafeValues` |
| Downstream method visible in bytecode | `UnsafeValues#getDataVersion(): int` |
| Diagnostic | `Missing API: org.bukkit.Bukkit#getUnsafe()`, `Status: NOT_IMPLEMENTED` |
| Lifecycle phase | `BukkitLoaderPlugin.onEnable`, while constructing the first Adventure message serializer |
| ItemStack or inventory? | Not reached and not referenced by this class; deferred |
| Bukkit Registry? | Not reached; deferred |
| CraftBukkit internals? | No at this point; the known later permission injection remains unreached |
| Category | **CORE_API** |
| Phase 9.8 action | Stop and document; do not implement `UnsafeValues` or continue the cascade |

The process behavior matched raw boot #7: best-effort disable produced the same
suppressed LuckPerms partial-initialization NPE, AtlasHybrid rollback completed,
no plugin-owned live thread was diagnosed, and normal `stop` ended the launcher.

## Phase 9.9 UnsafeValues audit

The Spigot 1.19.2 contract contains fourteen `UnsafeValues` methods. The full
classification and version-number provenance are recorded in
[`UNSAFE_VALUES_API.md`](../architecture/UNSAFE_VALUES_API.md). Only
`getDataVersion()` is `VERSION_METADATA` and safe for this phase. Legacy/data
fixing, ItemStack, advancement, attribute, creative-registry and bytecode
processing operations require CraftBukkit or Minecraft internals and remain
explicitly unsupported or deferred.

Inspection of LuckPerms' exact remapped `adventure-platform-bukkit-4.21.1` JAR
found exactly one UnsafeValues reference. In
`BukkitComponentSerializer.<clinit>`, the post-1.13 branch passes
`Bukkit.getUnsafe().getDataVersion()` into
`JSONOptions.byDataVersion().at(int)` and installs that option state on its Gson
serializer builder. The pre-1.13 branch uses data version zero plus the legacy
hover-event serializer. No other UnsafeValues call exists in that JAR, so
Minecraft 1.19.2 world data version `3120` is sufficient to cross this specific
initializer boundary.

Raw boot #9 confirmed that conclusion: the static initializer completed and
LuckPerms printed its platform enable banner. The next boundary was
`BukkitConnectionListener.<init>:71` calling the public
`Server#getOnlineMode(): boolean` method.

| Item | Classification |
|---|---|
| Passed boundary | `BukkitComponentSerializer.<clinit>` -> `UnsafeValues#getDataVersion()` |
| Returned data version | `3120`, Mojang 1.19.2 `version.json` `world_version` |
| New symbol/path | `BukkitConnectionListener.<init>:71` -> `Server#getOnlineMode()` |
| Use | If offline mode, combine with a CraftBukkit-version regex to activate a CraftBukkit offline-mode warning/login guard |
| Diagnostic | `Missing API: org.bukkit.Server#getOnlineMode()`, `Status: NOT_IMPLEMENTED` |
| Lifecycle phase | `BukkitLoaderPlugin.onEnable`, during platform-listener registration |
| Public API? | Yes; ordinary server configuration state |
| CraftBukkit/NMS required for the method? | No; later conditional code merely detects a CraftBukkit version string |
| Category | **CORE_API** |
| Phase 9.9 action | Stop and document; no cascading implementation |

Rollback removed AtlasHybrid registrations, but two LuckPerms-owned metadata
OkHttp threads created before this failure remained alive. Minecraft saved all
dimensions after `stop`; the Gradle launcher required interruption because of
those external threads.

## Phase 9.10 server identity and next event boundary

AtlasHybrid now obtains online-mode state directly from
`MinecraftServer#usesAuthentication()`. The static Bukkit accessor delegates to
the installed server instance, so no second configuration source exists. Raw
boot #10 passed the former `BukkitConnectionListener` linker boundary and
failed while Java reflection enumerated the declared methods of LuckPerms'
listener class:

| Item | Classification |
|---|---|
| Passed boundary | `BukkitConnectionListener.<init>:71` -> `Server#getOnlineMode()` |
| New symbol/path | `LPBukkitPlugin.registerPlatformListeners:136` -> `PluginManager#registerEvents` -> `Class#getDeclaredMethods` -> `org.bukkit.event.player.AsyncPlayerPreLoginEvent` |
| Diagnostic | `Missing API: org.bukkit.event.player.AsyncPlayerPreLoginEvent`, `Status: NOT_IMPLEMENTED` |
| Lifecycle phase | `BukkitLoaderPlugin.onEnable`, during platform-listener registration |
| Public API? | Yes; Bukkit asynchronous pre-login event contract |
| CraftBukkit/NMS required for the type itself? | No, but correct dispatch belongs to a dedicated connection-event phase |
| Category | **CORE_API** |
| Phase 9.10 action | Stop and document; do not add the event or continue the cascade |

### Failed-enable HTTP resource audit

Bytecode inspection of the exact LuckPerms 5.5.81 artifact shows that
`AbstractLuckPermsPlugin.enable()` constructs its own `OkHttpClient` and starts
the translation repository refresh before `registerPlatformListeners()`. Its
normal `disable()` path eventually shuts down the client's dispatcher executor
and evicts its connection pool. On partial enable, however,
`extensionManager.close()` is reached first and throws because the extension
manager has not yet been initialized; the later HTTP cleanup is consequently
skipped. This is case **B**: the plugin owns a client and intends to close it,
but its disable path is not tolerant of this partial initialization point.
AtlasHybrid does call the plugin's disable hook and then completes its own
rollback; it is not suppressing a cleanup stage.

Raw boot #9 recorded thread names `OkHttp metadata.luckperms.net` and
`OkHttp metadata.luckperms.net Writer`, and their persistence after Minecraft
stopped proves that at least one live resource prevented JVM exit. That older
run predates structured capture, so exact daemon flags, context classloaders and
stacks cannot be recovered from its log and are not guessed. Raw boot #10 ran
with the enhanced monitor, but neither thread survived the failure snapshot;
therefore no ownership confidence was assigned, and normal shutdown exited the
launcher. The generic diagnostic remains covered by a controlled failed-enable
fixture with `HIGH` context-classloader evidence.

## Phase 9.11 connection listener audit and raw boot

The exact LuckPerms 5.5.81 `BukkitConnectionListener` bytecode declares five
connection handlers. `onPlayerPreLogin` uses
`AsyncPlayerPreLoginEvent` at `LOW` to load user data and disallow database
failures; `onPlayerPreLoginMonitor` uses `MONITOR` to reject a later re-allow.
`onPlayerLogin` uses `PlayerLoginEvent` at `LOWEST` to validate loaded user
state, construct/inject `LuckPermsPermissible`, signal context, and disallow
failure states. `onPlayerLoginMonitor` uses `MONITOR` to reject a later re-allow
and verify injection. `onPlayerQuit` uses `PlayerQuitEvent` at `MONITOR` to
disconnect data and schedule uninjection. No handler explicitly sets
`ignoreCancelled`, and this listener does not use deprecated
`PlayerPreLoginEvent`.

AtlasHybrid now dispatches `AsyncPlayerPreLoginEvent` through its existing
event executor during Forge `PlayerNegotiationEvent` queued work. The event is
truly asynchronous; authenticated UUIDs are preserved, offline UUIDs use
Minecraft's deterministic algorithm, and the address is the real transport
peer. Proxy forwarding is not claimed. A denial closes the connection before
player construction and therefore cannot create a join event or AtlasHybrid
player state. Full semantics and pipeline ordering are documented in
[`CONNECTION_EVENT_API.md`](../architecture/CONNECTION_EVENT_API.md).

Raw boot #11 confirms that reflection resolves both async pre-login handlers.
The first following missing symbol is:

| Item | Classification |
|---|---|
| Passed boundary | `Class#getDeclaredMethods` resolves `AsyncPlayerPreLoginEvent` handlers |
| New symbol/path | `LPBukkitPlugin.registerPlatformListeners:136` -> `PluginManager#registerEvents` -> `Class#getDeclaredMethods` -> `org.bukkit.event.player.PlayerLoginEvent` |
| Diagnostic | `Missing API: org.bukkit.event.player.PlayerLoginEvent`, `Status: NOT_IMPLEMENTED` |
| Lifecycle phase | `BukkitLoaderPlugin.onEnable`, during `BukkitConnectionListener` registration after the LuckPerms enable banner |
| Public API? | Yes; synchronous, cancellable Bukkit login-stage event |
| CraftBukkit/NMS required for the type itself? | No; LuckPerms' handler subsequently performs its known CraftBukkit-shaped permissible injection |
| Category | **CORE_API** |
| Phase 9.11 action | Stop and document; defer `PlayerLoginEvent` and do not cascade |

The failed-enable dump found nine `luckperms-worker-*` daemon threads parked in
one ForkJoinPool, with high name/stack ownership confidence. AtlasHybrid's
rollback left zero permission providers and services. Normal `stop` saved the
three dimensions, the launcher exited successfully, and no server Java process
remained; no thread was killed or otherwise manipulated.

## Phase 9.12A login gate and raw boot #12

AtlasHybrid now constructs the actual future vanilla `ServerPlayer` at a
pre-placement gate, exposes a hidden CONNECTING adapter to synchronous
`PlayerLoginEvent`, and promotes that exact adapter only after Forge reports
login. DENY sends a login disconnect before placement and removes the adapter
and permission state. The hook is isolated to the Forge 1.19.2 platform and is
documented in [`MINECRAFT_LOGIN_HOOK.md`](../architecture/MINECRAFT_LOGIN_HOOK.md).

Raw boot #12 proves reflection and registration passed both LuckPerms
`PlayerLoginEvent` handlers. Registration advanced from
`BukkitConnectionListener` to `BukkitPlatformListener` and reached the next
missing declared handler type:

| Item | Classification |
|---|---|
| Passed boundary | `PlayerLoginEvent` LOWEST/MONITOR methods resolve; `BukkitConnectionListener` registration completes |
| New symbol/path | `LPBukkitPlugin.registerPlatformListeners:137` -> `PluginManager#registerEvents` -> `Class#getDeclaredMethods` -> `org.bukkit.event.server.ServerCommandEvent` |
| Diagnostic | `Missing API: org.bukkit.event.server.ServerCommandEvent`, `Status: NOT_IMPLEMENTED` |
| Lifecycle phase | platform-listener registration; overall `registerPlatformListeners` and `onEnable` incomplete |
| Public API? | Yes; synchronous server-command event contract |
| CraftBukkit/NMS required for the type itself? | No; correct dispatch needs a separately audited command pipeline bridge |
| Category | **CORE_API** |
| Phase 9.12A action | Stop and document; no cascade |

The post-stop dump observed eight parked daemon LuckPerms workers, daemon
Okio/OkHttp support threads, and one non-daemon OkHttp writer. The Minecraft
server stopped and saved normally, but the external writer retained the JVM
until the launcher was interrupted.

## Phase 9.13 server command boundary and raw boot #13

The Spigot 1.19.2 audit confirms that `ServerCommandEvent` extends
`ServerEvent`, is cancellable, and exposes a mutable command plus its sender.
`RemoteServerCommandEvent` extends it but owns a distinct `HandlerList`. Exact
LuckPerms bytecode declares handlers for local server commands, remote server
commands, player command preprocessing, and plugin enable. The first three feed
their mutable/cancellable command event into LuckPerms' common command handler;
the plugin-enable handler conditionally integrates Vault.

AtlasHybrid now dispatches local console and actual vanilla RCON sources through
that contract using the existing event executor and Forge pre-execution command
event. Mutation reparses once with the original Minecraft source; cancellation
cancels execution. Player command sources are deliberately excluded. No new
Mixin, NMS hook, CraftBukkit fake, or plugin-specific branch was added. See
[`SERVER_COMMAND_EVENT_API.md`](../architecture/SERVER_COMMAND_EVENT_API.md).

Raw boot #13 moved past both newly available classes and failed while reflecting
the remaining declared methods of the same platform listener:

| Item | Classification |
|---|---|
| Passed boundary | `ServerCommandEvent` and `RemoteServerCommandEvent` link in `BukkitPlatformListener` |
| New symbol/path | `LPBukkitPlugin.registerPlatformListeners:137` -> `PluginManager#registerEvents` -> `Class#getDeclaredMethods` -> `PlayerCommandPreprocessEvent` |
| Diagnostic | `Missing API: org.bukkit.event.player.PlayerCommandPreprocessEvent`, `Status: NOT_IMPLEMENTED` |
| Lifecycle phase | `BukkitLoaderPlugin.onEnable`, during platform-listener registration |
| Public API? | Yes; synchronous cancellable/mutable player-command event |
| Category | **CORE_API** |
| Registration status | connection listener complete; platform listener, overall method, and `onEnable` incomplete |
| Phase 9.13 action | Stop and document; do not implement the next event or cascade to `PluginEnableEvent` |

The pre-stop and post-stop dumps both showed seven daemon LuckPerms workers,
four OkHttp threads, one daemon Okio watchdog, and a non-daemon OkHttp writer.
Minecraft saved normally, but the writer retained the JVM until the launcher
was interrupted. No server process remained afterward.

## Phase 9.14 player command boundary and raw boot #14

The Spigot 1.19.2 event has two constructors, a mutable player and slash-prefixed
message, deprecated recipients, cancellation, and an independent static
`HandlerList`. LuckPerms registers its handler with `ignoreCancelled=true` and
passes the event player, message, and cancellable object to its common handler,
which watches the `op`/`deop` command pattern when operator support is disabled.

AtlasHybrid dispatches the event synchronously for any Minecraft command whose
source entity is a `ServerPlayer`, before Brigadier execution. Bukkit commands,
aliases, vanilla commands, and Forge commands share that point. Mutation
replaces the current parse result once; cancellation cancels the Forge event.
No network-thread dispatch, duplicate publication, Mixin, NMS hook, or
LuckPerms-specific path was added. The full design is in
[`PLAYER_COMMAND_EVENT_API.md`](../architecture/PLAYER_COMMAND_EVENT_API.md).

Raw boot #14 crossed the player-command type and stopped at the final previously
known declared event dependency in the same listener:

| Item | Classification |
|---|---|
| Passed boundary | `PlayerCommandPreprocessEvent` links in `BukkitPlatformListener` |
| New symbol/path | `LPBukkitPlugin.registerPlatformListeners:137` -> `PluginManager#registerEvents` -> `Class#getDeclaredMethods` -> `PluginEnableEvent` |
| Diagnostic | `Missing API: org.bukkit.event.server.PluginEnableEvent`, `Status: NOT_IMPLEMENTED` |
| Lifecycle phase | `BukkitLoaderPlugin.onEnable`, during platform-listener registration |
| Public API? | Yes; synchronous, non-cancellable plugin lifecycle event |
| LuckPerms use | Detect Vault by plugin name/provides and invoke `tryVaultHook(true)` |
| Category | **CORE_API** |
| Registration status | connection listener complete; platform listener, overall method, and `onEnable` incomplete |
| Phase 9.14 action | Stop and document; defer lifecycle implementation to a later phase |

The pre/post-stop counts were six daemon LuckPerms workers, four OkHttp threads,
one daemon Okio watchdog, and one non-daemon OkHttp writer. Minecraft stopped
and saved normally; the writer retained the JVM until the launcher was
interrupted. No compatibility server process remained afterward.

## Phase 9.15 plugin lifecycle boundary and raw boot #15

The audited Spigot 1.19.2 API defines `PluginEvent extends ServerEvent` with a
real `Plugin` reference and public `(Plugin)` constructor. `PluginEnableEvent`
and `PluginDisableEvent` are synchronous and non-cancellable, each with its own
static `HandlerList`. Spigot dispatches enable after the enable callback and
disable before the disable callback and resource cleanup. AtlasHybrid follows
that successful-path ordering while deliberately suppressing false lifecycle
events for failed enable attempts.

The existing `EventExecutor` bus performs both dispatches. A two-plugin fixture
proved earlier-plugin and self-observation, exact-once ordering, observer
exception isolation, reverse disable, cleanup, and restart. The integration
proof emitted both lifecycle markers exactly once.

Raw boot #15 produced this boundary result:

| Item | Classification |
|---|---|
| Passed boundary | `PluginEnableEvent` links in `BukkitPlatformListener` |
| Platform listener status | `BukkitConnectionListener` complete; `BukkitPlatformListener` complete |
| Checkpoint | `LUCKPERMS_PLATFORM_LISTENERS_REGISTERED` |
| Method status | `LPBukkitPlugin.registerPlatformListeners` complete |
| Later progress | H2 storage initialized; `LPBukkitPlugin.registerCommands` entered |
| New symbol/path | `BukkitCommandExecutor.register:75` -> `PluginManager#registerEvents` -> `Class#getDeclaredMethods` -> `org.bukkit.entity.Entity` |
| Diagnostic | `Missing API: org.bukkit.entity.Entity`, `Status: NOT_IMPLEMENTED` |
| Public API? | Yes; Bukkit base entity interface |
| Why linked here | compiler-generated selector pipeline method is resolved during declared-method reflection; selector execution was not reached |
| Category | **CORE_API** |
| LuckPerms enable | incomplete; `LUCKPERMS_ENABLE_REACHED` not recorded |
| Phase 9.15 action | stop and document; no cascade implementation |

The pre-stop dump showed ten daemon LuckPerms workers, four OkHttp threads,
one daemon Okio watchdog, one daemon H2 MVStore writer, and one non-daemon
OkHttp metadata writer. After normal Minecraft stop, nine workers, three
OkHttp threads including the writer, and the H2 writer remained; the watchdog
had exited. The writer retained the JVM until the launcher was interrupted. No
server process remained afterward.

## Phase 9.16 entity foundation and raw boot #16

| Item | Classification |
|---|---|
| Exact former linkage | `BukkitCommandExecutor` compiler-generated selector predicate accepts `Entity`; reflection resolves it before selectors can execute |
| Selector behavior | `Server#selectEntities` -> `instanceof Player` -> cast -> `Player#getUniqueId`; AtlasHybrid does not expose `selectEntities`, so execution is disabled |
| Other LuckPerms Entity use | `FoliaSchedulerAdapter` calls Paper `Entity#getScheduler`; the Folia adapter is not selected on Forge |
| Implemented core | public Entity/LivingEntity/HumanEntity/Player ancestry, UUID, Minecraft entity ID, live World and Location |
| Registry policy | existing player session registry reused; no duplicate wrapper or premature generic registry |
| EntityType | enum audited and deferred in full; no incomplete fake mapping |
| Passed boundary | `Entity` links, `BukkitCommandExecutor` registers, and `registerCommands` completes |
| New symbol/path | `AbstractLuckPermsPlugin.enable:233` -> `LPBukkitPlugin.setupContextManager:189` -> load `BukkitPlayerCalculator` -> `org.bukkit.GameMode` |
| Exact subsequent use | `GameMode.class`, `GameMode.values`, `Player#getGameMode`, and `PlayerGameModeChangeEvent` |
| Category | **CORE_API** |
| CraftBukkit/NMS? | No for this symbol; later known permissible injection is still architectural |
| LuckPerms enable | incomplete; `LUCKPERMS_ENABLE_REACHED` absent |
| Phase 9.16 action | stop and document; no GameMode/event cascade |

Raw boot #16 had six LuckPerms workers, four OkHttp threads, one Okio watchdog
and one H2 writer before stop. After normal world-saving shutdown, five
workers, three OkHttp threads and the H2 writer remained; Okio exited. The
non-daemon OkHttp writer retained the JVM until the launcher was interrupted.
No process remained. The regression suite passed `114/114`, integration and
both external plugin proofs passed, and both clean builds were byte-identical.

## Phase 9.17 game-mode context and raw boot #17

The Spigot 1.19.2 `GameMode` binary contract declares `CREATIVE`, `SURVIVAL`,
`ADVENTURE`, `SPECTATOR` with legacy values `1`, `0`, `2`, `3`. LuckPerms'
`BukkitPlayerCalculator` consumes the class, all enum values and
`Player#getGameMode()`. Its broader exact context surface also consumes
`Player#getWorld()`, `World#getName()`, `World#getEnvironment()`,
`World.Environment`, `Server#getWorlds()`, and join/world-change/game-mode-change
events. It does not consume locale, network address, inventory, metadata or
persistent-data APIs.

AtlasHybrid maps all four Minecraft `GameType` values explicitly and exposes a
volatile per-session snapshot because LuckPerms context recalculation is not
confined to the server thread. The snapshot starts from the real connecting
player, follows real server-thread game-mode changes, survives promotion of the
same adapter and becomes a stable last value after disconnect. No ordinal
mapping or synthetic parallel game-mode state is used.

| Item | Classification |
|---|---|
| Passed boundary | `GameMode.class`, `GameMode.values()` and `Player#getGameMode()` link in `BukkitPlayerCalculator` |
| Verified mapping | Minecraft Survival/Creative/Adventure/Spectator map explicitly to their Bukkit counterparts |
| New symbol/path | `AbstractLuckPermsPlugin.enable:233` -> `LPBukkitPlugin.setupContextManager:189` -> initialize `BukkitPlayerCalculator` -> `org.bukkit.World$Environment` |
| Diagnostic | `Missing API: org.bukkit.World$Environment`, `Status: NOT_IMPLEMENTED` |
| Public API? | Yes; Bukkit world-dimension classification enum |
| Exact LuckPerms use | enumerate environments and read each player's `World#getEnvironment()` for context keys |
| Category | **CORE_API** |
| CraftBukkit/NMS? | No for this symbol; known later permissible injection remains architectural |
| LuckPerms enable | incomplete; `LUCKPERMS_ENABLE_REACHED` absent |
| Phase 9.17 action | stop and document; no world/event cascade |

The pre-stop dump contained eight daemon LuckPerms workers, four OkHttp
threads, one daemon Okio watchdog and one daemon H2 writer. After normal world
saving, seven workers, three OkHttp threads and the H2 writer remained; the
non-daemon OkHttp metadata writer retained the JVM. The launcher was
interrupted after shutdown and no Forge server process remained. Regression
was `116/116`, all integration/external-plugin proofs passed, and both clean
builds were byte-identical.

## Phase 9.18 world environment and raw boot #18

The audited Spigot contract declares `NORMAL(0)`, `NETHER(-1)`, `THE_END(1)`
and `CUSTOM(-999)` in that order. It exposes deprecated `getId()` and
`getEnvironment(int)`, whose unknown result is `null`. AtlasHybrid maps the
real Minecraft dimension-key location explicitly: the three vanilla keys map
to their corresponding environments and all modded keys map to `CUSTOM`.

`BukkitPlayerCalculator#calculate` reads `Player#getWorld()`, then
`World#getEnvironment()` and `World#getName()`. It emits `dimension-type` as
`overworld`, `the_nether`, `the_end`, or the lower-case enum fallback, and
rewrites/submits the world name. `estimatePotentialContexts` additionally uses
`Environment.values()` and `Server#getWorlds()`. Its invalidation listeners
reference `PlayerJoinEvent`, `PlayerChangedWorldEvent` and
`PlayerGameModeChangeEvent`. It does not use World UUID, player location,
locale, address, client version, inventory, metadata or persistent data.

| Item | Classification |
|---|---|
| Passed boundary | `World.Environment`, its constants/values, and `World#getEnvironment()` link |
| Runtime mapping | Overworld=`NORMAL`, Nether=`NETHER`, End=`THE_END`, other dimension keys=`CUSTOM` |
| Existing context | stable World adapter and real `World#getName()` remain available |
| New symbol/path | `LPBukkitPlugin.setupContextManager:190` -> `PluginManager#registerEvents` -> `Class#getDeclaredMethods` -> `PlayerChangedWorldEvent` |
| Diagnostic | `Missing API: org.bukkit.event.player.PlayerChangedWorldEvent`, `Status: NOT_IMPLEMENTED` |
| Exact use | LOWEST listener signals a LuckPerms context refresh after a player changes worlds |
| Category | **CORE_API** |
| CraftBukkit/NMS? | The public event type does not require CraftBukkit; a correct real transition dispatch needs a separately audited Forge/Minecraft bridge |
| Still missing later | `PlayerGameModeChangeEvent`, `Server#getWorlds()` |
| LuckPerms enable | incomplete; `LUCKPERMS_ENABLE_REACHED` absent |
| Phase 9.18 action | stop and document; no event or World API cascade |

Pre-stop resources were eight daemon LuckPerms workers, four OkHttp threads,
one daemon Okio watchdog and one daemon H2 writer. After normal world-saving
shutdown, seven workers, three OkHttp threads, the watchdog and H2 writer
remained. The non-daemon OkHttp metadata writer retained the JVM until the
launcher was interrupted. No Forge server process remained. Regression was
`118/118`, all integration/external-plugin proofs passed, and both clean builds
were byte-identical.

## Phase 9.19 world-change invalidation and raw boot #19

The exact LuckPerms 5.5.81 bytecode declares
`BukkitPlayerCalculator#onWorldChange(PlayerChangedWorldEvent)`. When either
world or dimension-type contexts are enabled, it obtains `event.getPlayer()`
and calls `BukkitContextManager#signalContextUpdate(Object)`. The subsequent
calculator reads game mode, the player's destination world, environment and
rewritten world name; it does not use `getFrom()` itself. AtlasHybrid
nevertheless supplies the exact Bukkit source-world contract for all listeners.

| Item | Classification |
|---|---|
| Passed boundary | `PlayerChangedWorldEvent` links during listener reflection |
| Runtime dispatch | public Forge post-dimension event, synchronous and observational |
| Context state | stable online Player adapter; source from registry; destination visible through Player/Entity/Location; permission attachment preserved |
| Duplicate policy | one Forge changed-dimension event maps to one Bukkit event; same-world teleport maps to none |
| New symbol/path | `LPBukkitPlugin.setupContextManager:190` -> `PluginManager#registerEvents` -> `Class#getDeclaredMethods` -> `PlayerGameModeChangeEvent` |
| Diagnostic | `Missing API: org.bukkit.event.player.PlayerGameModeChangeEvent`, `Status: NOT_IMPLEMENTED` |
| Category | **CORE_API** context event |
| H2/commands | storage initialized; command registration remains complete |
| LuckPerms enable | incomplete; `setupContextManager` incomplete; `LUCKPERMS_ENABLE_REACHED` absent |
| Phase 9.19 action | stop and document; no context-event cascade |

Forge's public changed-dimension event covers successful normal dimension and
cross-level teleport paths, including modded keys. It does not cover death
respawn or the special End-credits return branch; those use a replacement
`ServerPlayer`/respawn lifecycle and are intentionally deferred rather than
faked. No Mixin, NMS hook, CraftBukkit facade or LuckPerms-specific behavior was
added.

Raw boot #19 had eight LuckPerms workers, four OkHttp threads, one Okio
watchdog and one MVStore writer before stop. After normal dimension saving the
Server thread was gone, while the same plugin-resource counts remained and the
non-daemon OkHttp metadata writer retained the JVM. The launcher was
interrupted after shutdown; no Forge server process remained.

## Phase 9.20 game-mode invalidation and raw boot #20

The exact LuckPerms 5.5.81 handler is registered at `MONITOR` with
`ignoreCancelled=true`. When game-mode contexts are enabled it calls
`BukkitContextManager#signalContextUpdate(event.getPlayer())`. Subsequent
context calculation reads the player's new snapshot after Minecraft completes
the allowed transition. Cancelled Bukkit events never invalidate the cache and
never change the real or exposed mode.

| Item | Classification |
|---|---|
| Passed boundary | `PlayerGameModeChangeEvent` links and its listener registers |
| Runtime dispatch | public cancellable Forge pre-transition event |
| Old/new semantics | Player exposes old mode during the event; event exposes requested mode |
| Cancellation | propagated to Forge and prevents the real transition |
| Duplicate policy | one real transition maps to one event; same-mode request maps to none |
| Context manager | complete; `LUCKPERMS_CONTEXT_MANAGER_REGISTERED` reached |
| Storage/commands | H2 initialized and command registration complete |
| New symbol/path | `LPBukkitPlugin.setupPlatformHooks:197` -> `InjectorSubscriptionMap.<clinit>` -> `SimplePluginManager` |
| Diagnostic | `NoClassDefFoundError: org/bukkit/plugin/SimplePluginManager` |
| Category | **ARCHITECTURAL** CraftBukkit/plugin-manager injection boundary |
| LuckPerms enable | incomplete; `LUCKPERMS_ENABLE_REACHED` absent |
| Phase 9.20 action | stop; no compatibility shape, injection or cascading implementation |

The raw boot's failed-enable inventory reported one LuckPerms worker, three
OkHttp threads, one Okio watchdog and one MVStore writer. One OkHttp metadata
writer was non-daemon. Minecraft shutdown completed and saved all worlds, but
the partial plugin resources retained the JVM until the launcher was
interrupted. No server process remained.
