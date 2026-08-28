# Event API

AtlasHybrid implements the public Bukkit event-registration subset required by
plugins that use either annotated handlers or explicit `EventExecutor`
registration. The implementation is generic and has no plugin-specific branch.

## Public contract

`EventExecutor#execute(Listener, Event)` has the Bukkit signature and may throw
`EventException`. `PluginManager#registerEvent` supports both public overloads:
the five-argument form and the six-argument form with `ignoreCancelled`.
`registerEvents` remains the annotation-driven entry point.

Each `RegisteredListener` owns its listener, executor, priority, plugin and
cancelled-event policy. Dispatch order is deterministic:

`LOWEST -> LOW -> NORMAL -> HIGH -> HIGHEST -> MONITOR`

Registration order is preserved within each priority. `MONITOR` receives no
AtlasHybrid-specific behavior. When `ignoreCancelled` is true, an already
cancelled event is skipped; when false, it is delivered normally.

An executor is wrapped with an explicit event-class check. A mismatched event
produces `EventException` instead of an obscure cast failure. Executor failures
retain their cause and stack trace, are reported as `EXECUTION_FAILED` with
plugin, event and listener identity, and do not prevent later listeners from
running.

## Ownership and cleanup

`HandlerList` tracks all handler lists and supports unregistering by listener or
plugin. Plugin disable and failed-enable rollback unregister every listener
owned by that plugin. A later registration therefore starts cleanly and cannot
inherit dead or duplicate listeners from the previous lifecycle.

## Validation

Focused tests cover explicit and annotated registration, all priority slots,
stable ordering within a slot, both cancelled-event policies, wrong event type,
executor failure with continued dispatch, listener/plugin unregister, exact-once
delivery and clean re-registration. The Forge proof emits
`EVENT_EXECUTOR_OK` exactly once.
