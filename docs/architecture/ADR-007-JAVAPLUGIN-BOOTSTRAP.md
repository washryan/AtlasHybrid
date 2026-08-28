# ADR-007: classloader-owned JavaPlugin bootstrap context

## Status

Accepted for AtlasHybrid `0.1.0-alpha` in Phase 9.3.

## Context

Bukkit initializes a `JavaPlugin` from its plugin classloader during the
superclass constructor. AtlasHybrid previously called `atlasInitialize` only
after the complete plugin constructor returned. Real plugins that read their
logger or server from a field initializer therefore failed even though metadata
and the server facade were already available.

## Decision

Each `AtlasPluginClassLoader` owns the immutable bootstrap context for one
plugin. The loader opens a thread-confined scope only while it initializes and
constructs the main class. `JavaPlugin` accepts context from that loader or one
of its descendant classloaders. The scope is always removed in `finally`, and
`atlasInitialize` verifies continuity rather than replacing the logger or
identity.

Constructor-safe methods are documented explicitly. Calls whose backing state
is intentionally created later fail with `PLUGIN_BOOTSTRAP_PHASE` and status
`AVAILABLE_LATER`, distinct from `NOT_IMPLEMENTED`.

## Alternatives

- A process-wide `ThreadLocal` was rejected because ownership would be implicit
  and nested classloader attribution would be weaker.
- A permanent classloader registry was rejected because cleanup and stale-loader
  retention would be harder to prove.
- Post-construction initialization was rejected because it conflicts with
  established `JavaPlugin` construction semantics.
- LuckPerms-specific construction handling was rejected as non-generic.

## Consequences

Logger identity and plugin metadata are stable across construction and normal
lifecycle. Sequential and parallel construction are isolated. Auxiliary
instances created during the owned scope share context but are not separately
registered or given lifecycle callbacks. No CraftBukkit, NMS, Mixin,
instrumentation or plugin-specific branch is introduced.
