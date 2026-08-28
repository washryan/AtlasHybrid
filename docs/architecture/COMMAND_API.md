# Command API and tab-completion bridge

AtlasHybrid implements the Bukkit command subset needed to execute plugin
commands and obtain their tab completions through Minecraft's Brigadier command
dispatcher. The implementation is generic and contains no plugin-specific
branches.

## Public contract

The target Bukkit contract was checked against the pinned Spigot API source for
Minecraft 1.19.2:

- `TabCompleter#onTabComplete(CommandSender, Command, String, String[])` returns
  `List<String>`;
- `TabExecutor` extends both `CommandExecutor` and `TabCompleter`;
- `Plugin` therefore participates in the `TabExecutor` contract, while
  `JavaPlugin` supplies the normal `false` command and `null` completion
  defaults;
- `PluginCommand#setTabCompleter` installs an explicit completer;
- absent an explicit completer, an executor that implements `TabCompleter` is
  used;
- a `null` result continues to the command default, while an empty list is a
  final result containing no suggestions.

`PluginCommand` defaults its executor to the owning plugin. Passing `null` to
`setExecutor` restores that owner, matching Bukkit behavior. Argument arrays are
copied before plugin callbacks. The current base `Command` fallback is an empty
list because AtlasHybrid's narrow server API does not yet expose Bukkit's full
online-player visibility model; it does not invent player-name suggestions.

## Forge and Brigadier bridge

Each registered command name and alias owns a Brigadier greedy-string argument
with a suggestion provider. The provider preserves the label actually typed,
splits arguments while retaining a trailing empty argument, creates the correct
player or console sender adapter, and forwards only the strings returned by the
Bukkit completer.

Suggestion callbacks execute on the Minecraft server thread. A request already
on that thread is handled directly; any other request is submitted to the server
executor and completes its Brigadier future after the Bukkit callback finishes.

## Phase 9.4 validation

- `53/53` automated tests pass, including interface inheritance, registration,
  explicit override, executor fallback, `null` versus empty semantics, argument
  isolation, aliases, player sender and console sender.
- The dedicated-server proof requests `/atlas <TAB>` from both console and a
  Forge fake player through the real Brigadier dispatcher. Both return exactly
  `alpha`, `beta`, `gamma`, and `TAB_COMPLETION_OK` is logged exactly once.
- All previous lifecycle, event, scheduler, permission, service, command,
  teleport, cancellation, diagnostic and shutdown markers remain single-shot.
- WelcomeMessage and WarpPlugin remain `FULL` in their pinned regression scopes.
- Two clean builds produce byte-identical runtime, test-plugin and test-mod
  artifacts, including ForgeGradle's reobfuscated output.

A physical client tab-key check was not performed in this automated phase. The
proof uses the production server dispatcher rather than a mocked command map.
