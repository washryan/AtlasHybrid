# Plugin lifecycle event API

AtlasHybrid implements the Bukkit 1.19.2 `PluginEvent`, `PluginEnableEvent`,
and `PluginDisableEvent` contracts through the existing plugin-loader lifecycle
and event bus. No plugin-specific lifecycle path exists.

## Public contract

`PluginEvent` extends `ServerEvent`, stores the real `Plugin` reference supplied
to its public constructor, and exposes it through `getPlugin()`. Enable and
disable events each have constructor `(Plugin)`, an independent static
`HandlerList`, synchronous dispatch, and no cancellation contract.

The signatures and dispatch order were audited against the Spigot 1.19.2 API
sources. AtlasHybrid omits only nullability annotations, which are not part of
the JVM method descriptors.

## Enable transition

The AtlasHybrid sequence is:

```text
discovery and preparation
-> onLoad
-> ENABLING (isEnabled == true)
-> onEnable
-> ENABLED
-> PluginEnableEvent
```

Listeners registered by the plugin during `onEnable` are therefore active for
its own enable event. Plugins enabled earlier also observe later plugins. A
listener exception follows the normal event-bus policy: it is diagnosed as
`EXECUTION_FAILED`, later listeners still run, and the successfully enabled
plugin is not rolled back.

Repeated `enableAll` calls do not republish an event for an already enabled
plugin. If `onEnable` throws, AtlasHybrid performs its existing atomic rollback
and publishes neither enable nor disable lifecycle event. The best-effort
`onDisable` callback in that failure path is compensation for partial setup,
not a completed enabled-to-disabled transition.

This last rule is an intentional safety guarantee requested by the AtlasHybrid
lifecycle contract. The audited legacy Spigot 1.19.2 `JavaPluginLoader` catches
an `onEnable` failure and can still publish `PluginEnableEvent`; AtlasHybrid
does not reproduce that false-success behavior.

## Disable transition and cleanup

For each successfully enabled plugin, reverse load order is preserved:

```text
PluginDisableEvent (plugin still enabled; listeners still registered)
-> DISABLING / onDisable
-> DISABLED
-> cancel scheduled tasks
-> unregister commands
-> unregister listeners, attachments, providers and services
-> remove plugin from PluginManager
-> classloader close when PluginRuntime closes
```

This matches the relevant Bukkit ordering: observers and the plugin's own
listener may see the disable event before `onDisable` and before ownership
cleanup. Reverse ordering also means an earlier plugin can observe a later
plugin's disable; after a plugin is cleaned up, its listeners receive no later
events. Repeated shutdown paths do not duplicate disable events.

## Validation

Unit coverage uses two isolated plugin JARs. It verifies earlier-plugin and
self-observation, true enabled state during both event types, exact-once
delivery, a throwing observer without target rollback, reverse disable order,
cleanup, idempotent repeated lifecycle calls, and two independent restart
cycles. The failed-enable fixture verifies zero enable and zero disable events.

The Forge proof emits `PLUGIN_ENABLE_EVENT_OK` and
`PLUGIN_DISABLE_EVENT_OK` exactly once on the real Server thread while all
prior integration markers and external regressions remain intact.
