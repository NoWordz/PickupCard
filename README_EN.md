[简体中文](README.md) | [English](README_EN.md)

<p align="center">
<img width="512" height="257" alt="PickupCard" src="https://github.com/user-attachments/assets/5bbce647-df3a-4c51-b87b-d63dfc7c4bdc" />
</p>

<h1 align="center">Pickup Card</h1>

<p align="center">
  <em>Every item you pick up becomes a card on your HUD</em>
</p>

<p align="center">
  <img alt="MC" src="https://img.shields.io/badge/MC-1.20.1-green">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Forge-red">
  <img alt="Side" src="https://img.shields.io/badge/Side-Client-blue">
  <img alt="Java" src="https://img.shields.io/badge/Java-17%2B-yellow">
  <img alt="Version" src="https://img.shields.io/github/v/release/NoWordz/PickupCard?sort=semver">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-brightgreen">
</p>

<p align="center">
  <a href="https://github.com/NoWordz/PickupCard/actions/workflows/build.yml"><img alt="Build" src="https://github.com/NoWordz/PickupCard/actions/workflows/build.yml/badge.svg?branch=main"></a>
</p>

> Latest release on [Releases](https://github.com/NoWordz/PickupCard/releases) · [Changelog](CHANGELOG.md) · Client-side only, no required dependencies.

## What it is

Pick up an item or some XP and a frosted-glass card pops into the bottom-right of your HUD: the item icon, its name, and how much you just gained, with the accent colour carrying the rarity. Grabbing the same thing again does not spam the screen — it merges into the same card and the count rolls from the old value to the new one.

Everything is drawn as vectors with [NanoVG](https://github.com/memononen/nanovg): no vanilla toast textures, and no rendering library to install. The mod is **client-side only** — nothing goes on the server, and it works on any server or modpack.

## Installation

| Dependency | Requirement |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.x (the ForgeGradle 6 line) |
| Required dependencies | **None** |
| Optional integration | [RarityCore](https://modrinth.com/mod/raritycore): if present, its seven rarity tiers and custom colours take over; otherwise vanilla's four tiers are used |

Drop the jar into `mods/` and you are done.

## Quick start

1. Join a world and pick something up — a card appears in the bottom-right corner.
2. Press **K** to open the config screen: pages on the left, values on the right; the `Placement & stacking` page lets you drag the anchor directly.
3. If an item is too noisy, put it on the blacklist on the `Filter` page; if you never want to miss it, put it on the whitelist.

## Features

- **Merging** — grabbing the same thing again merges into the same card (`merge.mode`, four tiers: same item + NBT / same item / same item except renamed / never); the card pulses once and the count rolls from the old value to the new one.
- **Three filter lists** — blacklist (never shown), whitelist (always shown and highlighted), muted (still pops, but is never highlighted and its pickup sound is muted). Rules accept `minecraft:stone`, `#forge:ores` and `@somebotania`. Nothing is dropped by default.
- **NEW badge** — items seen for the first time this session light up once. Deliberately not persisted: changing worlds resets it.
- **Rarity accent** — the bar and the count share the accent colour, four vanilla tiers (common / uncommon / rare / epic). With RarityCore installed you get its seven tiers, plus an entrance shimmer on high tiers and a glow that steps up by tier.
- **XP cards** — experience orbs pop a card too (nether star icon, its own green), sharing the same merge and filter rules.
- **Only your pickups** — the signal source is filtered by the picking player, so other players looting, or zombies picking up gear, never put a card on your screen.
- **Magnet detection** — when a magnet upgrade (e.g. Sophisticated Backpacks) pulls items into your inventory, a card pops too; it fires immediately when no other player is within 16 blocks, and near other players it confirms ownership by your own inventory changes. Vanilla hoppers emptying a whole stack and absorbed XP can't be detected.
- **Counting mode** — the number on a card is either this pickup (default, with a `+`) or the held total: it follows your vanilla inventory live (41 slots including armor and offhand), rising as you pick up and dropping as you spend.
- **Placement and stacking** — cards land in the strip to the right of the hotbar, right-aligned by default; the anchor is draggable, the card can be mirrored (bar on the far right), spacing is adjustable, and scaling can be automatic or manual (50%–200%).
- **When the screen is full** — by default a new card replaces the oldest one right away; you can switch back to queueing, where a full screen and a full queue collapse into a single "N more" overflow card.
- **Every animation can be turned off** — entrance, merge pulse and glow breathing each have their own switch; entrances come in four shapes (slide / clip / bounce / drop), exits in five (fade / train back / wipe / fall / shrink), plus an idle sway (off by default).

## Configuration

The config file is `config/pickupcard-client.toml`, but **the config screen (K) is the recommended way in** — it shows the effective values and writes them out immediately. The entries people actually touch:

| Key | Default | What it does |
| --- | --- | --- |
| `notice.holdMs` | 4000 | How long a card stays, measured from its last refresh |
| `notice.exitMs` | 480 | Exit animation length; 0 = vanish instantly |
| `merge.mode` | SAME_NBT | Merge granularity, four tiers |
| `layout.maxOnScreen` | 5 | How many cards on screen at once |
| `layout.queueSize` | 9 | Queue limit; 0 = no queue (a full screen drops pickups) |
| `layout.scalePercent` | 0 | 0 = automatic (shrink only when needed, floor 60%); manual 50–200 |
| `layout.align` | RIGHT | Left-aligned or right-aligned |
| `layout.mirrorCard` | false | Mirrored card: the bar moves to the far right and the animations flip with it |
| `layout.anchorX` / `anchorY` | -1 | Landing spot as a screen fraction; -1 = automatic. **Dragging it in the config screen is easier** |
| `count.format` | PLUS | `+64` / `×64` / `64` / `+1.2K` |

The deprecated keys `stickTo` and `leftEdge` are ignored when read (they were replaced by `anchorX` / `anchorY`). **The look of the card is not in the TOML** — see the next section.

## Custom card skins

Colours, corner radius, border, padding and animation timings are all data: changing the look means changing one file.

1. Take `assets/pickupcard/styles/default.json` out of the jar.
2. Put it into a resource pack at the same path (or edit the copy in your instance).
3. Save — it takes effect within a second, no restart needed.

The model is "the theme provides defaults, the TOML only overrides what you changed", so switching themes never throws away your own tweaks. The design source of truth and the parameter cross-reference live in [docs/design.md](docs/design.md); every tunable parameter is defined in exactly one place, [design/tokens.css](design/tokens.css).

## Compatibility

| Combination | Status |
| --- | --- |
| Minecraft 1.20.1 + Forge 47.4.x | ✅ The shipping target, running on real instances |
| Client only (nothing installed server-side) | ✅ Everything works |
| Installed alongside [UI Deck](https://github.com/E33EPUS/UIDeck) | ✅ nanovg is supplied through JarJar nesting, so there is no package conflict |
| [RarityCore](https://modrinth.com/mod/raritycore) | 🟡 Optional integration (rarity tiers and colours) |
| gnetum | 🟡 Throttles HUD rendering to its own fps cap, which stutters pickup animations — raise or disable the cap in gnetum's settings (confirmed on a real modpack) |
| Minecraft 1.21.1 (Fabric / NeoForge / Forge) | ❌ Not implemented — see below |
| Minecraft 1.20.1 Fabric / NeoForge | ❌ Not implemented |

## Known limitations

- **1.20.1 Forge is the only shipping target.** The three 1.21.1 targets are explicitly recorded as `buildable: false` in `versions/targets.json`: the matrix is a rule, so cells we have not done have to be visible in the file rather than forgotten.
- **English labels shrink on very narrow canvases.** At 320×180 (largest `guiScale`) English chip labels get noticeably small — no overlap, but not pleasant to read.
- **Long filter rules need a hover to read in full.** A rule row shows one line; the whole rule is in the hint bar along the bottom.
- **No server component, so server-only information is out of reach.** Renamed items and NBT come from grabbing the entity just before it is removed, which covers the vast majority of cases.
- **Two blind spots in magnet detection.** A vanilla hopper emptying a whole stack removes the entity directly (undetectable); XP absorbed by magnets is undetectable too. In multiplayer, when other players are nearby the mod switches to strict confirmation (waiting for your own inventory to change), and some container mods may therefore be missed.

## FAQ

**Do I need to install it on the server?** No, and there is no server component to install. It works on any server, vanilla or modded.

**Will I see cards when other players pick things up?** No. Only your own pickups pop a card.

**I picked up a whole stack — why is there only one card?** That is merging. Set `merge.mode` to `NEVER` if you want one card per pickup.

**I edited the TOML and nothing happened?** Check for leftover deprecated keys (`stickTo` / `leftEdge` / `merge.enabled` / `merge.windowMs` are ignored now) — and note that the card's look lives in the theme JSON, not in the TOML.

**No cards at all?** Check, in order: the master switch `enabled`, the blacklist on the filter page, and whether the on-screen and queue limits are full.

**The config screen will not open?** The key is K (rebindable in vanilla controls). If another mod claims it, edit the TOML instead or pick another key.

## More documentation

- [docs/architecture.md](docs/architecture.md) — layering and wiring, the rendering path, how it is packaged
- [docs/design.md](docs/design.md) — card design and the parameter cross-reference
- [docs/decision-rendering.md](docs/decision-rendering.md) — the rendering decision and who lost
- [CHANGELOG.md](CHANGELOG.md) — per-version changes

## Development

This repository is **single-branch, multi-target**: `shared/` holds platform-independent logic, `layers/` holds code split by mappings / loader / version, and `platforms/<target>/` holds one Gradle project per target; which targets exist and which layers each one mounts is declared entirely in `versions/*.json`.

```bash
cd platforms/1.20.1-forge && ./gradlew build    # compile + unit tests
python tools/verify_targets.py                   # structural consistency (CI's first gate)
```

Artifacts land in `platforms/1.20.1-forge/build/libs/`.

## License

MIT — see [LICENSE](LICENSE). Licences for bundled components (the NanoVG bindings with their four-platform natives, and LWJGL) are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Author: [扭曲 (E33EPUS)](https://github.com/E33EPUS) · Repository: [NoWordz/PickupCard](https://github.com/NoWordz/PickupCard) · Issues: [Issues](https://github.com/NoWordz/PickupCard/issues)

Copyright (c) 2026 扭曲 (E33EPUS)
