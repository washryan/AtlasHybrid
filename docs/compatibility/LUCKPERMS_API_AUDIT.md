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
