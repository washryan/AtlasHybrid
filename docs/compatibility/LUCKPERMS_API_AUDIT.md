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
| Plugin bootstrap/lifecycle | `JavaPlugin`, `Plugin`, `PluginDescriptionFile`, STARTUP load, `onLoad/onEnable/onDisable` | PARTIAL | CORE_API | Basic lifecycle exists, but the loader constructor links additional missing Bukkit types before `onLoad`. |
| Console and sender hierarchy | `ConsoleCommandSender`, `RemoteConsoleCommandSender`, `BlockCommandSender`, `ProxiedCommandSender`, conversations, permission methods | MISSING | CORE_API | The first raw-boot failure is `ConsoleCommandSender`. Adding only this interface would not make startup viable. |
| Server facade | name/version/Bukkit version, online/offline player lookup, operators, scheduler, plugin manager, services, messenger | PARTIAL | CORE_API | AtlasHybrid has a narrow `Server`; offline players, operators, services and messenger are absent. |
| Commands | `PluginCommand`, `TabExecutor`, `CommandMap`, sender permission checks, server command events | PARTIAL | CORE_API | Basic command execution exists. Tab completion, permission-aware senders, command events and the command map contract are missing. |
| Configuration | `YamlConfiguration`, `ConfigurationSection`, typed maps/lists, section traversal | PARTIAL | CORE_API | Current deterministic YAML supports tested scalar/list/location paths, not Bukkit's section model required by `BukkitConfigAdapter`. |
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
