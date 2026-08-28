# JavaPlugin bootstrap contract

AtlasHybrid parses `plugin.yml` before loading a plugin main class. The parsed
identity, server facade, data directory and one stable JUL logger are therefore
available before the plugin constructor runs. This matches the relevant Bukkit
contract: its plugin classloader initializes the `JavaPlugin` superclass before
subclass field initializers and constructor bodies execute.

## API phases

| Method | Phase | AtlasHybrid behavior |
|---|---|---|
| `getLogger()` | `CONSTRUCTOR_SAFE` | Returns the final plugin logger; identity is unchanged after `atlasInitialize`. |
| `getName()` / `getDescription()` | `CONSTRUCTOR_SAFE` | Uses metadata parsed before class loading. |
| `getDataFolder()` | `CONSTRUCTOR_SAFE` | Returns the final path; the directory need not exist yet. |
| `getServer()` | `CONSTRUCTOR_SAFE` | Returns the already-created server facade. |
| `getConfig()` | `CONSTRUCTOR_SAFE` | May lazily read `config.yml` using the final data directory. |
| `isEnabled()` | `CONSTRUCTOR_SAFE` | Returns `false` until enable begins. |
| `getCommand()` | `POST_LOAD_ONLY` | Commands are registered after construction and before `onLoad`; early access raises `PLUGIN_BOOTSTRAP_PHASE` / `AVAILABLE_LATER`. |

None of these methods is `POST_ENABLE_ONLY`. APIs whose semantics require an
enabled plugin remain guarded by their owning subsystem.

## Ownership and lifetime

`AtlasPluginClassLoader` owns an immutable bootstrap context. It activates that
context in a thread-local construction scope around class initialization and
main-constructor invocation, and clears it in `finally`. `JavaPlugin` walks its
classloader ancestry to find the provider, allowing a child jar-in-jar loader
without granting context from an unrelated plugin loader.

Multiple `JavaPlugin` instances created inside the same active, owned scope see
the same plugin identity and logger. This is a generic ownership rule, not a
LuckPerms exception. Construction outside the active scope is unbound and an
early context-dependent call receives a structured phase error instead of
`null` or an incidental `NullPointerException`.

The classloader-owned design avoids a permanent global context. The short
thread-local activation prevents cross-plugin leakage during sequential or
future parallel loading, while the immutable owner context gives nested child
classloaders a verifiable boundary.

## Lifecycle

The construction context does not call `onLoad`, register commands, enable the
plugin or create scheduler ownership early. After the constructor returns,
`atlasInitialize` verifies that the final context is the same context already
observed during construction. Normal load, enable, disable and cleanup remain
single-shot and ordered by `PluginRuntime`.

## Phase 9.3 validation

- `47/47` automated tests passed, including success/failure cleanup, sequential
  and parallel isolation, descendant ownership and logger continuity.
- The Forge integration proof passed with `EARLY_LOGGER_OK` exactly once and all
  earlier lifecycle, command, event, scheduler, permission and shutdown markers
  preserved.
- WelcomeMessage and WarpPlugin external regressions remained `FULL` within
  their previously validated scopes.
- Clean Builds A and B produced identical byte-for-byte runtime, test-plugin and
  test-mod artifacts.
- LuckPerms raw boot #3 passed the logger/server construction boundary and
  stopped at the next `CORE_API` symbol, `org.bukkit.command.TabExecutor`.
