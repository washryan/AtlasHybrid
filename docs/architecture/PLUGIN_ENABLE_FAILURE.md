# Plugin enable failure

AtlasHybrid treats plugin enable as an atomic lifecycle transition:

`DISABLED -> ENABLING -> ENABLED`

If `onEnable` throws, the transition instead enters cleanup and returns the
plugin to `DISABLED`. It is never left half-enabled.

## Disable decision

The audited Bukkit/Spigot lifecycle marks a plugin enabled before invoking
`onEnable`; a thrown enable exception can consequently reach the normal disable
path later. AtlasHybrid calls `onDisable` immediately and best-effort after an
enable failure so a plugin can release resources it already created. During
that callback `isEnabled()` remains true, matching normal disable semantics.
Any disable failure is preserved as a suppressed exception on the original
enable failure and cannot prevent AtlasHybrid-owned rollback.

The compensating callback does not publish `PluginDisableEvent`, because the
plugin never completed enable. `PluginEnableEvent` is likewise never published
for the failed attempt. Successful lifecycle event ordering is documented in
[`PLUGIN_LIFECYCLE_EVENT_API.md`](PLUGIN_LIFECYCLE_EVENT_API.md).

## Atomic rollback

After a failed enable, AtlasHybrid removes plugin-owned:

- event listeners and explicit executors;
- scheduled tasks;
- services and permission providers;
- permission attachments;
- dynamically registered commands.

Files and configuration are not deleted. The failed plugin remains known to the
runtime but disabled. Its classloader stays open through callbacks, rollback and
diagnostics, and closes only when the enclosing plugin runtime lifecycle closes.

## Thread diagnostic

AtlasHybrid captures a live-thread baseline before enable. After rollback it
reports newly surviving threads when their context-classloader ancestry,
stack/class ownership, or normalized plugin-name identity safely associates
them with the plugin. This is diagnostic only: AtlasHybrid does not use
`Thread.stop`, interrupt unrelated threads or terminate the JVM.

External resources that a plugin does not register through an AtlasHybrid-owned
API cannot be rolled back generically. A future cleanup SPI may be justified by
concrete cross-plugin evidence; none is introduced in this phase.

## Validation

The failed-enable fixture registers a listener, task, service, provider,
attachment and command, starts a controlled thread, then throws. Tests verify
the disabled state, best-effort `onDisable`, complete Atlas resource rollback,
thread detection, clean retry, classloader-close ordering, and zero false
enable/disable events. The Forge proof emits `FAILED_ENABLE_ROLLBACK_OK`
exactly once.
