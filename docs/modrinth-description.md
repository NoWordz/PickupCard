![Pickup Card](https://github.com/user-attachments/assets/5bbce647-df3a-4c51-b87b-d63dfc7c4bdc)

# Pickup Card

_Every item you pick up becomes a card on your HUD_

![MC](https://img.shields.io/badge/MC-1.20.1-green) ![Loader](https://img.shields.io/badge/Loader-Forge-red) ![Side](https://img.shields.io/badge/Side-Client-blue) ![Java](https://img.shields.io/badge/Java-17%2B-yellow) ![Version](https://img.shields.io/github/v/release/E33EPUS/PickupCard?sort=semver) ![License](https://img.shields.io/badge/License-MIT-brightgreen)

Pickup Card deals every pickup into the corner of your HUD as a frosted-glass card: the item icon, its name and how much you just gained, with rarity driving the accent colour. Grabbing the same thing again does not spam the screen — it merges into the same card and the count rolls from the old value to the new one.

Cards are drawn as vectors with [NanoVG](https://github.com/memononen/nanovg): no vanilla toast textures, and no rendering library to install.

The mod is **client-side only** — nothing goes on the server, it works on any server and in any modpack, and it needs no other mod. A card's look is data rather than code: colours, corner radius, border, padding and animation timings live in a theme JSON you can override with a resource pack.

## Features

*   🃏 **Card notices** — one card per pickup, items and XP alike: rarity bar, item icon, name and the amount gained; XP cards use a nether star and their own green
*   ➕ **Merging** — four modes, from "same item and NBT" to "never merge"; merging pulses the card once and rolls the count from the old value up to the new one
*   🔇 **Three filter lists** — blacklist (never shown), whitelist (always shown and highlighted) and muted (shown, but not highlighted); rules accept `minecraft:stone`, `#forge:ores` and `@somebotania`, plus a built-in ignore list for the usual spam items that can be switched off wholesale
*   🆕 **NEW badge** — items you have not seen this session light up once (deliberately not persisted: enter another world and it resets)
*   🌈 **Rarity accent** — the bar and the count share vanilla's four tier colours; with [RarityCore](https://modrinth.com/mod/raritycore) installed its seven tiers and your custom colours take over, plus an entrance shimmer and a tiered glow on the high tiers
*   🧍 **Only your pickups** — the signal is filtered by picker, so other players looting or a zombie picking up gear never puts a card on your screen
*   📐 **Placement & stacking** — the anchor is draggable and defaults to the strip beside the hotbar, right-edge aligned; the newest card appears against the hotbar's top edge and pushes older ones up; the card can be mirrored (bar to the right edge, content reversed), spacing is adjustable, and scaling is automatic (shrinks only when needed, floor 60%) or manual (50%–200%)
*   🚦 **Queue instead of dropping** — when the on-screen limit is full, new pickups wait in a queue; when the queue is full too, they collapse into a single "N more" overflow card
*   🎛️ **Every animation switchable** — entrance, merge pulse and glow breathing each have their own switch; entrance and exit each come in several shapes (slide / wipe out, fade / train retract / wipe closed)
*   🎨 **Card look is data** — colours, corner radius, border, padding and animation timings live in `assets/pickupcard/styles/*.json`; drop a copy into a resource pack at the same path and it takes effect within a second, no restart
*   ⚙️ **Config screen** — press **K**: paged, scrollable, hover explanations, a live preview that uses the real card painter and replays the whole timeline, and a per-page reset
*   ⚡ **Light on the frame** — five cards in steady state cost about 0.9 ms of drawing; the first card after joining used to cost 42 ms and now costs 1.6 ms thanks to a seven-frame warm-up, with a hard frame budget that logs when it is exceeded

## How to use

1.  Join a world and pick something up — a card appears beside the hotbar
2.  Press **K** to open the config screen: pages on the left, values on the right; the placement page lets you drag the anchor
3.  Find something too noisy? Put it in the blacklist on the filter page, or in the whitelist to make it stand out every time
4.  Want a separate card every time? Set `merge.mode` to `NEVER`

## Installation

| Requirement | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.x |
| Java | 17+ |
| Required dependencies | none |
| Optional | [RarityCore](https://modrinth.com/mod/raritycore) for seven-tier rarity and custom colours |

Download the JAR and drop it into `.minecraft/mods/`. Nothing goes on the server, and the mod works on any server or modpack. It also coexists with [UI Deck](https://github.com/E33EPUS/UIDeck): NanoVG is supplied through a nested JarJar, so the two do not fight over the same packages.

## Configuration

*   **In-game (recommended)** — press **K**; the screen shows the values that are actually in effect and writes them to disk immediately
*   **Config file** — `config/pickupcard-client.toml` backs the screen for anyone who prefers text
*   **Commonly changed** — `notice.holdMs` (4000), `notice.exitMs` (480; `0` = no exit animation), `merge.mode` (`SAME_NBT`), `layout.maxOnScreen` (5), `layout.queueSize` (9; `0` = no queue), `layout.scalePercent` (`0` = automatic), `layout.align` (`RIGHT`), `layout.mirrorCard` (off), `layout.anchorX` / `anchorY` (`-1` = automatic), `count.format` (`PLUS` / `X_PREFIX` / `PLAIN` / `ABBREVIATED`)
*   **Card appearance is not in the TOML** — take `assets/pickupcard/styles/default.json` out of the JAR, drop it into a resource pack at the same path, and the theme is yours; the model is "the theme supplies the defaults, the TOML only overrides what you changed", so changing the theme does not throw away your own tweaks
*   **Deprecated keys** — `stickTo`, `leftEdge`, `merge.enabled` and `merge.windowMs` are read and ignored

## Compatibility

| Mod / version | Status |
| --- | --- |
| Minecraft 1.20.1 + Forge 47.4.x | ✅ The shipping target, tested in play |
| Server without the mod | ✅ Every feature works (the mod is client-side) |
| [UI Deck](https://github.com/E33EPUS/UIDeck) installed alongside | ✅ NanoVG comes from a nested JarJar, no package clash |
| [RarityCore](https://modrinth.com/mod/raritycore) | 🟡 Optional integration (rarity tiers and colours); falls back to vanilla's four tiers without it |
| Minecraft 1.21.1 (Fabric / NeoForge / Forge) | ❌ Not implemented |
| Minecraft 1.20.1 Fabric / NeoForge | ❌ Not implemented |

## Known limitations

1.  **1.20.1 Forge is the only shipping target** — the 1.21.1 targets are recorded as not implemented in `versions/targets.json`
2.  **The muted list does not mute sound** — it only affects emphasis: the card still appears, just without the highlight (vanilla's pickup sound sits at an injection point that cannot be cancelled on 1.20.1)
3.  **English labels shrink on a narrow canvas** — at the largest GUI scale (a 320×180 canvas) English chip labels get noticeably small; they do not overlap, but they are not pleasant to read
4.  **Long filter rules need a hover** — a rule row shows one line, the full text is in the explanation strip below it
5.  **No server component, so server-only information is out of reach** — renamed items and NBT take the route of grabbing the real entity before it is removed, which covers the overwhelming majority of cases

## FAQ

**Do I need to install it on the server?** No — there is no server component at all. It works on any server, including vanilla servers and other people's modpacks.

**Will I see cards when other players pick things up?** No. Only your own pickups produce a card.

**I picked up a whole stack — why is there only one card?** That is merging. Set `merge.mode` to `NEVER` if you want a separate card every time.

**I changed the TOML and nothing happened?** Check for leftover deprecated keys first (`stickTo` / `leftEdge` / `merge.enabled` / `merge.windowMs` are ignored). Appearance settings are not in the TOML at all — they live in the theme JSON.

**No cards at all?** Check, in order: the master switch `enabled`, the blacklist on the filter page, and whether the on-screen and queue limits are full.

**The config screen will not open?** The key is **K** (rebindable under vanilla Controls). If another mod claims it, edit the TOML instead or pick another key.

**Can I put it in a modpack?** Yes. The code is MIT and needs no extra permission; if you redistribute the JAR, keep the third-party notices from the GitHub repository.

## Changelog

See the [GitHub CHANGELOG](https://github.com/E33EPUS/PickupCard/blob/main/CHANGELOG.md) — or the [latest release notes](https://github.com/E33EPUS/PickupCard/releases).

## Feedback

Found a bug or have a suggestion? Open an [issue](https://github.com/E33EPUS/PickupCard/issues), or leave a comment on this page.
