# WelcomeMessage 1.0 compatibility report

## Result

**FULL** for the plugin's advertised join-message behavior.

The unmodified external plugin was discovered, loaded and enabled by AtlasHybrid. A real player joined with a clean Forge 1.19.2 client and received the configured `Welcome!` message. The player then disconnected normally. Two controlled server boots also completed clean shutdown, and the plugin loaded again after restart.

## Tested artifact

- Upstream repository: <https://github.com/Ninjananas/WelcomeMessage>
- Version: `1.0`
- Source commit: `998befeca67ee9f533a1cd1ce58368d0a379ebd3`
- License: MIT
- Built with: JDK 17 and Spigot API `1.19.2-R0.1-20221207.161214-43`
- Artifact SHA-256: `F66F0332BCDAB792083AAF094DE0C30A025EE843FF828D629CCD7FD864D3D7D1`

The source and JAR are not part of AtlasHybrid. The artifact was used only from ignored local test state under `run-compat/`.

## Static API audit

The pre-boot audit found every referenced Bukkit symbol already supported. There were no `NOT_IMPLEMENTED` or `UNKNOWN` entries. The complete symbol table is in [the selection record](FIRST_EXTERNAL_PLUGIN.md).

No Bukkit API, runtime behavior, event bridge, command implementation, scheduler, plugin loader or diagnostic code was changed for this result.

## Runtime evidence

| Criterion | Result | Evidence |
|---|---|---|
| Isolated profile | PASS | `runCompatServer` used `run-compat/`; AtlasHybridTestPlugin and AtlasHybridTestMod were excluded |
| `plugin.yml` and main class | PASS | Metadata parsed and `WelcomeMessage v1.0` reached load and enable lifecycle |
| Classloader/linkage | PASS | Main class instantiated with no linkage or classloading error |
| `onLoad` phase | PASS | Inherited no-op lifecycle completed before the runtime's single load record |
| `onEnable` | PASS | Exactly one `Loaded plugin WelcomeMessage v1.0` and one `Enabled plugin WelcomeMessage v1.0` record per successful boot |
| Default config | PASS | `plugins/WelcomeMessage/config.yml` was created with `message: Welcome!` and read at runtime |
| Real `PlayerJoinEvent` | PASS | One human join was recorded in the external-plugin session |
| `Player#sendMessage` | PASS | The joining user confirmed `Welcome!` appeared in the client chat |
| Real player quit | PASS | One matching human quit was recorded |
| Errors/diagnostics | PASS | Zero `ERROR`/`FATAL` records and no AtlasHybrid unsupported-API diagnostic in successful sessions |
| Clean shutdown | PASS | Two controlled `stop` runs saved all dimensions and Gradle completed successfully |
| Restart | PASS | The plugin was loaded and enabled once again after a clean stop, then stopped cleanly |

The first human-session launcher did not retain interactive standard input, so that process was ended after the join/quit evidence was captured and is not used as shutdown evidence. Shutdown and restart evidence comes only from the subsequent interactive runs.

## Lifecycle interpretation

WelcomeMessage does not override `onLoad`, so there is no plugin-authored `onLoad` message. Its `onDisable` override is empty, so there is likewise no plugin-authored disable message. The runtime completed the corresponding lifecycle phases without an exception; clean server termination and successful reload on the next boot are the observable evidence available for this plugin.

## Regression and reproducibility checks

- Build A: `clean test proofArtifacts` — PASS, 9/9 tests.
- Existing AtlasHybrid integrated proof — PASS; all lifecycle, command, event, scheduler, diagnostic, cancellation and shutdown markers appeared exactly once, with zero `ERROR`/`FATAL` records.
- Build B: `clean test proofArtifacts` — PASS, 9/9 tests.
- Runtime JAR A/B SHA-256: `C331435C8608B893E9420259BC73FC4CAD24C169CFAF5FBA55B67DC7DBDF1DFA` — byte-identical.
- Test plugin JAR A/B SHA-256: `E6AF4B180295B0938E854C475E662E1C41888ECA22A015ABDE2701307356F761` — byte-identical.
- Test mod JAR A/B SHA-256: `DFBBB6CF13D0E25F2117C7F7CA1394D31B277B7ED09018C1580FD6833EE33C69` — byte-identical after ForgeGradle reobfuscation.

## Known scope

WelcomeMessage has no commands and no optional integrations. This result covers its version 1.0 behavior: default configuration, enable lifecycle, join-event dispatch and delivery of the configured message. It does not claim compatibility with unrelated Bukkit APIs or other versions of the plugin.

## Phase 9.2 regression

After the AtlasHybrid Permission Core changed `CommandSender` and player
composition, the pinned external artifact was loaded again beside the internal
integration fixture. It was discovered, loaded and enabled once; the Forge
player join was posted once; the internal permission/player-identity proof also
passed; shutdown was clean; and the run contained no plugin `ERROR`/`FATAL` or
unsupported call attributed to WelcomeMessage. Status remains **FULL**.
