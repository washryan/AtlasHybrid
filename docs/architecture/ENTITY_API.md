# Entity API foundation

Phase 9.16 introduces the first coherent Bukkit entity boundary. It is a
clean-room subset of the Spigot 1.19.2 public API, not a claim that the full
entity surface is implemented.

## LuckPerms linkage audit

Raw boot #15 failed while `PluginManager#registerEvents` reflected
`BukkitCommandExecutor`. The source-visible selector pipeline calls
`Server#selectEntities`, filters with `instanceof Player`, casts to `Player`,
and finally calls `Player#getUniqueId`. Its compiler-generated predicate method
has an `Entity` parameter, so reflection links `org.bukkit.entity.Entity` even
though AtlasHybrid does not expose `Server#selectEntities` and the selector
path cannot execute. `FoliaSchedulerAdapter` separately uses
`Entity#getScheduler`, but that Paper/Folia adapter is not selected on Forge.

## Public API audit

| Domain | Spigot 1.19.2 surface | Phase 9.16 classification |
|---|---|---|
| Identity | UUID, runtime entity ID, entity type | `getUniqueId` and `getEntityId` **SUPPORTED_ALREADY/REQUIRED_NOW**; `getType` **DEFERRED** |
| Location | location copy, supplied-location copy, rotation, teleport | `getLocation` and existing player teleport **SUPPORTED_ALREADY**; remaining overloads **DEFERRED** |
| World | current world and server | `getWorld` **REQUIRED_NOW**; inherited sender server access **SUPPORTED_ALREADY** |
| Lifecycle | remove, dead/valid, persistence | **DEFERRED**; generic non-player wrappers do not yet exist |
| Metadata | `Metadatable` | **ARCHITECTURAL** pending a metadata ownership/lifecycle design |
| Permissions | `CommandSender`/`Permissible` | **SUPPORTED_ALREADY** for Player through the permission core |
| Vehicle | vehicle membership/ejection | **DEFERRED** |
| Passengers | primary/modern passenger operations | **DEFERRED** |
| Persistent data | `PersistentDataHolder` | **ARCHITECTURAL** pending namespaced storage and persistence ownership |
| Sound | swim/splash sounds | **DEFERRED** |
| Fire | fire, visual-fire and freeze ticks | **DEFERRED** |
| Ticks | ticks lived and portal cooldown | **DEFERRED** |
| Custom name | `Nameable`, visibility and related flags | **DEFERRED** |

Velocity, bounds, pose, scoreboard tags, gravity, damage cause and nearby
entity queries are also deferred. None is required by the observed LuckPerms
linkage path.

## Hierarchy and adapters

The public chain is now `Entity -> LivingEntity -> HumanEntity -> Player`.
Spigot obtains part of this relationship through additional parent interfaces
such as `Damageable`; those broad behavior contracts are deliberately deferred,
while the entity ancestry itself is real and connected. Empty living/human
boundaries are not reflection-only stand-ins: `Player` actually inherits them
and the existing Forge player adapter implements the resulting contract.

There is no second player/entity wrapper. `PlayerSessionRegistry` remains the
owner of connecting and online adapters, preserves one adapter per UUID, and
cleans it on denial, quit, or server close. A generic all-entity registry is
deferred until a non-player API path requires one.

`getUniqueId()` uses the Minecraft UUID. `getEntityId()` uses
`net.minecraft.world.entity.Entity#getId()` and does not allocate a parallel
ID. `getLocation()` reads live x/y/z/yaw/pitch. `getWorld()` and the Location
reuse one cached `ForgeWorldAdapter` per live `ServerLevel`; the cache is
cleared on runtime close.

## EntityType policy

Spigot 1.19.2 defines `EntityType` as a large enum whose constants, names,
classes, legacy IDs and keys are observable binary API. A player-only or
`UNKNOWN`-based enum would be false compatibility, especially for modded
entities. Phase 9.16 therefore does not add `EntityType` or `Entity#getType`.
A future phase must define a complete vanilla mapping and an explicit modded
entity policy before exposing this method.

## Proof

Unit tests cover the public ancestry, stable UUID/entity ID/world/location,
same adapter lookup, duplicate prevention and cleanup. The dedicated-server
proof uses a connected runtime player and emits `ENTITY_API_OK` exactly once.
It also verifies that the entity ID is Minecraft's real ID and that the World
object used by Entity and Location is identical.

Phase 9.17 extends the connected `HumanEntity`/`Player` boundary with the real
game-mode getter. See [`GAMEMODE_API.md`](GAMEMODE_API.md) for its mapping and
thread-safety contract.
