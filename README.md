<div align="center">

# Thaumic Tinkerer for Thaumcraft 4 Reload

**The Thaumcraft 4 add-on by Vazkii, pixlepix and nekosune, carried from Minecraft 1.7.10 to 1.21.1 — on Fabric and NeoForge.**

[![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=flat-square)](#installation)
[![Fabric](https://img.shields.io/badge/Fabric-supported-DBD0B4?style=flat-square)](#installation)
[![NeoForge](https://img.shields.io/badge/NeoForge-supported-D7742F?style=flat-square)](#installation)
[![Requires Thaumcraft 4 Reload](https://img.shields.io/badge/requires-Thaumcraft%204%20Reload-8A4BD6?style=flat-square)](https://github.com/acak1221/Thaumcraft-1.21.1)
[![Status: alpha](https://img.shields.io/badge/status-alpha-8A4BD6?style=flat-square)](#status)

[Releases](https://github.com/acak1221/Thaumic-Tinkerer-1.21.1/releases) · [Thaumcraft 4 Reload](https://github.com/acak1221/Thaumcraft-1.21.1) · [Русская версия](README.ru.md)

</div>

Thaumic Tinkerer adds late-game devices, wand foci, enchantments and the KAMI tier to Thaumcraft 4. This repository is its port
to Minecraft 1.21.1.

> [!IMPORTANT]
> This is an **add-on**, not a standalone mod. It needs **[Thaumcraft 4 Reload](https://github.com/acak1221/Thaumcraft-1.21.1)**,
> the port of Azanor's Thaumcraft 4.2.3.5 to Minecraft 1.21.1, and does nothing without it. Its research appears in its own tab
> of the Thaumonomicon and builds on Thaumcraft's research.

## What is in it

The content is the 1.7.10 mod's: 74 research entries and 127 recipes, compared with the original source field by field
([docs/PARITY_REPORT.md](docs/PARITY_REPORT.md)).

| Group | Content |
|---|---|
| **Devices** | Osmotic Enchanter (you choose the enchantments, a wand pays the vis), Transvector Interface and Transvector Dislocator, Dynamism Tablet, Thaumic Restorer, Essentia Funnel, Kinetic and Corporeal Attractors, Remote Placer, Aspectalyzer, Tablet of Necromancy, Ethereal Platform, Levitational Locomotive, Golem Connector |
| **Wand foci** | Distortion, Dislocation, Ender Rift, Uprising, Mending, Telekinesis, Efreet's Flame, Celestial Recall, Shadowbeam, Experience Drain |
| **Enchantments** | Fourteen, given only by the Osmotic Enchanter: Ascent Boost, Slow Fall, Pounce, Shockwave, Flaming Touch, Disintegrate, Shatter, Tunnel, Quick Draw, Vampirism, Valiance, Focused Strike, Dispersed Strike, Final Strike |
| **Items** | Spellbinding Cloth, Helmet of Revealing, Cursed Spirit's Blade, Black Hole Ring, Feline Amulet, Talisman of Remedium, Tome of Knowledge Sharing, Hyperenergetic Nitor, Gaseous Illuminae and Tenebrae, Infused Seeds and Infused Farmland, the six imbued fires |
| **KAMI** | Ichor, Ichorcloth and Ichorium; the Ichorcloth armour and its four awakened pieces; Ichorium tools and their awakened forms; Bottomless Pouch, Ichorium Cap and the Ichor Woven Silverwood Wand Core; Celestial Gateway, Worldshaper's Looking Glass, Protoclay, the Bedrock Portal and its dimension. A config option turns the tier off |
| **Building blocks** | Smokey Quartz and its blocks, slabs and stairs of the Thaumcraft paving stones |

Optional integrations, used only when the other mod is installed: recipe pages for **EMI** and **JEI**, block tooltips for **Jade**,
and energy through the Transvector Interface (NeoForge's own energy capability; on Fabric through **Team Reborn Energy**).

Not ported: the ComputerCraft and OpenComputers peripherals and IC2 EU. Every difference from the 1.7.10 mod is listed in
[docs/DEVIATIONS.md](docs/DEVIATIONS.md).

## Installation

1. Install **Thaumcraft 4 Reload** for your loader from its [releases](https://github.com/acak1221/Thaumcraft-1.21.1/releases)
   and what it needs (its [README](https://github.com/acak1221/Thaumcraft-1.21.1#installation) says what).
2. Download the Thaumic Tinkerer jar for the same loader from the [releases](https://github.com/acak1221/Thaumic-Tinkerer-1.21.1/releases)
   of this repository and put it into `mods/` next to Thaumcraft.

| | Fabric | NeoForge |
|---|---|---|
| Add-on jar | `ThaumicTinkerer-fabric-<version>.jar` | `ThaumicTinkerer-neoforge-<version>.jar` |
| Minecraft | 1.21.1 | 1.21.1 |
| Thaumcraft 4 Reload | the Fabric jar | the NeoForge jar |
| Also needed | Fabric API, Architectury API 13.0.11+ | NeoForge 21.1.259+, Architectury API 13.0.11+ |

**Which Thaumcraft version.** The add-on uses only the public `thaumcraft.api`, and needs the extensions that Thaumcraft 4 Reload
has had since 2026-10-31. Each release names the Thaumcraft build it was tested with in its `BUILD_INFO.json`: 0.1.0-alpha.7 was built
and tested against Thaumcraft 4 Reload 0.1.0-alpha.11. It also starts with 0.1.0-alpha.24; that combination has not been through the
test suites.

Use a new world or a backup: both mods are alpha versions.

## Status

**Alpha** (0.1.0-alpha.7). All eight feature groups are ported for both loaders from one shared code base.

- The release build of 0.1.0-alpha.7 ran 429 JUnit tests, 362 GameTests on a Fabric server and 371 on a NeoForge server, without a failure.
- The client was looked at in a development client on both loaders, and multiplayer on a dedicated server with real clients
  ([docs/CLIENT_VERIFICATION.md](docs/CLIENT_VERIFICATION.md), [docs/MULTIPLAYER_VERIFICATION.md](docs/MULTIPLAYER_VERIFICATION.md)).
  The release workflow itself does not repeat those two checks.
- Not seen: the mod on a real GPU with sound, the Kami wings and ghost blocks, other mods than EMI, JEI and Jade.

What exactly was run and seen for each part is in [docs/STATUS.md](docs/STATUS.md).

**Known issue.** With Thaumcraft 4 Reload up to 0.1.0-alpha.24, recipes of this add-on that take Obsidian Tiles (the Osmotic Enchanter
among them) do not accept crafted tiles, so the infusion never starts. The cause was in Thaumcraft's `ItemApi` and is fixed there in commit
[`299a9c0`](https://github.com/acak1221/Thaumcraft-1.21.1/commit/299a9c01); it needs the first Thaumcraft 4 Reload release after 0.1.0-alpha.24.

## For developers

- [docs/BUILDING.md](docs/BUILDING.md): what you need, every build and test command, what a good run looks like, the layout of the repository and how releases are made.
- [docs/PORTING_RULES.md](docs/PORTING_RULES.md): the rules every file follows (naming, data components, licence headers, API-only imports).
- [docs/DEVIATIONS.md](docs/DEVIATIONS.md): every difference from the 1.7.10 mod, with the per-group documents in `docs/deviations/`.
- [docs/API_GAPS.md](docs/API_GAPS.md) and `docs/api-requests/`: what the add-on needed from the Thaumcraft API and how each request was answered.
- [docs/HOOKS.md](docs/HOOKS.md) and [docs/NEOFORGE.md](docs/NEOFORGE.md): the loader events, and the NeoForge build.
- [docs/AUDIT.md](docs/AUDIT.md): the defect audit of 2026-11-01.

The add-on talks to Thaumcraft only through the public `thaumcraft.api`; a JUnit test (`TinkererApiOnlyTest`) fails the build if a source
file imports anything else of Thaumcraft. Until 2026-10-31 the add-on was written inside the
[Thaumcraft 4 Reload repository](https://github.com/acak1221/Thaumcraft-1.21.1) (`NOTICE.md`, "History"); that repository keeps the API
extensions the add-on needs and its guides for add-on authors.

## Credits and licence

Thaumic Tinkerer was written by **Vazkii**, continued by **pixlepix** and **nekosune** and maintained for 1.7.10 by the
**GTNewHorizons** contributors. Thaumcraft 4 is the work of **Azanor**. This port changes none of that authorship.

**CC BY-NC-SA 3.0** for the add-on (`LICENSE`, `NOTICE.md`): non-commercial, share-alike. The build files, tools and workflows of this
repository are MIT (`LICENSE-MIT`); the Gradle wrapper is Gradle's own (Apache-2.0). `NOTICE.md` lists what falls under which.
