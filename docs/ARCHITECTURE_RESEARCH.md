# AtlasHybrid — Architectural Research

Status: Phase 1 complete (architecture gate)

Audit date: 2026-08-26

Target: Minecraft 1.19.2, Forge 43.x, Java 17

Project version planned for the first proof: `0.1.0-alpha`

## 1. Scope and method

This is a clean-room architectural study. It is not a source-code import plan.
The audit used public upstream documentation, repository structure, build files,
license files, and selected integration points. AtlasHybrid must retain its own
repository, history, naming, documentation, and implementation.

The conclusions below distinguish three kinds of evidence:

- **Observed upstream behavior**: backed by a linked source or official document.
- **AtlasHybrid decision**: a project choice derived from the evidence.
- **Deferred question**: deliberately excluded from the first proof.

No Mohist, Arclight, Magma, CraftBukkit, Spigot, or Paper source is to be copied
into AtlasHybrid without a separate provenance and license review.

## 2. Executive conclusion

The smallest credible architecture that can run a real, deliberately small
Bukkit plugin next to native Forge mods is:

1. Keep the official Forge 43.x server and ModLauncher bootstrap intact.
2. Package the AtlasHybrid runtime as a dedicated-server Forge mod.
3. Implement a clean-room, binary-compatible **subset** of the Bukkit API under
   `org.bukkit`, sufficient for the test plugin only.
4. Discover `plugins/*.jar`, parse `plugin.yml`, topologically order hard
   dependencies, and create one closeable classloader per plugin.
5. Bridge Forge server events to AtlasHybrid Bukkit events.
6. Bridge Forge's Brigadier command registration to the minimal Bukkit command
   abstraction.
7. Run the first scheduler only from the Forge server tick on the logical server
   thread.
8. Fail unsupported API calls explicitly and record them in diagnostics.

This design intentionally does **not** claim general Bukkit, Spigot, or Paper
compatibility. It proves the runtime seam with one real plugin JAR and one real
Forge mod JAR. Plugins that use CraftBukkit internals, NMS, Paper API, broad
Bukkit surfaces, bytecode generated against a different API shape, or unsupported
transitive plugin libraries are outside the first milestone.

## 3. What the upstreams actually do

### 3.1 Forge 1.19.2

Forge owns the launch and transformation pipeline. Its 1.19.2
`ForgeServerLaunchHandler` declares the `forgeserver` launch target, adds the
patched Minecraft server artifact, and adds the Forge universal artifact as a
mod. `ServerLifecycleHooks` then publishes server about-to-start, starting,
started, stopping, and stopped events on the Forge event bus.

Evidence:

- [ForgeServerLaunchHandler at the audited commit](https://github.com/MinecraftForge/MinecraftForge/blob/66e4ae51423e2dde5edd5ffea4a13dfab4621192/fmlloader/src/main/java/net/minecraftforge/fml/loading/targets/ForgeServerLaunchHandler.java)
- [ServerLifecycleHooks at the audited commit](https://github.com/MinecraftForge/MinecraftForge/blob/66e4ae51423e2dde5edd5ffea4a13dfab4621192/src/main/java/net/minecraftforge/server/ServerLifecycleHooks.java)
- [Forge 1.19.2 getting-started documentation](https://docs.minecraftforge.net/en/1.19.2/gettingstarted/)
- [Forge 1.19.2 repository branch](https://github.com/MinecraftForge/MinecraftForge/tree/1.19.2)

Forge documentation states that 1.19.2 development uses Mojang official mappings;
production transformations still require care around SRG-named members (for
example in access transformers). A ForgeGradle build must therefore own all
Minecraft-facing compilation and reobfuscation. Plugin code must not link directly
to mapped Minecraft classes in the first proof.

**AtlasHybrid decision:** do not replace ModLauncher, do not reimplement Forge,
and do not ship a patched Minecraft server. AtlasHybrid starts as a regular Forge
mod placed in `mods/`. A small launcher/setup script may later install and invoke
the official Forge server, but it must remain a wrapper, not a competing loader.

### 3.2 Bukkit API and CraftBukkit

Bukkit is primarily an API contract. CraftBukkit is the server implementation and
the adapter between Bukkit abstractions and Minecraft server objects. Those roles
must not be conflated.

Observed Bukkit mechanics relevant to the first proof:

- `SimplePluginManager` scans candidate files and computes hard and soft
  dependency order before loading.
- `JavaPluginLoader` validates hard dependencies and creates a plugin
  classloader.
- `PluginClassLoader` refuses to define `org.bukkit.*` and `net.minecraft.*`
  itself, so those namespaces come from the server side of the classloader
  boundary.
- Listener registration reflects over event-handler methods; dispatch is stored
  per event in `HandlerList`, ordered by priority and baked to an array.
- Plugin enable/disable is a server-owned state transition that invokes the
  plugin lifecycle and emits lifecycle events.

Evidence:

- [SimplePluginManager](https://github.com/Bukkit/Bukkit/blob/f210234e59275330f83b994e199c76f6abd41ee7/src/main/java/org/bukkit/plugin/SimplePluginManager.java)
- [JavaPluginLoader](https://github.com/Bukkit/Bukkit/blob/f210234e59275330f83b994e199c76f6abd41ee7/src/main/java/org/bukkit/plugin/java/JavaPluginLoader.java)
- [PluginClassLoader](https://github.com/Bukkit/Bukkit/blob/f210234e59275330f83b994e199c76f6abd41ee7/src/main/java/org/bukkit/plugin/java/PluginClassLoader.java)
- [HandlerList](https://github.com/Bukkit/Bukkit/blob/f210234e59275330f83b994e199c76f6abd41ee7/src/main/java/org/bukkit/event/HandlerList.java)
- [PluginDescriptionFile](https://github.com/Bukkit/Bukkit/blob/f210234e59275330f83b994e199c76f6abd41ee7/src/main/java/org/bukkit/plugin/PluginDescriptionFile.java)

**AtlasHybrid decision:** reimplement only the required public API shape and
behavior from specifications and black-box expectations. Do not import
CraftBukkit. Minecraft-backed wrapper implementations belong in AtlasHybrid's own
adapter packages, not in a copied CraftBukkit tree.

### 3.3 Spigot and Paper

Spigot builds on Bukkit/CraftBukkit and distributes its build workflow through
BuildTools. Paper 1.19-era sources are patch-based and inherit upstream Bukkit,
CraftBukkit, and Spigot behavior. Paper adds a much broader API, server patches,
performance changes, plugin loading behavior, and implementation details.

Paper's current plugin documentation is useful as a design reference: explicit
classloader dependencies improve isolation and cycles should be rejected. It is
not a compatibility target for AtlasHybrid 0.1.

Evidence:

- [Spigot BuildTools documentation](https://www.spigotmc.org/wiki/buildtools/)
- [Spigot source host](https://hub.spigotmc.org/stash/projects/SPIGOT)
- [Paper archived source layout](https://github.com/PaperMC/Paper-archive/tree/3e933fe49d526b6d5e671cb6314ade7e95d69d86)
- [Paper license inheritance note](https://github.com/PaperMC/Paper-archive/blob/3e933fe49d526b6d5e671cb6314ade7e95d69d86/LICENSE.md)
- [Paper plugin classloading isolation documentation](https://docs.papermc.io/paper/dev/getting-started/paper-plugins/#classloading-isolation)

**AtlasHybrid decision:** no Paper patches, Paper plugin loader, Paper API, or
Paper compatibility statement in 0.1. Bukkit-style `plugin.yml` is the only plugin
format in scope.

### 3.4 Arclight

Arclight is the most directly relevant public 1.19.2 reference. The audited
`GreatHorn` line uses a sophisticated bootstrap that injects modules and a
ModLauncher launch plugin, extracts an embedded common mod, transforms Bukkit and
Minecraft classes with Mixins, remaps plugin classes, and bridges many Forge
events to Bukkit/CraftBukkit events.

The breadth of its core mixin list demonstrates that broad plugin compatibility
requires intervention across networking, worlds, entities, commands, inventories,
and server lifecycle—not just forwarding a few Forge events. Its block-break
dispatcher also demonstrates the safe cancellation pattern: initialize the Bukkit
event from Forge state, dispatch it, then write cancellation and experience back
to the Forge event.

Evidence:

- [ApplicationBootstrap](https://github.com/IzzelAliz/Arclight/blob/ae81c7b50461414f1091de5eb72f72c4e65f4155/arclight-forge/src/main/java/io/izzel/arclight/boot/application/ApplicationBootstrap.java)
- [ModBootstrap](https://github.com/IzzelAliz/Arclight/blob/ae81c7b50461414f1091de5eb72f72c4e65f4155/arclight-forge/src/main/java/io/izzel/arclight/boot/mod/ModBootstrap.java)
- [PluginClassLoaderMixin](https://github.com/IzzelAliz/Arclight/blob/ae81c7b50461414f1091de5eb72f72c4e65f4155/arclight-common/src/main/java/io/izzel/arclight/common/mixin/bukkit/PluginClassLoaderMixin.java)
- [BlockBreakEventDispatcher](https://github.com/IzzelAliz/Arclight/blob/ae81c7b50461414f1091de5eb72f72c4e65f4155/arclight-common/src/main/java/io/izzel/arclight/common/mod/server/event/BlockBreakEventDispatcher.java)
- [Core mixin inventory](https://github.com/IzzelAliz/Arclight/blob/ae81c7b50461414f1091de5eb72f72c4e65f4155/arclight-common/src/main/resources/mixins.arclight.core.json)
- [Arclight GPL-3.0 license](https://github.com/IzzelAliz/Arclight/blob/ae81c7b50461414f1091de5eb72f72c4e65f4155/LICENSE)

**AtlasHybrid decision:** adopt none of Arclight's injection code. Learn from its
separation of bootstrap/common/platform adapters and from its bidirectional event
state propagation. Defer module injection, runtime remapping, CraftBukkit mixins,
and broad compatibility until a concrete plugin requires them.

### 3.5 Mohist and Magma

The currently discoverable official Mohist repository is archived under
`MohistMC-Archives/Mohist` and its available main line targets older Minecraft.
The currently discoverable `magmafoundation/Magma-Forge` default branch also
targets 1.12.2. They remain useful historical evidence that hybrid runtimes
combine Forge patching with Bukkit/CraftBukkit/Spigot layers, but they are not a
safe source of 1.19.2 implementation truth for this project.

Evidence:

- [Mohist archived repository](https://github.com/MohistMC-Archives/Mohist/tree/748fa196c8017440634adc77b136f22f0c2c9bcd)
- [Magma Forge repository](https://github.com/magmafoundation/Magma-Forge/tree/bd235d1301fad08007dbae28da8448c7261f5c41)

**AtlasHybrid decision:** do not use forks, mirrors, or binary decompilation to
fill the missing 1.19.2 history. Record these projects as studied at a structural
level only. Any future source-level use requires an exact commit, exact file,
license, author notice, and a written reason.

## 4. Component audit

| Concern | Indispensable for proof | First implementation | Deferred |
|---|---:|---|---|
| Forge bootstrap | Yes | Official Forge installer/launcher unchanged; AtlasHybrid is a mod | Custom launch service or bootstrap injection |
| Forge patching | No | Forge's own patched server only | AtlasHybrid patches/mixins if an event gap is proven |
| Bukkit API | Yes | Clean-room minimal binary surface | Full Bukkit API |
| CraftBukkit | No | Own adapters around Forge/Minecraft objects | NMS/CraftBukkit compatibility layer |
| Spigot/Paper | No | None | APIs, patches, timings, config, plugin formats |
| Plugin metadata | Yes | Strict `plugin.yml` parser | Commands/permissions metadata beyond proof |
| Plugin classloader | Yes | One closeable loader per plugin; protected namespaces; explicit dependency edges | Runtime bytecode remapping and library resolver |
| Event system | Yes | Listener registry plus three event types | Full HandlerList parity and all priorities if not needed |
| Commands | Yes | Brigadier root bridged to Bukkit executor/sender | Complete Brigadier tree projection/tab completion |
| Scheduler | Yes | Tick-indexed main-thread queue | Async workers and complex repeating tasks |
| Permissions | Minimal | Operator/default check only where required | Bukkit permission graph and Forge permission integration |
| Lifecycle | Yes | Load, enable after server start, disable before stop | Hot reload/unload |
| Mappings | Yes at boundary | ForgeGradle owns Minecraft mappings; plugins see no NMS | Plugin NMS remapper |
| Mixins/patches | No | Zero for proof unless an event cannot be expressed safely | Narrow, documented injections |
| Diagnostics | Yes | Structured unsupported-call and plugin failure records | Static compatibility scanner |

## 5. Proposed architecture after audit

The original module list is adjusted to separate stable API code from
Minecraft-version-specific integration:

```text
AtlasHybrid/
├── bukkit-api/             # clean-room minimal org.bukkit surface; no Forge imports
├── plugin-loader/          # metadata, dependency graph, classloaders, lifecycle
├── runtime-core/           # event bus, command model, scheduler, diagnostics contracts
├── platform-forge-1.19.2/  # the Forge mod and all net.minecraft/net.minecraftforge adapters
├── test-plugin/            # AtlasHybridTestPlugin.jar
├── test-mod/               # AtlasHybridTestMod.jar
├── diagnostics/            # report sinks and unsupported API exception
├── docs/
├── run/                    # ignored, isolated server runtime
├── build.gradle(.kts)
└── settings.gradle(.kts)
```

`bootstrap/`, `command-bridge/`, `event-bridge/`, and `scheduler/` need not all be
separate Gradle projects on day one. Too many tiny build modules would obscure the
actual classloader boundaries. The important compile-time boundary is:

```text
test-plugin -> bukkit-api
plugin-loader -> bukkit-api + runtime-core
platform-forge-1.19.2 -> plugin-loader + runtime-core + Forge/Minecraft
test-mod -> Forge/Minecraft only
```

The `bukkit-api` module must never depend on Forge, Minecraft, or AtlasHybrid
implementation packages. This makes plugin compilation deterministic and exposes
accidental implementation leakage during build time.

## 6. Bootstrap and lifecycle design

Proposed lifecycle mapping:

| Forge/server point | AtlasHybrid action |
|---|---|
| Forge mod construction | Register listeners only; no plugin execution |
| `ServerAboutToStartEvent` | Bind the server adapter and create runtime state |
| `ServerStartingEvent` | Scan/validate metadata, resolve dependency graph, instantiate plugins, call `onLoad()` |
| command registration event | Register AtlasHybrid/plug-in Brigadier command roots |
| `ServerStartedEvent` | Call `onEnable()` in dependency order |
| server tick END phase | Execute due synchronous tasks |
| player login/logout and block break Forge events | Construct and dispatch corresponding Bukkit events |
| `ServerStoppingEvent` | Reject new tasks; call `onDisable()` in reverse enable order |
| `ServerStoppedEvent` | Close classloaders and clear all static runtime references |

The exact command registration event occurs before `ServerStartingEvent` in some
Forge sequences. Therefore plugin command declarations must be collected early or
the bridge must register one stable AtlasHybrid Brigadier root whose dispatcher
delegates to a mutable command map. The first proof should use the stable root
approach for `/atlas`; arbitrary plugin command registration can follow only after
an integration test confirms lifecycle ordering.

## 7. Plugin metadata and dependency graph

Initial accepted keys:

- required: `name`, `version`, `main`
- optional: `api-version`, `description`, `authors`, `depend`, `softdepend`

Validation rules:

1. JAR is a regular readable file and has exactly one root `plugin.yml`.
2. YAML parsing uses safe construction with aliases and input size bounded.
3. Plugin name is normalized only for lookup; the declared spelling is retained.
4. Duplicate names are fatal for both candidates.
5. Main class must be a valid binary class name and must extend `JavaPlugin`.
6. Missing hard dependency prevents the dependent plugin from loading.
7. Hard dependency cycles are fatal and name the entire cycle.
8. Soft dependencies influence order only when present; soft cycles are broken
   deterministically and logged.
9. Unknown keys are retained for diagnostics but ignored by the alpha runtime.

Use Kahn topological sorting with lexical filename/name tie-breaking so boot order
is reproducible across file systems.

## 8. Classloader model

Each plugin gets one `URLClassLoader`-derived loader and one protection domain.
The policy is:

- parent-first for `java.*`, `javax.*`, `jdk.*`, `sun.*`, `org.bukkit.*`,
  AtlasHybrid public API, `net.minecraft.*`, and `net.minecraftforge.*`;
- child-first for the plugin's own classes and embedded libraries;
- dependency lookup only through declared hard dependencies, then present soft
  dependencies;
- no global scan of unrelated plugin classloaders;
- reject any plugin JAR that contains protected API namespaces;
- close every loader during shutdown, remove listeners/tasks/commands owned by
  that plugin, and discard caches keyed by plugin classes.

This is stricter than legacy Bukkit's global class lookup. That difference is
intentional because global lookup creates accidental coupling and class retention.
It must be called out as an alpha limitation.

## 9. Event bridge semantics

The bridge is not a blind mirror. Every event adapter defines:

- source Forge event and phase;
- server-thread requirement;
- field conversion rules;
- whether Bukkit cancellation is semantically reversible;
- write-back behavior;
- reentrancy guard, if one platform action can trigger the other again.

Initial mapping:

| Bukkit event | Forge source | Cancellation |
|---|---|---|
| `PlayerJoinEvent` | player logged-in event | Not cancellable |
| `PlayerQuitEvent` | player logged-out event | Not cancellable |
| `BlockBreakEvent` | `BlockEvent.BreakEvent` | Bidirectional; final Bukkit state is written back to Forge |

For block breaking, AtlasHybrid must subscribe with cancellation visibility,
initialize the Bukkit event from the current Forge cancellation state, dispatch
on the server thread, and preserve cancellation additively: an earlier Forge/mod
cancellation cannot be undone by a Bukkit listener, while a Bukkit cancellation
is written back to Forge. An integration test must verify that the world block remains and
no drops/experience are produced when canceled.

Join/quit events expose a minimal immutable player view initially. Methods that
would mutate game state without an implemented adapter throw a compatibility
exception instead of returning fabricated values.

## 10. Commands, scheduler, and permissions

### Commands

Minecraft/Forge uses Brigadier; Bukkit exposes `Command`, `CommandSender`, and a
command map/executor model. The bridge must translate the source into a sender
adapter, pass label and arguments without reparsing quoted text when Brigadier has
already parsed it, and translate the boolean result into a Brigadier result code.

For the proof, AtlasHybrid owns `/atlas` and `/atlas info`. The test plugin should
register their executors through the minimal Bukkit API, while the Forge adapter
owns the actual Brigadier node.

### Scheduler

`runTask` queues for the next safe server tick. `runTaskLater` schedules at
`currentTick + max(1, delay)`. Only the END phase of the logical server tick drains
the queue. Tasks execute in `(dueTick, sequence)` order. Plugin disable cancels all
owned pending tasks. No async scheduler is exposed.

### Permissions

Permissions are not actually needed to prove `/atlas`. The first sender contract
may expose `isOp()` and a conservative `hasPermission`: console true, players true
only for explicitly supported/registered defaults or operators. Full Bukkit
permission attachments and Forge PermissionAPI integration are deferred.

## 11. Major Forge ↔ Bukkit conflict zones

1. **Double event delivery:** a Bukkit action may call Minecraft code that emits a
   Forge event, which may be bridged back into Bukkit. Every mutable bridge needs
   origin/reentrancy tracking.
2. **Cancellation timing:** some Forge notifications occur after mutation or are
   informational. Only pre-action, cancellable sources may support Bukkit
   cancellation.
3. **Registry divergence:** mods add blocks, items, entities, dimensions, and
   capabilities that vanilla-era Bukkit enums cannot represent faithfully.
4. **Thread ownership:** Forge lifecycle setup may be parallel; Bukkit plugin
   enable, events, commands, and the initial scheduler expect one server thread.
5. **Classloading/modules:** ModLauncher uses transformed module classloaders;
   plugin URL classloaders must delegate server/API namespaces and must not define
   duplicate Minecraft or Bukkit classes.
6. **Mappings/NMS:** Bukkit plugins that reference versioned CraftBukkit or NMS
   names need runtime remapping and implementation classes. The proof explicitly
   rejects them.
7. **Command ownership:** Forge/vanilla and plugins can register the same literal;
   precedence, namespacing, permissions, suggestions, and reload semantics differ.
8. **Lifecycle order:** Forge mod loading, datapack reload, command construction,
   world creation, and player acceptance do not align exactly with Bukkit phases.
9. **World/entity wrappers:** identity must be stable. Creating a new Bukkit
   wrapper for every access breaks equality, metadata, and plugin caches.
10. **Shutdown leaks:** static HandlerLists, scheduled tasks, loggers, threads,
    and cross-plugin class references can keep closed plugin loaders alive.
11. **Mixin collisions:** broad injections can target the same method as mods.
    Arclight's issue history and mixin inventory show this is a practical, not
    theoretical, risk.
12. **Behavioral assumptions:** plugins often rely on CraftBukkit quirks beyond
    the published API. Passing linkage does not imply semantic compatibility.

## 12. Mappings and transformation policy

- Compile `platform-forge-1.19.2` with the Forge 1.19.2 toolchain and Mojang
  mappings selected by ForgeGradle.
- Reobfuscate only the Forge-facing runtime artifact using ForgeGradle.
- Keep `bukkit-api`, `runtime-core`, and test plugin free of Minecraft symbols.
- Do not remap plugin bytecode in 0.1.
- Detect references to `org.bukkit.craftbukkit` or `net.minecraft` during plugin
  class definition when practical; report `NMS_NOT_SUPPORTED` with the offending
  class rather than attempting an unsafe fallback.
- Do not add access transformers or Mixins until a named integration test proves
  that a public Forge hook is insufficient. Every future transformer requires a
  version-pinned target, conflict analysis, and fallback diagnostic.

## 13. Diagnostics contract

All compatibility failures should produce a structured record:

```text
[AtlasHybrid Compatibility]
Plugin: ExamplePlugin
Unsupported API: org.bukkit.entity.Player#someMethod
Module: bukkit-player
Status: NOT_IMPLEMENTED
Runtime: 0.1.0-alpha
Minecraft: 1.19.2
Forge: <detected version>
```

Records need stable codes such as `NOT_IMPLEMENTED`, `NMS_NOT_SUPPORTED`,
`PAPER_API_NOT_SUPPORTED`, `MISSING_DEPENDENCY`, `DEPENDENCY_CYCLE`,
`PROTECTED_NAMESPACE`, and `WRONG_THREAD`. The in-memory collector should count
supported bridge calls and unsupported calls per plugin so a later compatibility
report does not require redesign.

## 14. License audit and guardrails

This section is engineering guidance, not legal advice.

| Upstream | Observed license/status | AtlasHybrid rule |
|---|---|---|
| Minecraft Forge 1.19.2 | LGPL-2.1, with specific notices and non-transitive MCP data restrictions in its license file | Depend on official Forge artifacts; retain notices; do not redistribute MCP data or a patched Minecraft server |
| Bukkit API | GPL-3.0 repository | No source copying in the clean-room API; if any code is incorporated, AtlasHybrid distribution must be reviewed for GPL obligations |
| CraftBukkit/Spigot | Upstream-derived GPL distribution/build constraints; source is distributed through Spigot's workflow | Do not import or bundle for the proof |
| Paper archive | GPL-3.0 inherited from Bukkit/Spigot, with some contributor code optionally MIT | Do not import patches/API for the proof; file-level provenance would be required later |
| Arclight | GPL-3.0 | Architecture study only; no code copied |
| Archived Mohist | GPL-3.0 repository metadata/license | Architecture study only; no code copied |
| Magma Forge | Repository license detection is `NOASSERTION`; exact file-level terms must be inspected before any use | Architecture study only; treat reuse as blocked |

Primary license evidence:

- [Forge 1.19.2 LICENSE.txt](https://github.com/MinecraftForge/MinecraftForge/blob/66e4ae51423e2dde5edd5ffea4a13dfab4621192/LICENSE.txt)
- [Bukkit GPL-3.0 license](https://github.com/Bukkit/Bukkit/blob/f210234e59275330f83b994e199c76f6abd41ee7/LICENSE.txt)
- [Paper license note](https://github.com/PaperMC/Paper-archive/blob/3e933fe49d526b6d5e671cb6314ade7e95d69d86/LICENSE.md)
- [Arclight license](https://github.com/IzzelAliz/Arclight/blob/ae81c7b50461414f1091de5eb72f72c4e65f4155/LICENSE)
- [Minecraft EULA](https://www.minecraft.net/eula)

Recommended initial AtlasHybrid license: **GPL-3.0-only**, subject to owner/legal
confirmation before publication. This is the conservative choice for an
implementation of a GPL API ecosystem and leaves room for carefully attributed
GPL-compatible incorporation later. It does not permit bundling Minecraft code or
ignore Forge/MCP notices. Do not add a final `LICENSE` file until the project owner
accepts this choice; record the decision in `docs/LICENSE_NOTES.md` first.

## 15. What is indispensable vs. reimplementable

Indispensable external components:

- official Minecraft 1.19.2 server obtained through the authorized Forge flow;
- official Forge 43.x loader/runtime;
- Java 17;
- ForgeGradle/mapping toolchain for the Forge-facing artifact.

Reimplementable AtlasHybrid components:

- minimal Bukkit API surface;
- plugin metadata parser and dependency resolver;
- plugin classloader policy;
- plugin lifecycle manager;
- event dispatcher and Forge adapters;
- command adapter;
- synchronous scheduler;
- diagnostics and compatibility counters.

Components deliberately not reimplemented:

- Forge mod discovery/loading and transformation;
- Minecraft server internals;
- CraftBukkit as a whole;
- Paper/Spigot patches;
- NMS remapping in the first proof.

## 16. First-proof acceptance criteria

The architecture gate permits implementation only if the proof remains bounded to
these outcomes:

1. An official Forge 1.19.2/43.x dedicated server boots on Java 17.
2. `AtlasHybridTestMod.jar` is discovered by Forge and logs its successful load.
3. The AtlasHybrid runtime mod discovers `plugins/AtlasHybridTestPlugin.jar`.
4. Metadata validation and deterministic dependency ordering pass.
5. The plugin gets `onLoad`, `onEnable`, and `onDisable` exactly once.
6. `/atlas` and `/atlas info` execute on the server thread.
7. Join, quit, and block-break events reach registered test listeners exactly once.
8. Configured block-break cancellation prevents the break and its side effects.
9. A delayed task runs at or after its due tick on the server thread.
10. An unsupported API probe emits a structured diagnostic.
11. Unit tests cover parser, ordering, lifecycle, command dispatch, event dispatch,
    cancellation propagation, scheduler order/cancellation, and diagnostics.
12. Runtime output is separate from source under ignored `run/`.

## 17. Explicit non-goals for 0.1

- Drop-in compatibility with arbitrary Bukkit/Spigot/Paper plugins.
- CraftBukkit or versioned NMS packages.
- Paper API/plugin format, Folia, Fabric, NeoForge, or Minecraft 1.20+.
- Async scheduling, hot reload, plugin downloader, or compatibility hacks.
- A single fat JAR containing Minecraft, Forge, or copied upstream server code.

## 18. Architecture risks and go/no-go tests

| Risk | Earliest falsification test | Response if it fails |
|---|---|---|
| AtlasHybrid API classes are invisible to plugin loaders under ModLauncher | Load a plugin whose main class extends minimal `JavaPlugin` | Adjust loader parent/module reads; do not inject Forge internals without a new ADR |
| Forge command event occurs before plugin enable | Register stable `/atlas` bridge node and attach executor later | Keep one bridge root; document limited dynamic registration |
| Block break cancellation fires too late or duplicates another path | Dedicated-server integration test checks block, drops, XP, and invocation count | Find a narrower Forge hook; only then evaluate a minimal Mixin |
| Plugin loader leaks at shutdown | Weak-reference GC test after unregister/close | Remove registries/caches retaining plugin classes |
| Minimal API accidentally promises broad compatibility | Linkage test against only the test plugin plus README matrix | Keep unsupported methods absent or explicitly diagnostic; do not claim Bukkit complete |
| License boundary becomes ambiguous | Automated source/provenance inventory before packaging | Block release until `THIRD_PARTY.md` and `LICENSE_NOTES.md` are resolved |

## 19. Architecture decision records

### ADR-001 — AtlasHybrid is a Forge mod for the first proof

Accepted. It preserves Forge as the official loader and gives direct access to
public lifecycle, command, player, tick, and block events. A custom launcher may
wrap installation later but is not part of the compatibility core.

### ADR-002 — No CraftBukkit/Paper source in the first proof

Accepted. The proof uses a clean-room minimal API and AtlasHybrid-owned adapters.
This constrains compatibility but keeps the first implementation reviewable.

### ADR-003 — No Mixins or AtlasHybrid Minecraft patches by default

Accepted. Public Forge events are sufficient for the requested proof. A Mixin is
allowed only after an integration test demonstrates an unbridgeable semantic gap.

### ADR-004 — Strict declared-dependency classloader isolation

Accepted. Protected namespaces are parent-owned and plugins may see only their own
classes plus declared dependencies. This deliberately differs from permissive
legacy global lookup.

### ADR-005 — GPL-3.0-only is proposed, not yet finalized

Pending owner approval. Implementation may proceed privately with provenance
tracking, but public release is blocked until `LICENSE`, `LICENSE_NOTES.md`, and
`THIRD_PARTY.md` are reviewed.

## 20. Implementation gate result

**GO, with constraints.** The first functional proof is technically viable without
cloning a hybrid upstream and without replacing Forge. Implementation must follow
ADRs 001–004, remain limited to the acceptance criteria above, and stop if it
requires CraftBukkit/Paper source, runtime NMS remapping, or broad Minecraft
patching. Such a discovery requires updating this audit before code is added.
