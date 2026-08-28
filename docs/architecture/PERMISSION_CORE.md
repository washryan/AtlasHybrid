# AtlasHybrid Permission Core

AtlasHybrid 0.1.0-alpha implements a clean-room Bukkit permission subset owned
by the runtime. It does not emulate CraftBukkit fields and contains no
LuckPerms-specific path.

## Components

- `PermissibleBase` implements the public permissible, attachment, default and
  effective-permission contracts without CraftBukkit private state.
- `AtlasPermissible` adds the Atlas provider pipeline to that public core.
- `AtlasPermissionRegistry` owns registered permissions, subscriptions and live
  subjects for one server lifecycle.
- `PermissionProviderRegistry` is the generic, priority-ordered external
  provider SPI exposed through `AtlasPermissions.providers()`.
- `AtlasServicesManager` implements the public Bukkit services registry.

Player and console adapters contain one `AtlasPermissible`; they do not inherit
or mutate Minecraft permission fields.

## Evaluation order

For `hasPermission(node)`, nodes are stripped and normalized with
`Locale.ROOT` to lower case. Empty nodes are rejected.

```text
explicit attachment (last attachment wins)
  -> first answering external provider, Highest to Lowest
  -> registered permission/default and explicit children
  -> Bukkit unknown-node default (OP)
```

An explicit attachment result, including `false`, wins over providers. A
provider returns `Optional.empty()` to abstain. A provider exception is logged
with its owning plugin and node, then evaluation continues with the next
provider and finally the core. No implicit `*` or `example.*` matching exists;
providers may define their own wildcard semantics.

`isPermissionSet` and `getEffectivePermissions` describe the enumerable Bukkit
core snapshot. A query-only external provider is not fabricated into that
snapshot.

## Defaults and children

`TRUE`, `FALSE`, `OP` and `NOT_OP` are resolved against current operator state.
Changing a mutable subject's op value recalculates its snapshot. Registered
children are expanded recursively with cycle protection. A false parent value
inverts its child values.

Unknown nodes use Bukkit's `Permission.DEFAULT_PERMISSION`, which is `OP`.

## Attachments

Attachments retain their plugin and permissible owners and an ordered node map.
Setting or unsetting a value recalculates the subject. Later attachments override
earlier attachments. Removal recalculates first and invokes the removal callback
at most once. Timed attachments use the existing main-thread scheduler.

Plugin disable removes every owned attachment, provider and service even if the
plugin's `onDisable` throws. Player disconnect closes the session permissible,
unsubscribes it and discards attachments; attachments are not persisted across
sessions.

## Player and console lifecycle

`ForgeServerAdapter` keeps one player adapter per UUID for the connected
session. Join, block and quit bridges reuse that adapter. Quit handlers run
before the adapter is closed. The dedicated console has one server-lifecycle
adapter, is op, and cannot be de-opped.

## Provider SPI

Registrations contain owner, provider, priority and deterministic registration
sequence. Priorities are `LOWEST`, `LOW`, `NORMAL`, `HIGH` and `HIGHEST`;
equal-priority providers are queried in registration order. Disabled owners are
not queried. Unregister by provider and unregister-all by owner are supported.

The SPI accepts only an immutable `PermissionSubject` snapshot and normalized
node. It exposes no Minecraft, Forge, CraftBukkit or plugin-specific type.

## Services manager

The services registry supports registration, highest-priority load and
registration lookup, service/plugin enumeration, targeted unregister,
provider-wide unregister and plugin-wide cleanup. Service priorities follow the
public Bukkit order from `Lowest` through `Highest`; equal priority preserves
registration order. Returned collections are immutable snapshots.

Service publication and permission decisions remain separate paths.

## Thread model

Permission and service queries read immutable, volatile snapshots. Mutations are
serialized per registry or permissible and publish a complete replacement
snapshot. Plugin callbacks, including attachment removal callbacks and provider
queries, execute without a registry-wide lock. The provider registry catches
failures at the provider boundary so one provider cannot destabilize the server.

## Current compatibility boundary

The generic core is implemented and independently tested. This does not make the
unmodified LuckPerms Bukkit platform compatible: it still assumes Bukkit loader
construction behavior and later CraftBukkit permissible injection. LuckPerms
therefore remains `BLOCKED` pending a separately reviewed generic adapter/loader
phase.

## Phase 9.2 validation

- Java 17 / Gradle 7.6.4 unit suite: **38/38 PASS**.
- Automated Forge integration proof: **PASS**, including permission command,
  attachment true/false, provider, console, service, stable player identity and
  lifecycle cleanup to zero providers/services.
- Normal proof log: zero `ERROR`/`FATAL`, zero ANSI escape bytes, lifecycle and
  proof markers exactly once, clean shutdown.
- WelcomeMessage 1.0 regression: **FULL preserved**.
- WarpPlugin 1.0 commands, aliases, teleport, persistence and restart regression:
  **FULL preserved**.
- Reproducibility: two clean builds produced identical SHA-256 values.

| Artifact | Build A / Build B SHA-256 |
|---|---|
| `atlashybrid-1.19.2-0.1.0-alpha.jar` | `7cd7adf1a2499f20b76a825a9dd011b4e3c6141742130f85de6a4bc0c7ae3956` |
| `AtlasHybridTestPlugin-0.1.0-alpha.jar` | `7ffb64136176228a80b167c4c64970b0558b567012f8e94b37de090b493ad267` |
| `atlashybrid-test-mod-1.19.2-0.1.0-alpha.jar` | `847e7ff0a67846173d6e4fd7b098e384aaf51c4d289955b37696177f1ee598be` |
