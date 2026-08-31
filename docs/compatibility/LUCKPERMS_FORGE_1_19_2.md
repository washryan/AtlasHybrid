# LuckPerms Forge 1.19.2 feasibility proof

## Result

**PARTIAL / PASS — PLAYER PERMISSION QUERY BRIDGE.** The official, unchanged LuckPerms Forge
`5.4.46` artifact completes enable on Minecraft `1.19.2`, Forge `43.5.0` and
Java 17 beside AtlasHybrid `0.1.0-alpha`.

The supported scope is the Atlas `Player#hasPermission` query bridge described
below. LuckPerms Bukkit `5.5.81` remains **BLOCKED** at its CraftBukkit-specific
platform hooks. No LuckPerms artifact is tracked or bundled.

## Exact artifact

| Field | Evidence |
|---|---|
| Published release | `v5.4.46-forge`, “v5.4.46 (Forge 1.19.2)” |
| Source commit | `1790c0ad4744d31ea3e30eb87822b4f506de449b` |
| 1.19.2 update included | `6e0e0e8ae9af64e74a7747c60aaca26e7db655df` (`Forge 1.19.2 (#3477)`) |
| Artifact | `LuckPerms-Forge-5.4.46.jar`, 1,374,871 bytes |
| SHA-256 | `f8afe723c0a1dc0c0f45cf9d21d34a1730570ea179d26ba15a17945ba5f5508b` |
| Published target | Forge; Minecraft `1.19`, `1.19.1`, `1.19.2` |
| Forge metadata | `loaderVersion="[41,)"`; Forge `43.5.0` is inside the declared range |
| Original compile target | Minecraft `1.19.2`, Forge `43.0.3` in the historical source |
| License | MIT |

Sources: [official Modrinth release](https://modrinth.com/mod/luckperms/version/39WCFdto),
[original artifact](https://cdn.modrinth.com/data/Vebnzrzj/versions/39WCFdto/LuckPerms-Forge-5.4.46.jar),
[1.19.2 source commit](https://github.com/LuckPerms/LuckPerms/commit/6e0e0e8ae9af64e74a7747c60aaca26e7db655df),
[5.4.46 source commit](https://github.com/LuckPerms/LuckPerms/commit/1790c0ad4744d31ea3e30eb87822b4f506de449b),
and [MIT license](https://github.com/LuckPerms/LuckPerms/blob/1790c0ad4744d31ea3e30eb87822b4f506de449b/LICENSE.txt).

## Isolated execution

The ignored `run-luckperms-forge/` profile contained the official Forge
`43.5.0` server, the reproducible AtlasHybrid runtime JAR and the original
LuckPerms Forge JAR. `plugins/` was empty for the baseline raw boot; the
LuckPerms Bukkit JAR was absent.

An initial `forgeserveruserdev` attempt failed in
`ForgeCommandExecutor#onRegisterCommands` because the published mod calls the
SRG symbol `Commands.m_82127_`, while the userdev runtime presents development
names. This is a run-profile mapping mismatch, not a production Forge
incompatibility. The valid proof therefore used the official `forgeserver`
installation and reobfuscated AtlasHybrid JAR.

## Raw boot and checkpoints

The production raw boot recorded:

```text
LuckPerms v5.4.46
Running on Forge - Forge
Loading configuration...
Loading storage provider... [H2]
Loading internal permission managers...
Performing initial data load...
Successfully enabled.
Done (...)! For help, type "help"
Successfully initialized permission handler luckperms:permission_handler
```

`LUCKPERMS_FORGE_ENABLE_REACHED = YES`.

Both aliases were live. `lp` reported LuckPerms `5.4.46`; `luckperms info`
reported platform `Forge`, server `1.19.2-43.5.0`, H2 storage and no messaging
or extensions.

## Real user and permissions

A real Minecraft protocol 760 connection joined as `AtlasForgeProof` in the
isolated offline-mode proof profile. LuckPerms reported:

- UUID `7e2222b3-3e9e-3df1-bf14-db7a86f54f60` (offline UUID);
- state `Online` and name `atlasforgeproof`;
- parent group `default`;
- active contexts `dimension-type=overworld`, `gamemode=survival`,
  `world=world`.

Using the official `lp user ... permission` commands, `atlas.proof` produced:

```text
undefined -> set true -> true -> set false -> false -> unset -> undefined
```

The Forge `PermissionAPI` selected `luckperms:permission_handler`. Source audit
confirms that its boolean-node path resolves the loaded User, obtains cached
contextual `QueryOptions`, calls cached `checkPermission`, and falls back to the
Forge node's default resolver only for `UNDEFINED`. The arbitrary
`atlas.proof` command proof exercises LuckPerms' permission processor; no Atlas
provider or extra Forge permission node was introduced in this phase.

## API and classloader proof

An ignored read-only probe plugin called only the public
`LuckPermsProvider.get()` API. It did not register a service or permission
provider. The boot recorded:

```text
LP_FORGE_PUBLIC_API_AVAILABLE version=5.4.46
LP_API_CLASSLOADER=cpw.mods.modlauncher.TransformingClassLoader@...
LP_IMPL_CLASSLOADER=me.lucko.luckperms.common.loader.JarInJarClassLoader@...
PROBE_CLASSLOADER=dev.atlashybrid.loader.AtlasPluginClassLoader@...
LP_API_CLASS_IDENTITY=true
```

The public API is therefore parent-visible with one class identity across the
Forge mod and Atlas plugin boundary; implementation classes remain isolated in
LuckPerms' jar-in-jar loader. The public static provider is available after the
Forge backend enables.

`LuckPerms#getPlayerAdapter(ServerPlayer.class)` is the native adapter. It
rejects `org.bukkit.entity.Player.class`. A future Atlas bridge should prefer
the public UUID/User path, then obtain online contextual `QueryOptions` through
the public context manager (or the native `ServerPlayer` adapter held by the
Forge platform), and finally call cached permission data. It must not construct
contexts manually when the online native subject is available.

## Bukkit limitations

- Forge LuckPerms is a mod, not a Bukkit `Plugin`.
- A Bukkit `depend: [LuckPerms]` currently fails Atlas plugin dependency
  resolution because only Bukkit plugin candidates satisfy hard dependencies.
- `PluginManager#getPlugin("LuckPerms")` returns no plugin.
- Forge LuckPerms does not publish itself in the Bukkit `ServicesManager`.
- Registering the already parent-visible public API instance in that manager is
  technically possible, but is **CONDITIONAL** on a reviewed owner/lifecycle
  bridge and was not implemented.
- Vault integration is not supplied by the Forge backend and remains a separate
  problem.

## Shutdown

Each valid production stop logged LuckPerms shutdown, H2 close and `Goodbye!`,
then saved all dimensions. The Java process exited with code `0`; no residual
server or non-daemon LuckPerms thread retained the process.

## Status

The Forge backend feasibility gate and the Phase 9.24B Player permission-query
bridge pass. Compatibility is `PARTIAL` because Bukkit service/plugin identity,
dependency resolution and Vault remain outside this bridge.

## Atlas regression and reproducibility

`clean test proofArtifacts` passed `123/123` tests twice. Build A and Build B
were byte-identical:

| Artifact | Build A / Build B SHA-256 |
|---|---|
| AtlasHybrid runtime | `a2090f1cf4f68b3ea938e267372f1d7d1bbf435288b8e074ae5feebcb50f1959` |
| AtlasHybridTestPlugin | `31655955e38070bd9ba7b743df49f55b66044044fc0b103b27c7b59909feb459` |
| AtlasHybridTestMod | `d59afdc839840299039459f6bbd207e827b3222841c10f4489ca204e6a320931` |

The normal integration proof passed with WelcomeMessage and WarpPlugin loaded,
real block-break cancellation, permission/service checks, commands, events,
scheduler and clean shutdown. The only `ERROR` was the deliberate failed-enable
fixture and was followed by `FAILED_ENABLE_ROLLBACK_OK`. `git diff --check`
passed. No LuckPerms Bukkit JAR was active and the only tracked JAR remained the
Gradle wrapper.

## Phase 9.24B permission bridge

AtlasHybrid now contains an optional compatibility bridge compiled only against
the official `net.luckperms:api:5.4` artifact. The API is not bundled into the
Atlas runtime. `ModList` gates bridge construction, so Atlas boots unchanged
when the LuckPerms mod is absent.

At `ServerStartedEvent`, after LuckPerms Forge has published its public API, the
bridge moves through `DISCOVERED` to `BOUND` and registers one system-owned
provider at `HIGHEST`. It checks the public provider identity on server ticks so
an unavailable or replaced API is removed/rebound without retaining the old
instance. Shutdown unregisters the provider before Bukkit plugins are disabled.

The query path is public API only:

```text
Bukkit Player#hasPermission(node)
  -> AtlasPermissible
  -> PermissionProviderRegistry
  -> UserManager#getUser(player UUID)
  -> ContextManager#getQueryOptions(loaded User)
  -> User#getCachedData#getPermissionData(QueryOptions)
  -> checkPermission(node)
```

Only online, already-loaded Player subjects are supported. A missing User or
missing live `QueryOptions` abstains; the bridge never synchronously loads
storage and never fabricates context. Mapping is `TRUE -> true`, `FALSE ->
false`, and `UNDEFINED -> abstain`.

Precedence is intentional: explicit Atlas/Bukkit attachments are evaluated
first, LuckPerms TRUE/FALSE next, and Atlas registered permissions/defaults
last. The Forge backend cannot see Bukkit attachments, so attachment-first is
required to preserve their documented behavior. LuckPerms FALSE remains an
authoritative denial over the Atlas core/default fallback.

The production Forge 43.5.0 proof used the original artifact hash recorded
above and the Bukkit-facing `/atlaspermcheck` probe. It observed:

```text
UNDEFINED -> Atlas TRUE default
LuckPerms TRUE -> true
LuckPerms FALSE -> false (overrides Atlas TRUE default)
remove -> Atlas TRUE default
gamemode=survival -> true
gamemode=creative -> false
```

Repeated logout/relogin used the same UUID without Atlas retaining a Player or
User reference. `lp reloadconfig` kept the public API identity valid. Normal
stop logged provider removal, plugin disable, LuckPerms H2 close and process
exit with no remaining server process.

This status is deliberately scoped. It does not expose `LuckPerms.class` in the
Bukkit `ServicesManager`, create a Bukkit plugin identity, satisfy
`depend: [LuckPerms]`, supply a Bukkit `PlayerAdapter`, implement Vault, or
claim Bukkit permission registry/subscription parity.
