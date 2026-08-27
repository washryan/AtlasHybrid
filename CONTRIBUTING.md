# Contributing to AtlasHybrid

AtlasHybrid values small, attributable, testable compatibility changes.

1. Read `docs/ARCHITECTURE_RESEARCH.md` and keep Forge as the official loader.
2. Do not paste or mechanically translate code from hybrid server projects.
3. Record every external source consulted and every dependency added.
4. Keep Minecraft/Forge symbols inside the versioned platform module.
5. Add a failing behavior test before expanding the Bukkit surface or adding a transformer.
6. Run `./gradlew clean test proofArtifacts` on JDK 17.
7. Do not include Mojang server binaries, generated/decompiled Minecraft sources, credentials, worlds, or logs.

Changes involving upstream code, Mixins, access transformers, NMS remapping, or
license ambiguity require an architecture and provenance update before merge.
