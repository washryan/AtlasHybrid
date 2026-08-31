# LuckPerms real-world Bukkit plugin matrix

## Scope and method

Phase 9.25 tested five unchanged, open-source Bukkit artifacts against
AtlasHybrid `0.1.0-alpha`, Minecraft `1.19.2`, Forge `43.5.0`, Java 17 and the
official LuckPerms Forge `5.4.46` artifact. Each plugin received an isolated,
ignored production `forgeserver` profile. Source was audited before execution,
then the first real blocker was recorded without patching the plugin or adding
an Atlas API.

The external JARs, profiles, worlds, H2 databases and logs are not tracked.
Source clones and artifacts were used only as test inputs. Inspection confirmed
that none of the five plugin JARs packages `net/luckperms/api/**`.

## Artifact provenance

| Plugin | Version / source ref | License | Original artifact and SHA-256 | Test profile |
|---|---|---|---|---|
| [LPC](https://github.com/ThePM2/LPC) | `3.7.2`; source commit `f4dc8cf03f14f5ac4d5aa49d74f31175638f33c0` | Unlicense / public domain | [Modrinth artifact](https://cdn.modrinth.com/data/4o7Lp9aB/versions/aqfFPEOS/LPC-3.7.2.jar), `1ed82992c2d315392d4ff2a4eb963e056f262359d4f23655aa4bfa8222b2ec6f` | `lpc` |
| [ExtraContexts](https://github.com/LuckPerms/ExtraContexts) | `2.0-SNAPSHOT`; Jenkins build 21; source commit `d6eec99de4bf2b25333cbae6edd965961f52f67f` | MIT | [Official Jenkins artifact](https://ci.lucko.me/job/ExtraContexts/lastSuccessfulBuild/artifact/target/ExtraContexts.jar), `ce7e67bccfe8dc67005d3dd57958133b8abb1283c89875b9a8dc7e275b36fb8e` | `extracontexts` |
| [LuckPermsGUI](https://github.com/BGHDDevelopment/LuckPermsGUI) | `4.6`; tag/commit `9a7fbc312fd1437886d6a55be99ee95fa08138b6` | MIT | Original GitHub release JAR, `c7d3d888e7dd285a8c160b5d31a672c906b88e2c7b42638f5662c435970252a2` | `luckpermsgui` |
| [TAB](https://github.com/NEZNAMY/TAB) | `3.1.5`; tag commit `fd4704f75ecbdab81d6ba27525857619178afcd5` | Apache-2.0 | Original GitHub release JAR, `7420875f1afacc93eb3cff955b626cbb9cc68f8a1b96a6d08bfb5aa2263b4a72` | `tab` |
| [Vault](https://github.com/MilkBowl/Vault) | `1.7.3-b131`; tag commit `d456b051c366284d1394ccb2a24f8ddcd31eeb45` | LGPL-3.0 | Original GitHub release JAR, `a6b5ed97f43a5cf5bbaf00a7c8cd23c5afc9bd003f849875af8b36e6cf77d01d` | `vault` |

## Pre-boot source audit

| Plugin | Selection category | Dependency metadata | LuckPerms / permission path | Other likely surface |
|---|---|---|---|---|
| LPC | A, B, D | `depend: [LuckPerms]`; `softdepend: [PlaceholderAPI]` | Loads `LuckPerms.class` from Bukkit ServicesManager; uses `getPlayerAdapter(Player.class)` in debug/chat formatting; calls `Player#hasPermission` for color nodes | `AsyncPlayerChatEvent`, configuration, commands `/lpc reload|clear|debug` |
| ExtraContexts | A, B | `depend: [LuckPerms]`; soft WorldGuard and PlaceholderAPI | Loads the API from ServicesManager and registers/unregisters `ContextCalculator<Player>` instances with LuckPerms `ContextManager` | Configuration and `/extracontexts-reload` |
| LuckPermsGUI | A, B | `depend: [LuckPerms]` | Uses public `LuckPermsProvider`; no ServicesManager lookup and no fake plugin identity requirement | Configuration defaults, metrics, inventories and `/lpgui` |
| TAB | C, E | `softdepend` includes LuckPerms and Vault | Treats `isPluginEnabled("LuckPerms")` as feature detection, then reads the real plugin version before using public `LuckPermsProvider` APIs | Parses the CraftBukkit package version and uses version-specific NMS storage |
| Vault | E | no LuckPerms dependency; `load: startup` | Service broker candidate, not itself a direct LuckPerms API consumer | Configuration defaults, Bukkit service discovery, permission registry and scheduler |

No selected descriptor declares `loadbefore`. Classic dependency entries carry
names, not version constraints.

## Raw-boot results

| Plugin | Raw boot | First blocker | Category | Status | Generic fix feasible? | Notes |
|---|---|---|---|---|---|---|
| LPC 3.7.2 | Hard virtual dependency resolved and plugin class loaded | `NoClassDefFoundError: org/bukkit/event/player/AsyncPlayerChatEvent` while `registerEvents` inspected LPC's listener methods | `BUKKIT_API` | **BLOCKED** | Yes, if Atlas can implement the asynchronous chat event with correct thread/cancellation semantics | The 9.24D dependency and 9.24C service gates passed. Execution stopped before LPC reached its Bukkit `PlayerAdapter` and real chat/permission behavior. Clean stop closed LuckPerms/H2. |
| ExtraContexts 2.0-SNAPSHOT | Hard virtual dependency resolved; loaded and enabled; zero unsupported APIs | none in tested lifecycle | none | **PARTIAL** | Not needed for the proven scope | The `has-played-before` calculator registered, `/extracontexts-reload` registered it again after cleanup, and shutdown was clean. A real player-side calculation was not proven, so this is not marked FULL; its `ContextCalculator<Player>` versus Forge `ServerPlayer` subject boundary remains to be tested. |
| LuckPermsGUI 4.6 | Hard virtual dependency resolved and plugin class loaded | `NoSuchMethodError: YamlConfiguration#addDefault(String,Object)` in its metrics initialization | `BUKKIT_API` | **BLOCKED** | Yes; configuration defaults are generic Bukkit API | Failure occurred before inventory and LuckPermsGUI functions. Failed-enable rollback and clean shutdown passed. |
| TAB 3.1.5 | Plugin class loaded; LuckPerms remained an optional soft dependency | `ArrayIndexOutOfBoundsException` while parsing `Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3]`; NMS access follows this branch | `CRAFTBUKKIT_INTERNAL` / `NMS` | **INCOMPATIBLE** | No, not within current architecture | Its LuckPerms feature check also requires real Bukkit plugin identity (`isPluginEnabled`/`getPlugin`), but startup fails earlier on CraftBukkit/NMS assumptions. A suppressed SnakeYAML class gap appeared during rollback. Clean shutdown passed. |
| Vault 1.7.3-b131 | Plugin class loaded at startup | `NoSuchMethodError: FileConfiguration#addDefault(String,Object)` in `Vault#onEnable` | `BUKKIT_API` | **BLOCKED** | Yes; same generic defaults contract independently blocks LuckPermsGUI | Vault service/economy/permission integration was not reached, so Vault is not yet proven to be the dominant LuckPerms blocker. Clean shutdown passed. |

The raw profiles recorded `LUCKPERMS_FORGE_DISCOVERED`, permission-provider
binding, Bukkit service registration and virtual capability availability before
plugin processing. LPC, ExtraContexts and LuckPermsGUI additionally recorded
`VIRTUAL_DEPENDENCY_RESOLVED`, proving that unmodified external hard-dependency
metadata uses the generic Phase 9.24D contract.

## Functional coverage and limits

- **ServicesManager:** externally proven by LPC and ExtraContexts reaching code
  after their public service lookup. ExtraContexts used the obtained API to
  register a real context calculator.
- **Hard dependency:** externally proven for three original plugin artifacts.
- **Soft dependency:** TAB remained non-blocking, as required. Its optional
  LuckPerms feature did not activate because virtual capability is deliberately
  not fake Bukkit plugin identity.
- **Player permissions:** LPC contains real `Player#hasPermission` gates, but
  its chat event blocker occurs first. Existing Atlas integration proofs still
  prove the permission bridge itself; no Phase 9.25 external plugin reached a
  player action that could honestly extend that claim.
- **Bukkit PlayerAdapter:** LPC calls it, but raw execution did not reach that
  path. ExtraContexts exposes a related Bukkit-player context-calculator
  boundary. Neither is enough evidence to implement an adapter speculatively.
- **Commands/config/events:** ExtraContexts reload command and configuration
  worked. LPC event registration, LuckPermsGUI configuration and Vault
  configuration were independently classified as general Bukkit gaps rather
  than LuckPerms bridge failures. TAB commands never became available.
- **Context:** calculator registration and reload were proven, not calculation
  against a live Bukkit player. No FULL status is claimed.

## Decision gate

1. **Is fake LuckPerms plugin identity justified? No.** Only TAB among the five
   selected artifacts used plugin identity for LuckPerms feature detection, and
   TAB is already incompatible at an earlier CraftBukkit/NMS boundary. Hard
   dependencies and public service discovery work without a fake plugin.
2. **Is Bukkit `PlayerAdapter<Player>` common enough to implement now? No.** LPC
   uses it, and ExtraContexts raises an adjacent subject-type question, but the
   paths have not reached a functional raw proof. An adapter must not be invented
   before its correct context and lifecycle semantics are established.
3. **Is Vault the dominant blocker? No.** Vault itself stops at the same generic
   configuration-defaults API as LuckPermsGUI; no tested plugin reached a
   missing Atlas Vault provider.
4. **Are most plugins already functional with the three current bridges? Not
   yet.** The bridges themselves held: no raw failure was classified as
   `LUCKPERMS_BRIDGE` or `VIRTUAL_DEPENDENCY`. However, only ExtraContexts
   completed enable, and its live-player context calculation remains unproven.

## Recommended Phase 9.26

**Phase 9.26D — Generic Bukkit configuration defaults API.** Audit and implement
the public `Configuration` / `FileConfiguration` / `YamlConfiguration`
`addDefault` and default-copy semantics correctly, then re-run LuckPermsGUI and
Vault breadth-first. This is the only first blocker independently observed in
multiple candidates and does not require LuckPerms-specific behavior,
CraftBukkit internals, NMS, fake plugin identity or Vault implementation.

`AsyncPlayerChatEvent` should remain a later generic-event candidate because it
currently blocks only LPC and requires careful async and cancellation semantics.
TAB should not drive compatibility work under the current architectural rules.

## Phase validation

- `clean test proofArtifacts`: **142/142 PASS** in both clean builds;
- standard Forge integration: **PASS**, including WelcomeMessage, WarpPlugin,
  login lifecycle, commands, scheduler, real block-break cancellation and clean
  shutdown;
- LuckPerms production proof: public API identity, ServicesManager query,
  virtual hard dependency, `reloadconfig`, provider/service/capability cleanup
  and H2 shutdown **PASS**;
- all five external raw profiles reached a controlled stop; no Java server
  process or stale running profile remained;
- `git diff --check`: **PASS**;
- external LuckPerms/plugin JARs remain ignored and untracked.

Build A and Build B were byte-identical:

| Atlas artifact | Build A SHA-256 | Build B SHA-256 | Result |
|---|---|---|---|
| runtime | `d11f498cab4e744af0ab73f8f1c359dca566b1c0517a6e4c6766926a129a2a72` | `d11f498cab4e744af0ab73f8f1c359dca566b1c0517a6e4c6766926a129a2a72` | PASS |
| test plugin | `daeeb79d76746c921462b25c46dd79886804856f6f5d8e90aede293b310c2aba` | `daeeb79d76746c921462b25c46dd79886804856f6f5d8e90aede293b310c2aba` | PASS |
| test mod | `d59afdc839840299039459f6bbd207e827b3222841c10f4489ca204e6a320931` | `d59afdc839840299039459f6bbd207e827b3222841c10f4489ca204e6a320931` | PASS |
| dependent probe | `e5dd4fd91e0abf1aa776aadc8461df92eebee3dd23404a78f35637e27a08f24f` | `e5dd4fd91e0abf1aa776aadc8461df92eebee3dd23404a78f35637e27a08f24f` | PASS |

## Future Atlas Control automation

The isolated profile recipe is suitable for later automation: build a pinned
Atlas commit, download pinned/hash-verified LuckPerms Forge and plugin artifacts,
start a temporary Forge server, wait for bridge/capability markers, run a
plugin-specific command or protocol probe, stop cleanly, classify the first
blocker, and retain logs outside Git. External binaries should remain fetched
test inputs rather than repository or release contents.
