# Configuration API subset

AtlasHybrid provides a deliberately bounded Bukkit configuration model for the
plugins validated by the project. Phase 9.5 expanded the previous flat YAML
support into nested, path-addressable sections without adding a general Java
object deserializer or a plugin-specific branch.

## Audited public surface

The contracts were compared with the pinned Bukkit/Spigot API for Minecraft
1.19.2 and with LuckPerms 5.5.81 source. The implemented hierarchy is:

```text
ConfigurationSection
  -> MemorySection
     -> MemoryConfiguration
        -> FileConfiguration
           -> YamlConfiguration
```

The supported section operations are `get`, `set`, `contains`, `isSet`, typed
string/boolean/int/double/list access, `getConfigurationSection`,
`createSection`, `getKeys` and `getValues`. Dot is the path separator, so
`data.address` resolves the `address` child of the `data` section.

LuckPerms' Bukkit adapter specifically uses `loadConfiguration(File)`, typed
getters with fallbacks, `isSet`, `getConfigurationSection`, `getKeys(false)` and
`getString`. The `Reader` overload also exists in the target API and is used by
Bukkit's generic `JavaPlugin` default-config path, so it is supported. The
audited LuckPerms path does not use `addDefault`, configuration options or
`copyDefaults`; those broader systems remain outside this subset.

## Loading, safety and errors

Files are decoded explicitly as UTF-8. Missing and empty files return an empty
configuration. Invalid input is logged at `SEVERE` and returns an empty
configuration, matching the target API's load-and-log behavior.

The parser accepts the scalar, nested mapping and scalar-list forms used by the
validated plugins. It rejects duplicate keys, invalid indentation, arbitrary
YAML tags, anchors, aliases, merge keys and unsupported flow collections. Input
is capped at 4 MiB, nesting at 64 levels and parsed nodes at 100,000. It never
reflectively constructs Java classes. The existing `org.bukkit.Location` map is
the only explicit serialization shape converted to an object.

This is not a claim of complete YAML 1.1/1.2 compatibility. Unsupported syntax
fails closed instead of being interpreted approximately.

## Persistence and validation

Saving recursively emits deterministic mappings, lists, primitive scalars and
locations. Comments and original textual formatting are not preserved; semantic
values are preserved across load, modify, save and reload.

Phase 9.5 validation:

- `59/59` automated tests pass, covering missing/empty files, UTF-8, scalar
  types, nested sections, lists, paths, nulls, invalid input, unsafe constructs,
  section mutation, roundtrip persistence and the existing location/config
  regression.
- The real LuckPerms `config.yml` (37 KB) parses to 123 accessible paths,
  including `storage-method`, `data.address` and pool settings.
- The Forge proof emits `YAML_LOAD_OK` exactly once while preserving all older
  lifecycle, command completion, event, scheduler, permission, teleport,
  cancellation, diagnostic and shutdown markers.
- WelcomeMessage and WarpPlugin remain `FULL`; WarpPlugin's YAML location/list
  persistence and restart behavior remain intact.
- Builds A and B produce byte-identical runtime, test-plugin and test-mod JARs.
