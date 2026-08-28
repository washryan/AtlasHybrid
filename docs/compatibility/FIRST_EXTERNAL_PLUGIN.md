# First external plugin selection

## Selected plugin

- **Name:** WelcomeMessage
- **Repository:** https://github.com/Ninjananas/WelcomeMessage
- **Version:** 1.0
- **Pinned commit:** `998befeca67ee9f533a1cd1ce58368d0a379ebd3`
- **License:** MIT, copyright Ninjananas (2021)
- **Declared API target:** Bukkit/Spigot `api-version: 1.18`
- **Conceptual runtime target:** compatible with the stable Bukkit APIs used on Minecraft 1.19.x
- **Size:** one Java class, one listener, no commands
- **Dependencies:** none
- **Lifecycle:** `onEnable`, `onDisable`
- **Listener:** `PlayerJoinEvent`
- **Behavior:** copies the bundled default config and sends its configured `message` to the joining player

The repository is public, contains a clear MIT license, and has an official `v1.0` release. The test pins the source commit rather than trusting a third-party mirror. The plugin source is not modified, copied into AtlasHybrid, or bundled for redistribution.

## Rejected candidates

- `conclube/DeluxeAsyncJoinLeaveMessage`: broader async/message integration surface.
- `4ndyZ/JoinMessagePlus`: broader feature surface than needed for the first target.
- `micfun123/Welcomer_plugin`: targets Spigot 1.21 and uses inventory/material APIs outside the current subset.
- `minecraftindia/server-greeting`: also uses `PlayerDeathEvent`, mutable join/death messages, `ChatColor`, and player-history APIs.

## Static Bukkit API surface

Classification reflects AtlasHybrid at commit `3dc49b99505237ecd17b7d87ad54740598fd4578`, before the first external boot.

| Symbol used by WelcomeMessage | Purpose | AtlasHybrid status |
|---|---|---|
| `JavaPlugin` | Plugin base class | SUPPORTED |
| `JavaPlugin#onEnable()` | Enable lifecycle | SUPPORTED |
| `JavaPlugin#onDisable()` | Disable lifecycle | SUPPORTED |
| `JavaPlugin#saveDefaultConfig()` | Copy bundled `config.yml` when absent | SUPPORTED |
| `JavaPlugin#getServer()` | Obtain server facade | SUPPORTED |
| `JavaPlugin#getConfig()` | Read plugin configuration | SUPPORTED |
| `Server#getPluginManager()` | Obtain plugin manager | SUPPORTED |
| `PluginManager#registerEvents(Listener, Plugin)` | Register listener | SUPPORTED |
| `Listener` | Listener marker | SUPPORTED |
| `EventHandler` | Event handler annotation | SUPPORTED |
| `PlayerJoinEvent` | Join callback | SUPPORTED |
| `PlayerJoinEvent#getPlayer()` | Obtain joining player | SUPPORTED |
| `FileConfiguration#getString(String)` | Read `message` | SUPPORTED |
| `Player#sendMessage(String)` | Send the configured message to the real player | SUPPORTED |

No `UNKNOWN` or `NOT_IMPLEMENTED` symbol was found by the static audit. Runtime verification is still required because binary linkage, metadata parsing, classloading, config extraction, event dispatch, and message delivery cannot be proven statically.

## Provenance and artifact policy

The compatibility artifact was built from the pinned original source without source changes, using JDK 17 and the official Spigot API `1.19.2-R0.1-20221207.161214-43` compile classpath.

- **Artifact:** `WelcomeMessage-1.0.jar`
- **SHA-256:** `F66F0332BCDAB792083AAF094DE0C30A025EE843FF828D629CCD7FD864D3D7D1`

It is an external test dependency only: **plugin tested externally; not bundled**. The JAR remains under ignored local state in `run-compat/` and must not be committed or redistributed with AtlasHybrid.

The completed runtime evidence and final result are recorded in [WELCOME_MESSAGE.md](WELCOME_MESSAGE.md).
