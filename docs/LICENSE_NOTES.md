# License notes

Status: **GPL-3.0-only ACCEPTED**. This document is not legal advice.

The AtlasHybrid project license is GPL-3.0-only. The implementation remains a
clean-room implementation of a narrow Bukkit-compatible API subset. No upstream
Bukkit implementation source is incorporated in the first alpha.

Publication guardrails:

- `THIRD_PARTY.md` must match the resolved build and packaged JAR contents;
- Forge LGPL notices and source offer/link obligations must be reviewed;
- the runtime artifact must not contain Minecraft server classes, MCP data, or
  generated/decompiled Mojang sources;
- Mojang's official mapping notice must be respected: the mappings are used for
  development through ForgeGradle and are not redistributed complete and unmodified;
- any future imported upstream file must record origin commit, original license,
  modifications, and required notices.

Source reuse from Magma, CraftBukkit, Spigot, Paper, Mohist, or Arclight remains
outside this alpha. Any future reuse requires file-level provenance, license
compatibility review, modification records, and all required notices.
