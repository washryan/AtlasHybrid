# Third-party components

This inventory covers the direct platform, build, and test components of the
initial proof. It must be updated before publishing an artifact with a new direct
dependency. Libraries supplied transitively by an external Forge installation
remain governed by that distribution and are not bundled by AtlasHybrid.

| Component | Version/scope | Use | License/status | Bundled by AtlasHybrid |
|---|---|---|---|---|
| Minecraft | 1.19.2 | Dedicated server runtime | Mojang EULA | No |
| Minecraft Forge | 43.5.0 | Official mod loader and server integration API | LGPL-2.1-only; retain upstream notices | No; resolved/installed separately |
| ForgeGradle | 5.1.x | Build-time mapping/reobfuscation | See upstream distribution | No runtime bundling |
| Mojang official mappings | Minecraft 1.19.2 development mappings | Compile-time symbol mapping through ForgeGradle | Microsoft mapping notice and Minecraft EULA; complete unmodified mappings may not be redistributed | No |
| Gradle | 7.6.4 wrapper | Build tool | Apache-2.0 | Wrapper bootstrap files only |
| JUnit Jupiter | 5.10.3 | Tests | EPL-2.0 | No runtime bundling |

Studied but not incorporated: Bukkit, CraftBukkit, Spigot, Paper, Arclight,
Mohist, and Magma. Exact source links and audit commits are in
`docs/ARCHITECTURE_RESEARCH.md`.
