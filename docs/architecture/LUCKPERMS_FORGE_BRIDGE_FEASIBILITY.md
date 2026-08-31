# LuckPerms Forge bridge feasibility

## Decision

The historical official LuckPerms Forge backend is a viable native permission
authority for AtlasHybrid's Minecraft `1.19.2` / Forge `43.5.0` target.
Feasibility is proven; the Atlas `PermissionProvider` bridge is intentionally
not implemented in Phase 9.24A.

The future direction is:

```text
LuckPerms Forge 5.4.46
  -> public LuckPerms API
  -> optional Atlas LuckPerms PermissionProvider
  -> AtlasPermissible
  -> Bukkit Player#hasPermission
```

This avoids every rejected Bukkit-backend dependency: no
`SimplePluginManager` private maps, no `CraftHumanEntity#perm`, no fake plugin,
no private reflection and no patched LuckPerms JAR.

## Native Forge architecture

The Forge backend registers listeners before normal enable. Its
`setupPlatformHooks()` and platform service-registration method are empty.
`ForgePermissionHandlerListener` participates in Forge's public
`PermissionGatherEvent`:

- it adds a `luckperms:permission_handler` factory;
- it selects that handler when Forge still uses the default handler;
- it contributes LuckPerms command permission nodes;
- the handler registers gathered node names in LuckPerms' registry;
- boolean queries use loaded User cached permission data plus contextual
  `QueryOptions` and preserve Forge's default resolver for `UNDEFINED`;
- string/integer nodes resolve through cached metadata.

This is a native platform implementation, not a compatibility injection.

## Contexts

`ForgePlayerCalculator` supplies the enabled public context keys:

| Context | Runtime source | Invalidation |
|---|---|---|
| `world` | `ServerLevelData#getLevelName`, with configured rewrites | player dimension change |
| `dimension-type` | namespaced dimension location; vanilla namespace shortened | player dimension change |
| `gamemode` | live `ServerPlayer` game type | Forge game-mode change |

The real proof observed `world=world`, `dimension-type=overworld` and
`gamemode=survival`. The dedicated backend reported no static contexts; player
identity is the User subject, not a context pair. The context manager also has
an integrated-server-owner query option for single-player hosts.

## Public query path

For an online Atlas Player, the safest future path is:

```text
Bukkit Player UUID
  -> LuckPerms#getUserManager().getUser(UUID)
  -> LuckPerms#getContextManager().getQueryOptions(User)
  -> User#getCachedData().getPermissionData(QueryOptions)
  -> checkPermission(node)
  -> TRUE / FALSE / UNDEFINED
```

`getQueryOptions(User)` is public and returns an `Optional` only while the
corresponding user is online. Atlas can alternatively unwrap its own native
`ServerPlayer` and use `getPlayerAdapter(ServerPlayer.class)`. Passing Bukkit
`Player.class` is invalid: the Forge adapter requires the exact
`net.minecraft.server.level.ServerPlayer` class.

Recommended mapping for the existing Atlas SPI remains:

| LuckPerms result | Atlas provider result |
|---|---|
| `TRUE` | allow |
| `FALSE` | deny |
| `UNDEFINED` | abstain, then Atlas fallback/default policy |

The semantic interaction between LuckPerms' Forge default resolver and Atlas'
Bukkit defaults must be decided and tested before implementation.

## Classloader boundary

Runtime proof established:

- public `net.luckperms.api.*`: Forge
  `TransformingClassLoader`, parent-visible to Atlas plugins;
- LuckPerms implementation: private LuckPerms `JarInJarClassLoader`;
- Bukkit probe: Atlas `AtlasPluginClassLoader`;
- `api instanceof LuckPerms`: true across the boundary.

An optional bridge must compile against the API only and must not shade it.
Using `LuckPermsProvider.get()` after Forge enable is viable. Direct imports of
`me.lucko.luckperms.*` implementation classes are rejected.

## Bukkit integration gaps

| Feature | Bukkit 5.5.81 backend | Forge 5.4.46 backend | Atlas requirement |
|---|---|---|---|
| Bootstrap | blocked by CraftBukkit injectors | complete | use Forge backend |
| Storage/users/groups | reached before Bukkit blocker | complete, H2 proven | consume public API |
| Commands | registered before Bukkit blocker | `/lp` and `/luckperms` proven | no duplicate Bukkit command required |
| Contexts | Bukkit Player calculator complete | native ServerPlayer calculator complete | reuse native QueryOptions |
| Permission query | Craft permissible injection | Forge `IPermissionHandler` | delegate AtlasPermissible through public API |
| Bukkit PlayerAdapter | available only if Bukkit backend enables | unavailable; exact type is ServerPlayer | use UUID/User or native unwrap |
| Bukkit ServicesManager | publication blocked | not registered | optional reviewed service bridge |
| Bukkit plugin identity | real Bukkit plugin | none | do not fake by default |
| `depend: [LuckPerms]` | satisfiable by plugin candidate | unsatisfied | future capability/virtual dependency policy |
| `getPlugin("LuckPerms")` | returns plugin | returns null | document; do not fake in this phase |
| Vault | Bukkit integration surface | absent | separate future work |
| subscriptions/defaults | CraftBukkit-specific injectors | Forge gathered nodes/default resolver | reconcile generically for FULL status |

## Service bridge feasibility

Registering the same `LuckPerms` API instance in AtlasHybrid's public
`ServicesManager` is technically possible because API class identity is shared.
The result is **CONDITIONAL**, not implemented:

- registration needs a legitimate Atlas plugin/service owner;
- it must occur only after `LuckPermsProvider` becomes available;
- it must unregister before or during LuckPerms/Forge shutdown;
- reload/restart must not retain the LuckPerms implementation classloader;
- service publication does not satisfy `depend: [LuckPerms]` or plugin identity.

## Artifact and licensing

The tested original `LuckPerms-Forge-5.4.46.jar` has SHA-256
`f8afe723c0a1dc0c0f45cf9d21d34a1730570ea179d26ba15a17945ba5f5508b`.
LuckPerms is MIT licensed. A separate adapter may compile against the public API
and be distributed without bundling LuckPerms. This phase neither copied source
nor incorporated the external JAR in Atlas outputs.

## Implementation gate

Phase 9.24A establishes feasibility only. A later phase should implement an
optional API-only bridge with player-only queries, service/provider lifecycle,
context correctness, persistence and restart tests. Bukkit hard dependency,
plugin identity and Vault must remain separate decisions.
