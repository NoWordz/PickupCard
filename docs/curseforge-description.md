**Every item you pick up becomes a card on your HUD — items and XP, mergeable, filterable, themeable.**

Pickup Card is a **client-side** Minecraft 1.20.1 (Forge) mod that deals your pickups into the corner of the screen as frosted-glass cards: the item icon, its name, and how much you just gained, with rarity driving the accent colour. Zero required dependencies, nothing to install on the server, and it works on any server or modpack.

Everything is drawn as vectors with NanoVG — no vanilla toast textures, and no rendering library to install.

*(This description is pasted by hand: CurseForge's official API has no endpoint for editing a project description.)*

## Features

- **Merging** — grab the same thing again and it merges into the same card: it pulses once and the count rolls from the old value to the new one. Four merge tiers, from "same item and NBT" to "never merge".
- **Three filter lists** — blacklist (never shown), whitelist (always shown and highlighted) and muted (shown, but not highlighted). Rules accept `minecraft:stone`, `#forge:ores` and `@somebotania`, plus a built-in ignore list for the usual spam items.
- **NEW badge** — items you have not seen this session light up once (deliberately not persisted; a new world resets it).
- **Rarity accent** — the bar and the count share the accent colour across vanilla's four tiers. With RarityCore installed, its seven tiers and your custom colours take over, and high tiers gain an entrance shimmer and a tiered glow.
- **Placement and stacking** — cards land in the strip to the right of the hotbar, right-aligned by default. Drag the anchor, mirror the card, change the spacing, or scale automatically (down to 60%) or by hand (50%–200%).
- **Queued instead of dropped** — when the on-screen limit is full, new pickups queue up instead of pushing older cards out; when the queue is full too, they collapse into a single "N more" overflow card.
- **Every animation can be turned off** — entrance, merge pulse and glow breathing each have their own switch.
- **Only your pickups** — other players looting, or zombies picking up gear, never put a card on your screen.

## Configuration

Everything is configurable in game: press **K** and use the config screen, which previews a real card and replays the whole timeline as you change timings. A `config/pickupcard-client.toml` file backs it for anyone who prefers editing text.

The card's look is data, not code: take `assets/pickupcard/styles/default.json` out of the jar, drop it into a resource pack, and the colours, corner radius, border, padding and animation timings are yours — effective within a second, no restart.

## Requirements

| | |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.x |
| Required dependencies | none |
| Optional | RarityCore, for seven-tier rarity and custom colours |

Client-side only. Nothing needs to be installed on the server, and no server-side information is required.

## Known limitations

- 1.20.1 Forge is the only shipping target; the 1.21.1 targets are recorded as not implemented.
- The muted list affects emphasis only — it does **not** mute vanilla's pickup sound.
- At the largest GUI scale (a 320×180 canvas), English labels shrink noticeably. They do not overlap, but they are not pleasant to read.

## License

MIT. Bundled components (the NanoVG bindings with their four-platform natives, and LWJGL) are listed in `THIRD_PARTY_NOTICES.md` inside the jar.
