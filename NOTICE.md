# Thaumic Tinkerer for Thaumcraft 4 Reload: sources and licence

This repository is a port of **Thaumic Tinkerer** (Minecraft 1.7.10, an add-on for Thaumcraft 4) to Minecraft 1.21.1,
written against the public `thaumcraft.api` of Thaumcraft 4 Reload. It is a separate mod (`thaumictinkerer`) with its
own jar for each loader (Fabric and NeoForge); it is not part of the Thaumcraft jar.

## Licence

Thaumic Tinkerer was created by **Vazkii** (continuing his *Elemental Tinkerer*), later maintained by **pixlepix** and
**nekosune** and, for 1.7.10, by the **GTNewHorizons** contributors. It is distributed under the
**Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported** licence (`LICENSE` in the root of this repository, legal code
unchanged from the upstream repository). Every Java file ported from the original keeps its original header
("created by Vazkii ... Derivative Work on Thaumcraft 4. Thaumcraft 4 (c) Azanor 2012") and adds the line that it was
ported to 1.21.1, with a pointer to this file.

Which files are under which licence:

- **CC BY-NC-SA 3.0** (`LICENSE`): the add-on itself, that is `common/`, `compat/` (the optional EMI, JEI and Jade integrations), `fabric/`
  (Java sources, data, assets, language files, the `fabric.mod.json` files), the sources and resources of `neoforge/` (`neoforge/src/`: the Java
  sources, the mixin configs, the `neoforge.mods.toml` files and the generated GameTest structures and world preset), `lang-extra/`, `docs/`,
  `README.md`, `README.ru.md` and this `NOTICE.md`. It must not be sold or
  used commercially, and further adaptations must carry the same licence.
- **MIT** (`LICENSE-MIT`, the licence of Thaumcraft 4 Reload, from which these files come): the files that build and check the add-on and
  contain no Thaumic Tinkerer code: `settings.gradle`, `build.gradle`, `gradle.properties`, `fabric.gradle`, `gradle/multiplayer.gradle`, `neoforge/build.gradle`,
  `neoforge/gradle.properties`, `.gitignore`, `.gitattributes`, `.github/` (the CI workflow), `tools/` (with `tools/neoforge_gametests.py`, which
  generates the NeoForge GameTest wrappers from the Fabric ones and writes no Thaumic Tinkerer code of its own) and `scripts/porting/` (the Python tools that produced the resources and the parity
  oracle from the 1.7.10 source; they carry no Thaumic Tinkerer text). The scripts, the Gradle files and the workflow say so in an
  `SPDX-License-Identifier: MIT` line, the Python tools too (`scripts/porting/README.md` repeats it).
- **Apache-2.0** (Gradle's own licence, not relicensed): `gradlew`, `gradlew.bat` and `gradle/wrapper/` (the wrapper jar and its properties), copied
  unchanged from the Thaumcraft repository; `gradlew` and `gradlew.bat` carry the Apache-2.0 notice of Gradle.
- `LICENSE-MIT` is a byte copy of the licence file of the Thaumcraft 4 Reload repository, so its second line still names the original Thaumcraft 4.2.3.5
  (c) Azanor, as it does there. The MIT files listed above contain no Thaumcraft code; the line is inherited, it is not a statement about them.
- The add-on jars (Fabric and NeoForge) are separate downloads; the Thaumcraft jars contain none of them, and this repository contains no Thaumcraft 4 jar, no
  Thaumcraft assets and no Thaumcraft 4 source.
- Publishing the port does not establish rights in Thaumcraft 4 (Azanor), which Thaumic Tinkerer is a derivative work
  of, nor in Minecraft.
- **CC: Tweaked** (the optional computer integration, `compat/src/main/java/thaumic/tinkerer/compat/computer/cc` and the loader glue,
  `docs/deviations/ComputerCraft.md`): the add-on is compiled against the API artifacts of CC: Tweaked (`cc.tweaked:cc-tweaked-1.21.1-core-api`,
  `-fabric-api`, `-forge-api` from <https://maven.squiddev.cc>, the version is `cc_tweaked_version` in `gradle.properties`) and contains and
  redistributes no part of it; players install CC: Tweaked themselves, and the GameTest runs download the mod as a test dependency that goes into
  no artifact of the add-on. The API files carry their own licence in an SPDX line: the ComputerCraft Public License (`LicenseRef-CCPL`,
  "Copyright Daniel Ratcliffe, 2011-2022. This API may be redistributed unmodified and in full only.") or the Mozilla Public License 2.0
  (`MPL-2.0`, "The CC: Tweaked Developers"). CC: Tweaked itself is published under the ComputerCraft Public License 1.0 and the MPL 2.0
  (<https://github.com/cc-tweaked/CC-Tweaked>). The add-on's own code that uses the API stays under CC BY-NC-SA 3.0 like the rest of it.

## Pinned sources

- Specification of the behaviour: <https://github.com/GTNewHorizons/ThaumicTinkerer>, branch `master`, commit
  `28f4def938e80d7a0b6f433c2b84300dfb7c35ce` ("update+gradle+bs+deps+gitignore", Minecraft 1.7.10, Forge 10.13.4.1614).
- Textures and the language files come from the same commit. `scripts/porting/tt_assets.py` converts them
  deterministically (lower-case snake_case paths, `.lang` to JSON) and records the hash of every source file in
  `scripts/porting/tt_assets_map.json`.
- Read as a reference for the structure of a later port only, no code or assets taken:
  <https://github.com/Thaumic-Tinkerer/ThaumicTinkerer>, commit `a43bc8e226e1efab36dd2796abc4e8050e89992e` (that
  branch is Minecraft 1.12.2 for Thaumcraft 6, a different mod design).

## What changed

Every deviation from the 1.7.10 behaviour is listed in `docs/DEVIATIONS.md` with its reason (1.21.1 has no metadata,
no OreDictionary, no numeric ids, no `EnumHelper`; enchantments, dimensions and recipes are data; the OpenComputers
peripherals, NEI, Forge Multipart, EnderIO, Botania, Blood Magic, IC2 and Tinkers' Construct integrations depend on 1.7.10 mods
that do not exist on 1.21.1, and the ComputerCraft peripherals and turtle tool are ported to CC: Tweaked).

## History

The port was written inside the Thaumcraft 4 Reload repository, <https://github.com/acak1221/Thaumcraft-1.21.1>, in the directory
`addons/thaumic-tinkerer` of the branch `claude/hopeful-hamilton-v8m5pi`, up to commit `4315aedabdd5ec54bdffe1c33e6fcb35096a8b3d`; the
history of those files is there. Since then this repository is where the add-on is developed, and the Thaumcraft repository no longer
contains it. The extensions of `thaumcraft.api` that the port asked for (`docs/API_GAPS.md`, `docs/api-requests/`) stay in Thaumcraft.
