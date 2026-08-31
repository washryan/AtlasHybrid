# LuckPerms manual playtest

This guide records the AtlasHybrid `0.1.0-alpha` **playable development
milestone**. It is a local development checkpoint, not an official release.
LuckPerms Forge `5.4.46` integration remains **PARTIAL / PASS**.

Validated scope includes Bukkit `Player#hasPermission` TRUE/FALSE/UNDEFINED
handling, live contexts, login/logout, public LuckPerms API access through
ServicesManager, virtual `depend: [LuckPerms]`, `reloadconfig`, groups,
permission persistence, web-editor session generation and clean shutdown. It
does not provide Bukkit LuckPerms plugin identity,
`PluginManager#getPlugin("LuckPerms")`, Bukkit `PlayerAdapter<Player>`, Vault,
TAB's CraftBukkit/NMS requirements or the missing Bukkit APIs recorded in the
compatibility matrix.

## Server profile

The persistent local profile is `run-playtest/`. It is intentionally ignored by
Git and is separate from all automated integration profiles.

| Component | Version / state |
|---|---|
| Minecraft | 1.19.2 |
| Forge | 43.5.0 |
| Java | 17 (`C:\Program Files\Java\jdk-17`) |
| AtlasHybrid | 0.1.0-alpha |
| LuckPerms Forge | 5.4.46 |
| LuckPerms JAR SHA-256 | `f8afe723c0a1dc0c0f45cf9d21d34a1730570ea179d26ba15a17945ba5f5508b` |
| World | persistent `run-playtest/world/` |
| Storage | persistent LuckPerms H2 under `run-playtest/config/luckperms/` |
| Gameplay | survival, normal difficulty |
| Authentication | `online-mode=false`, matching the existing local test environment |

The profile has an empty `plugins/` directory. No compatibility probes or
external Bukkit plugins run during normal gameplay.

## Start and connect

1. Double-click `run-playtest/start-playtest.bat`.
2. Wait for `Done (...)! For help, type "help"`.
3. Confirm these Atlas markers appear once:
   - `LUCKPERMS_FORGE_DISCOVERED`;
   - `LUCKPERMS_PERMISSION_PROVIDER_BOUND`;
   - `LUCKPERMS_BUKKIT_SERVICE_REGISTERED`;
   - `LUCKPERMS_VIRTUAL_DEPENDENCY_AVAILABLE`.
4. Start a clean Forge 1.19.2 client and connect to `localhost:25565`.

AtlasHybrid and LuckPerms are server-side for this profile; do not install their
JARs in the client. Because the profile is in offline mode, keep it local or on
a trusted LAN. Do not expose port 25565 publicly in this configuration.

## LuckPerms command baseline

Commands below use the syntax verified on the running Forge 5.4.46 backend. In
the server console, omit the leading `/`; in-game, include it.

First inspect the server and your loaded user:

```text
/lp
/lp info
/lp user <player> info
```

New players do not initially have LuckPerms administration commands. Grant
yourself access from the server console if you want to run them in-game:

```text
lp user <player> permission set luckperms.* true
```

Exercise the exact permission lifecycle:

```text
/lp user <player> permission set atlas.test true
/lp user <player> permission check atlas.test
/lp user <player> permission set atlas.test false
/lp user <player> permission check atlas.test
/lp user <player> permission unset atlas.test
/lp user <player> permission check atlas.test
```

Expected `permission check` results are `true`, `false`, then `undefined`.
Atlas maps TRUE to allow, FALSE to deny and UNDEFINED to its normal attachment
and default fallback.

## Groups

The persistent playtest database already contains an `admin` group with
`atlas.admin=true`. Add your actual player after the first login:

```text
/lp group admin info
/lp group admin permission check atlas.admin
/lp user <player> parent add admin
/lp user <player> info
```

For a clean re-creation or another group, the verified syntax is:

```text
/lp creategroup <group>
/lp group <group> permission set <node> true
/lp user <player> parent add <group>
/lp user <player> parent remove <group>
```

## Context playtest

Use the user info command after each transition and inspect the active contexts:

```text
/lp user <player> info
```

Play through:

- Overworld to Nether and back (`world` and `dimension-type`);
- survival to creative and back (`gamemode`);
- logout, reconnect and full server restart.

Example context-specific nodes supported by the verified command parser:

```text
/lp user <player> permission set atlas.test true world=world
/lp user <player> permission set atlas.test false gamemode=creative
```

Remove the same contextual node using the identical context suffix:

```text
/lp user <player> permission unset atlas.test world=world
/lp user <player> permission unset atlas.test gamemode=creative
```

## Official web editor

LuckPerms Forge 5.4.46 supports the official editor:

```text
/lp editor
```

The production playtest generated a session successfully and the official page
loaded its two groups and one proof user. To complete a manual edit:

1. Open the URL printed by the command.
2. Select your user or group.
3. Add or change `atlas.test`.
4. Press **Save**.
5. Copy the generated `/lp applyedits <code>` command.
6. Run that command in-game or in the server console.
7. Verify with `/lp user <player> permission check atlas.test`.

Saving in the browser generates an apply command; the server data changes only
after `applyedits` runs. This is the LuckPerms web editor, not LuckPermsGUI.

## Atlas `Player#hasPermission` observation

The normal playtest profile deliberately contains no test plugin, so it does
not add a probe command or gameplay listeners. The existing production proof
already verifies Bukkit `Player#hasPermission("atlas.test")` for TRUE, FALSE,
UNDEFINED fallback and contexts.

During this manual session, use permission-gated behavior from any future
explicitly approved plugin and compare it with LuckPerms `permission check`.
Do not install the internal Atlas test plugin for ordinary long-running play;
it intentionally exercises events, diagnostics and block cancellation.

## Long-run checklist

- join, leave and rejoin several times;
- travel between dimensions;
- change gamemode and return to survival;
- set, negate and unset direct permissions;
- add/remove a group parent;
- run `lp reloadconfig`;
- allow normal autosaves and optionally run `save-all flush` before a planned
  stop;
- review `run-playtest/logs/latest.log` for unexpected `ERROR` or `FATAL` lines.

## Clean shutdown

Always type this in the server console:

```text
stop
```

Expected shutdown evidence:

- virtual dependency becomes unavailable;
- LuckPerms Bukkit service and Atlas permission provider are removed;
- Atlas reports zero remaining providers/services;
- LuckPerms closes H2 and logs `Goodbye!`;
- all world dimensions save and Java exits.

Do not close the console window while the world or H2 database is saving.

## Deferred compatibility work

This playtest does not implement or work around:

- `YamlConfiguration` / `FileConfiguration#addDefault`;
- `AsyncPlayerChatEvent`;
- Vault;
- TAB's CraftBukkit/NMS assumptions;
- EssentialsX or LuckPermsGUI.

Tentative future milestones are: (1) LuckPerms playtest, (2) EssentialsX, (3)
generic APIs demonstrated necessary by EssentialsX, (4) Vault, (5) TAB, then
other plugins. This order is guidance only and should change if real playtest
evidence identifies a more important problem.

Sources for command semantics: [LuckPerms command usage](https://luckperms.net/wiki/Command-Usage),
[usage guide](https://luckperms.net/wiki/Usage), and
[official web editor guide](https://luckperms.net/wiki/Web-Editor).
