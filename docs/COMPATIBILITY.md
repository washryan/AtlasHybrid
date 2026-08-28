# Plugin compatibility

Compatibility is assigned from observed behavior on AtlasHybrid, not from a percentage estimate.

- **FULL:** every advertised behavior within the tested plugin scope worked.
- **PARTIAL:** the plugin loaded, but at least one tested advertised behavior did not work.
- **INCOMPATIBLE:** the plugin could not complete startup or its primary behavior could not run.
- **UNTESTED:** no controlled AtlasHybrid execution has been completed.

| Plugin | Version | Result | Validated behavior | Notes |
|---|---:|---|---|---|
| AtlasHybridTestPlugin | 0.1.0-alpha | FULL | Lifecycle, commands, player events, block-break cancellation, scheduler and unsupported-API diagnostic | Internal integration fixture |
| WelcomeMessage | 1.0 | FULL | Discovery, config extraction/read, enable, real `PlayerJoinEvent`, configured chat message, clean stop and restart | External plugin tested from pinned upstream source; not bundled |

See [the WelcomeMessage compatibility report](compatibility/WELCOME_MESSAGE.md) for scope, provenance and evidence.
