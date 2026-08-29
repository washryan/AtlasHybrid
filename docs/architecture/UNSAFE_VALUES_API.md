# UnsafeValues and data-version API

## Scope

AtlasHybrid exposes the narrow public `UnsafeValues` surface required to report
the Minecraft world data version. `Bukkit#getUnsafe()` delegates to
`Server#getUnsafe()`; the Forge server adapter owns one immutable
`AtlasUnsafeValues` instance, so both access paths return the same object.

The implementation has no CraftBukkit, NMS, reflection, Mixin or
instrumentation dependency. Unsupported exposed conversions fail with the
normal structured `NOT_IMPLEMENTED` compatibility diagnostic instead of a
fabricated `null`, `0`, or `AIR` result.

## Version semantics

The official Minecraft 1.19.2 server JAR contains a root `version.json` with:

| Field | Value | Meaning |
|---|---:|---|
| `id` / `name` | `1.19.2` | Minecraft release name |
| `world_version` | `3120` | DataFixer/world data version returned by `getDataVersion()` |
| `protocol_version` | `760` | Client/server network protocol; not returned here |
| `pack_version.resource` | `9` | Resource-pack format; not returned here |
| `pack_version.data` | `10` | Data-pack format; not returned here |

The Bukkit compatibility version remains
`1.19.2-R0.1-ATLASHYBRID`; it is also unrelated to the integer returned by
`UnsafeValues#getDataVersion()`.

`AtlasUnsafeValues.MINECRAFT_1_19_2_DATA_VERSION` records the versioned Mojang
metadata value `3120`. It is deterministic build/runtime metadata, not a clock,
protocol, pack, or AtlasHybrid version.

## Spigot 1.19.2 method audit

The target `UnsafeValues` source declares the following methods. Classification
describes what a semantically correct implementation needs, not what could be
stubbed sufficiently to pass linkage.

| Method | Classification | AtlasHybrid 0.1.0-alpha |
|---|---|---|
| `toLegacy(Material)` | `CRAFTBUKKIT_DEPENDENT` | Exposed; explicit `NOT_IMPLEMENTED` |
| `fromLegacy(Material)` | `CRAFTBUKKIT_DEPENDENT` | Exposed; explicit `NOT_IMPLEMENTED` |
| `fromLegacy(MaterialData)` | `CRAFTBUKKIT_DEPENDENT` | `DEFERRED`; `MaterialData` surface absent |
| `fromLegacy(MaterialData, boolean)` | `CRAFTBUKKIT_DEPENDENT` | `DEFERRED`; `MaterialData` surface absent |
| `fromLegacy(Material, byte)` returning `BlockData` | `NMS_DEPENDENT` | `DEFERRED`; block-data conversion absent |
| `getMaterial(String, int)` | `NMS_DEPENDENT` | Exposed; explicit `NOT_IMPLEMENTED` |
| `getDataVersion()` | `VERSION_METADATA` | Supported: `3120` |
| `modifyItemStack(ItemStack, String)` | `NMS_DEPENDENT` | `DEFERRED`; ItemStack/inventory is outside this phase |
| `checkSupported(PluginDescriptionFile)` | `CRAFTBUKKIT_DEPENDENT` | `DEFERRED`; CraftBukkit API-version policy |
| `processClass(PluginDescriptionFile, String, byte[])` | `CRAFTBUKKIT_DEPENDENT` | `DEFERRED`; CraftBukkit class rewriting/remapping |
| `loadAdvancement(NamespacedKey, String)` | `NMS_DEPENDENT` | `DEFERRED`; advancement manager/persistence absent |
| `removeAdvancement(NamespacedKey)` | `NMS_DEPENDENT` | `DEFERRED`; advancement manager/persistence absent |
| `getDefaultAttributeModifiers(Material, EquipmentSlot)` | `NMS_DEPENDENT` | `DEFERRED`; item attributes absent |
| `getCreativeCategory(Material)` | `NMS_DEPENDENT` | `DEFERRED`; item/creative registry semantics absent |

No audited method was classified `SAFE_GENERIC`: legacy conversion, material
data fixing, item mutation, advancements, attributes, creative categories and
class processing all require a larger version-specific subsystem. Only
`getDataVersion()` is safe `VERSION_METADATA` for this phase.

## LuckPerms/Adventure use

Bytecode inspection of the exact remapped
`adventure-platform-bukkit-4.21.1` library shipped with LuckPerms 5.5.81 found
one `UnsafeValues` call site. In `BukkitComponentSerializer.<clinit>`, Adventure
first establishes the post-1.13 branch through `Material.BLUE_ICE`. That branch
passes `Bukkit.getUnsafe().getDataVersion()` to
`JSONOptions.byDataVersion().at(int)`, then applies the resulting option state
to the Gson component serializer builder. The pre-1.13 branch instead supplies
zero and a legacy hover serializer.

The JAR contains no other `getUnsafe`, `getDataVersion`, or `UnsafeValues`
reference. Therefore the supported method is sufficient for this static
initializer, without implementing LuckPerms-specific behavior.
