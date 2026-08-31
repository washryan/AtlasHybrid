# LuckPerms Forge permission bridge

## Decision and scope

AtlasHybrid integrates the official LuckPerms Forge 5.4 backend as an optional
external permission authority for online Bukkit `Player#hasPermission` calls.
The bridge lives in the Forge compatibility area and uses only the published
`net.luckperms.api` contract. It neither changes LuckPerms nor emulates
CraftBukkit.

This is intentionally a `PARTIAL / PASS` capability. Bukkit service discovery,
plugin identity/dependency satisfaction, Bukkit `PlayerAdapter`, Vault and
permission subscription parity are separate problems.

## Dependency and discovery

The Forge platform has `compileOnly net.luckperms:api:5.4`; the API is never
copied into the Atlas artifact. `ModList#isLoaded("luckperms")` gates creation
of the compatibility class. An Atlas-only production boot proves that the core
runtime has no hard LuckPerms classloading dependency.

Discovery states are `ABSENT`, `DISCOVERED`, `BOUND`, `UNBOUND` and `FAILED`.
An installed mod starts as discovered. At `ServerStartedEvent`, the bridge calls
the public `LuckPermsProvider#get()` and registers only after that API exists.
Normal absence is silent. Unexpected API/linkage failures warn once and retain
Atlas fallback behavior.

## Query contract

```text
Player#hasPermission(node)
  -> AtlasPermissible
  -> PermissionProviderRegistry (HIGHEST system provider)
  -> UserManager#getUser(UUID)
  -> ContextManager#getQueryOptions(User)
  -> CachedPermissionData#checkPermission(node)
```

The bridge supports Player subjects only. Console and other subjects abstain.
An unloaded User or unavailable live `QueryOptions` also abstains; permission
checks never load storage, block on a future, or fabricate context.

Tristate mapping is exact:

| LuckPerms result | Atlas provider result |
|---|---|
| `TRUE` | `Optional.of(true)` |
| `FALSE` | `Optional.of(false)` |
| `UNDEFINED` | `Optional.empty()` |

LuckPerms owns cached permission data. Atlas adds no second cache and requests
fresh contextual `QueryOptions` on every check. Cached-data reads are the same
synchronous, thread-safe public path used by LuckPerms platform adapters; no
storage or network I/O occurs in `hasPermission`.

## Precedence

The established Atlas order is retained:

1. explicit Bukkit permission attachments;
2. priority-ordered external providers, with LuckPerms at `HIGHEST`;
3. Atlas registered-permission/default behavior.

LuckPerms Bukkit normally imports Bukkit attachments and defaults into its own
calculation. The Forge backend cannot observe them. Evaluating attachments
first preserves Bukkit session grants/denials; evaluating Atlas defaults only
after LuckPerms abstains ensures a LuckPerms FALSE is never mistaken for
UNDEFINED.

## Ownership and lifecycle

`PermissionProviderRegistry` now supports an explicit system owner in addition
to its existing Plugin owner. This avoids inventing a fake Bukkit plugin.
Plugin registrations retain their enable-state and disable cleanup semantics.

The bridge checks the public provider identity at the end of server ticks. A
removed API unregisters immediately and changes to `UNBOUND`; a replacement is
rebound without retaining the old API. `ServerStoppingEvent` removes the system
provider before Bukkit plugin disable. The provider stores no Player or User,
only the current LuckPerms API instance, so logout needs no Atlas cleanup.

## Verification

Unit coverage includes TRUE, FALSE, abstain, unsupported subjects, missing User,
missing QueryOptions, changing context result/no Atlas cache, exception
isolation, system-provider priority, registration, unregistration and rebinding.

The real Forge 43.5.0 proof used unchanged LuckPerms Forge 5.4.46. An actual
protocol player was queried only through Bukkit `/atlaspermcheck`. It proved
UNDEFINED fallback, TRUE, authoritative FALSE, removal/fallback, and a live
`gamemode=survival` TRUE to `gamemode=creative` FALSE transition. Reconnect,
`lp reloadconfig`, provider removal, H2 close and process exit were clean.

The separate Atlas-only integration proof verifies that absence leaves all
existing behavior unchanged, including WelcomeMessage, WarpPlugin, login,
events, scheduler, block-break cancellation and shutdown.

## Explicit non-goals

- no Bukkit `ServicesManager` registration for `LuckPerms.class`;
- no fake `PluginManager#getPlugin("LuckPerms")` result;
- no satisfaction of `depend: [LuckPerms]`;
- no Vault bridge;
- no Bukkit `PlayerAdapter` claim;
- no private reflection, LuckPerms implementation API, bytecode patch, Mixin,
  CraftBukkit class or external JAR incorporation.
