# Material and registry API foundation

AtlasHybrid exposes `org.bukkit.Material` as a real Java enum. This is required
for binary compatibility: compiled plugins access enum constants with JVM
`getstatic`, and replacing the enum with a registry-backed class would break
that bytecode contract.

## Vanilla 1.19.2 catalog

The versioned enum source contains 1,281 modern vanilla materials. It was
generated deterministically from the Mojang-mapped Minecraft 1.19.2 `Blocks`
and `Items` registry declarations bundled by ForgeGradle, not from Bukkit's
`Material` source:

- 933 block registry identifiers;
- 1,152 item registry identifiers;
- 1,281 identifiers in their union.

Each constant stores independent block and item membership. `isAir` is true
only for `AIR`, `CAVE_AIR` and `VOID_AIR`. The catalog order and flags have the
SHA-256 fingerprint
`8E0DE363C35314708DED5C8CB8FA3FBC11D377AC275C95BB0AE8BE6BB7EA7EB1`,
which is asserted by tests. The generated Java source is committed, so normal
builds do not depend on a local registry scan, absolute path, clock or network.

The implemented API is deliberately bounded to enum `name`, `valueOf` and
`values`, plus `getMaterial(String)`, `matchMaterial(String)`, `isBlock`,
`isItem`, `isAir` and `getKey`. Exact lookup is case-sensitive. Match lookup
accepts forms such as `STONE`, `stone` and `minecraft:stone`, following the
Bukkit 1.19 normalization contract.

## Namespaced keys

`NamespacedKey` validates lowercase namespace and key syntax, supports
Minecraft and plugin namespaces, parsing, value equality, stable hashing and
the canonical `namespace:key` string. `Material` implements `Keyed`; every
catalog entry returns a `minecraft` key matching its vanilla registry id.

## Block bridge and modded policy

`Block#getType()` now has the Bukkit binary signature returning `Material`.
The Forge adapter resolves the real block registry key and maps vanilla keys to
the enum. The integration proof places a real stone block and emits
`MATERIAL_API_OK` only when the resulting Bukkit type is `Material.STONE`.

Modded block and item identifiers are intentionally not synthesized into the
enum because Java enum constants are static. If a modded block has no vanilla
material, `Block#getType()` reports that absence explicitly instead of
misrepresenting it as `AIR`. A future AtlasHybrid registry bridge may expose
modded `NamespacedKey` values outside the Bukkit `Material` enum.

`ItemStack`, inventory APIs, the broad Bukkit `Registry` surface and
`UnsafeValues` are outside this phase. None was needed for the observed
Material lookup or real-block proof.
