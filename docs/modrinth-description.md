![Pickup Card](https://raw.githubusercontent.com/E33EPUS/PickupCard/main/design/banner.png)

# Pickup Card

_Every item you pick up becomes a card on your HUD_

![MC](https://img.shields.io/badge/MC-1.20.1-green) ![Loader](https://img.shields.io/badge/Loader-Forge-red) ![Side](https://img.shields.io/badge/Side-Client-blue) ![Java](https://img.shields.io/badge/Java-17%2B-yellow) ![Version](https://img.shields.io/github/v/release/E33EPUS/PickupCard?sort=semver) ![License](https://img.shields.io/badge/License-MIT-brightgreen)

Pickup Card deals every pickup into the corner of your HUD as a frosted-glass card: the item icon, its name and how much you just gained, with rarity driving the accent colour. Grabbing the same thing again does not spam the screen — it merges into the same card and the count rolls from the old value up to the new one.

Cards are drawn as vectors with [NanoVG](https://github.com/memononen/nanovg) — no vanilla toast textures, and no rendering library to install. The mod is **client-side only**: drop the JAR into `.minecraft/mods/`, nothing goes on the server, and it needs no other mod.

## Features

*   🃏 **Cards for items and XP** — rarity bar, item icon, name and the amount gained; XP cards use a nether star and their own green
*   ➕ **Merging** — four modes, from "same item and NBT" to "never merge"; the card pulses once and the count rolls from the old value up
*   🔇 **Three filter lists** — blacklist, whitelist and muted; rules accept `minecraft:stone`, `#forge:ores` and `@somebotania`, plus a built-in ignore list for the usual spam items
*   🌈 **Rarity accent** — the bar and the count share vanilla's four tier colours; with [RarityCore](https://modrinth.com/mod/raritycore) installed its seven tiers and your custom colours take over, with a shimmer and a tiered glow on the high tiers
*   🆕 **NEW badge** — items you have not seen this session light up once (not persisted: another world resets it)
*   🧍 **Only your pickups** — other players looting, or a zombie picking up gear, never puts a card on your screen
*   📐 **Placement & stacking** — the anchor is draggable and right-aligned by default; the newest card sits against the hotbar's top edge and pushes older ones up; mirror, spacing and scale (automatic down to 60%, or manual 50%–200%) are all yours
*   🚦 **Queue instead of dropping** — a full screen queues new pickups; a full queue collapses into one "N more" card
*   🎛️ **Every animation switchable** — entrance, merge pulse and glow breathing each have their own switch, and entrance and exit each come in several shapes
*   🎨 **Card look is data** — colours, radius, border, padding and timings live in `assets/pickupcard/styles/*.json`; override it in a resource pack and it takes effect within a second
*   ⚙️ **Config screen** — press **K**: paged, scrollable, hover explanations, and a live preview that replays the whole timeline
*   ⚡ **Light on the frame** — 5 cards in steady state cost about 0.9 ms; the first card after joining went from 42 ms to 1.6 ms

## Configuration

*   **In-game (recommended)** — press **K**; it shows the values in effect and writes them to disk immediately
*   **File** — `config/pickupcard-client.toml`; the deprecated keys `stickTo`, `leftEdge`, `merge.enabled` and `merge.windowMs` are ignored
*   **Commonly changed** — `notice.holdMs` (4000), `notice.exitMs` (480; `0` = no exit), `merge.mode` (`SAME_NBT`), `layout.maxOnScreen` (5), `layout.queueSize` (9; `0` = no queue), `layout.scalePercent` (`0` = automatic), `layout.align` (`RIGHT`), `layout.mirrorCard` (off), `layout.anchorX` / `anchorY` (`-1` = automatic), `count.format` (`PLUS` / `X_PREFIX` / `PLAIN` / `ABBREVIATED`)

## Compatibility

| | |
| --- | --- |
| Minecraft 1.20.1 + Forge 47.4.x | ✅ The shipping target, tested in play |
| Server without the mod | ✅ Every feature works (client-side) |
| [UI Deck](https://github.com/E33EPUS/UIDeck) alongside | ✅ NanoVG comes from a nested JarJar, no package clash |
| [RarityCore](https://modrinth.com/mod/raritycore) | 🟡 Optional; falls back to vanilla's four tiers |
| Minecraft 1.21.1 (Fabric / NeoForge / Forge) | ❌ Not implemented |
| Minecraft 1.20.1 Fabric / NeoForge | ❌ Not implemented |

## Known Limitations

*   **1.20.1 Forge only** — the 1.21.1 targets are recorded as not implemented in `versions/targets.json`
*   **The muted list does not mute sound** — the card still appears, just without the highlight
*   **English labels shrink on a narrow canvas** — at the largest GUI scale (320×180) they get small, though they never overlap
*   **Long filter rules need a hover** — a rule row shows one line; the full text is in the strip below it
*   **No server component** — renamed items and NBT come from grabbing the real entity before it is removed, which covers almost every case

## FAQ

**Do I need it on the server?** No — there is no server component. Any server will do, vanilla or modpack.

**Will I see other players' pickups?** No. Only your own pickups produce a card.

**A whole stack, but only one card?** That is merging. Set `merge.mode` to `NEVER` for a separate card every time.

**I changed the TOML and nothing happened.** Check for the deprecated keys first; appearance settings are not in the TOML at all.

**No cards at all?** Check, in order: the master switch `enabled`, the blacklist on the filter page, then whether the screen and queue limits are full.

**The config screen will not open?** The key is **K** (rebindable under vanilla Controls); if another mod claims it, use the TOML or pick another key.

**Can I put it in a modpack?** Yes — the code is MIT and needs no extra permission; if you redistribute the JAR, keep the third-party notices.

## Changelog

See the [GitHub CHANGELOG](https://github.com/E33EPUS/PickupCard/blob/main/CHANGELOG.md) — or the [latest release notes](https://github.com/E33EPUS/PickupCard/releases).

## Feedback

Found a bug or have a suggestion? Open an [issue](https://github.com/E33EPUS/PickupCard/issues), or leave a comment on this page.
