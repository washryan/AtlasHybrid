# Plugin resource ownership after failed enable

## Problem

A plugin can create external resources during `onEnable` before later code
throws. AtlasHybrid can deterministically roll back resources registered in its
own managers, but it cannot safely close an arbitrary executor, socket, HTTP
client or library-global pool that the plugin never registered with AtlasHybrid.
Calling `onDisable` remains best effort; a plugin whose disable path assumes a
fully completed enable can throw before reaching its own cleanup.

AtlasHybrid therefore diagnoses surviving threads but does not stop, interrupt,
or reflect into them. The diagnostic heading is **Plugin Resource**, not
“resource leak,” unless ownership and lifecycle semantics are independently
established.

## Thread ownership confidence

The failed-enable monitor snapshots live threads immediately before each
plugin enable attempt and examines only new threads still alive after rollback.

| Confidence | Evidence |
|---|---|
| `HIGH` | Thread context classloader is exactly the plugin classloader |
| `MEDIUM` | Captured stack contains a class already loaded by the plugin classloader |
| `LOW` | Only the normalized thread name contains the plugin name |

The report records thread name, daemon flag, state, context-classloader type,
confidence/evidence and up to twelve captured stack frames. Name-only evidence
never authorizes action. A non-daemon survivor explains why a JVM may remain
alive after the Minecraft server thread exits; a daemon survivor does not by
itself keep the process alive.

## Cleanup boundary

Current rollback owns and cleans:

- AtlasHybrid scheduler tasks;
- commands;
- listeners and event executors;
- services and permission providers;
- permission attachments;
- plugin enabled state and best-effort `onDisable`.

It does not claim ownership of arbitrary plugin-created Java resources. There
is no `Thread.stop`, indiscriminate interrupt, `System.exit`, private reflection
or library-specific cleanup.

## Possible future ResourceRegistry/SPI

A future opt-in registry could allow a plugin or platform adapter to register
`AutoCloseable`, `Closeable` or `ExecutorService` resources with a plugin owner.
Rollback could then close them in reverse registration order and aggregate
failures, using the same ownership model as tasks and services.

Benefits include deterministic cleanup and testable ownership. Costs include
ordering rules, duplicate/shared-resource handling, timeout policy, exception
aggregation, async close semantics and the risk that adapters register objects
they do not exclusively own. It would not help an unmodified plugin that never
uses the SPI. For those reasons Phase 9.10 documents the option but does not
implement a registry merely for LuckPerms.

## LuckPerms observations

Raw boot #9 identified `OkHttp metadata.luckperms.net` and
`OkHttp metadata.luckperms.net Writer` after a failed enable. The old log did
not capture daemon flags, context classloaders or stacks, so those fields cannot
be assigned retrospectively. Artifact inspection establishes that the plugin
creates its own HTTP client and begins translation metadata refresh before
listener registration. Its normal disable path contains dispatcher shutdown
and connection-pool eviction, but a partial-state null dereference at
`extensionManager.close()` occurs first and prevents that cleanup.

Raw boot #10 used the structured monitor. Neither OkHttp thread was live at the
post-rollback snapshot, so no resource block or ownership-confidence claim was
emitted. Minecraft saved every dimension and Gradle exited normally. This
contrast is why the monitor reports observations rather than treating a thread
name or one prior run as proof of a permanent leak.
