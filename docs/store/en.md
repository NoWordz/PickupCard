Every item you pick up becomes a card on your HUD

# Overview

- Pickup Card pops a card whenever you pick up an item or XP: item icon, name and how much you gained, with rarity deciding the accent colour — picking things up is no longer just a "ding" and an extra slot in your inventory;
- A client-side mod: nothing goes on the server, it works on any server and in any modpack, with no required dependencies;
- Every card is drawn as vectors with NanoVG, with no vanilla hint textures and no rendering library to install.

# Features

- 🃏 **Card notices** — a card per pickup, items or XP: rarity bar + item icon + name + count;
- ➕ **Merging** — grabbing the same thing again merges into the same card (four modes), with the count rolling up instead of spamming the screen;
- 🔇 **Three filter lists** — blacklist (never shown), whitelist (always shown and highlighted) and muted (shown, but not highlighted); rules accept minecraft:stone / #forge:ores / @somebotania, and the built-in ignore list for the usual spam items can be switched off wholesale;
- ✨ **Rarity accent** — vanilla's four tier colours (white / yellow / aqua / purple); with RarityCore installed it switches to its seven tiers and your own colours, plus an entrance shimmer and a tiered glow on high-rarity cards;
- 🆕 **NEW badge** — items you have not seen this session light up once;
- 🧍 **Only your pickups** — the signal is filtered by picker, so other players looting or a zombie picking up gear never puts a card on your screen;
- 📐 **Placement & stacking** — cards land in the strip to the right of the hotbar, right-aligned by default; the anchor is draggable, the card can be mirrored (bar moved to the far right), spacing is adjustable, and scaling is automatic (shrinks only when needed, floor 60%) or manual (50%–200%);
- 🚦 **Queue when full** — a full screen queues new pickups instead of pushing old cards out; when the queue is full too they collapse into one "N more" overflow card;
- 🎛️ **Every animation switchable** — entrance, merge pulse and glow breathing each have their own switch; entrance and exit each come in several shapes (slide / wipe, fade / train retract / wipe close);
- 🎨 **Card look is data** — colours, corner radius, border, padding and animation timings live in a theme JSON; change one file and it takes effect within a second, no restart;
- ⚙️ **Config screen** — press K: paged, scrollable, hover explanations, and a preview that uses the real card painter and replays the whole timeline, so any timing key you touch is visible immediately.

# Compatibility

| Environment / mod | Status |
| --- | --- |
| Minecraft 1.20.1 + Forge 47.4.x | ✅ The shipping target, tested in play |
| Server without the mod | ✅ Every feature works (client-side) |
| RarityCore | 🟡 Optional: rarity tiers and colours; falls back to vanilla's four tiers without it |
| UI Deck installed alongside | ✅ NanoVG comes from a nested JarJar, no package clash |
| Minecraft 1.21.1 (Fabric / NeoForge / Forge) | ❌ Not implemented |
| Minecraft 1.20.1 Fabric / NeoForge | ❌ Not implemented |

# Configuration

Changing things in game is recommended — press K; the screen shows the values in effect and writes them to disk immediately. The file behind it is `config/pickupcard-client.toml`, and the commonly changed keys are:

| Key | Default | Meaning |
| --- | --- | --- |
| `notice.holdMs` | 4000 | how long a card stays (ms), counted from its last refresh |
| `notice.exitMs` | 480 | exit animation length; 0 = disappear at once |
| `merge.mode` | SAME_NBT | how eagerly pickups merge (four modes) |
| `layout.maxOnScreen` | 5 | how many cards fit on screen |
| `layout.queueSize` | 9 | queue length; 0 = no queue |
| `layout.scalePercent` | 0 | 0 = automatic (shrinks only when needed, floor 60%); manual 50–200 |
| `layout.align` | RIGHT | left-edge or right-edge alignment |
| `layout.mirrorCard` | false | mirror the card: bar on the far right, animations flipped too |
| `count.format` | PLUS | `+64` / `×64` / `64` / `+1.2K` |

The deprecated keys `stickTo` and `leftEdge` are read and ignored (replaced by `anchorX` / `anchorY`). **A card's appearance is not in the TOML** — it lives in the theme JSON, see "Card look is data" above.

# Known Limitations

- Only 1.20.1 Forge ships (the 1.21.1 targets are recorded as not implemented);
- The muted list only affects emphasis — it does **not** mute vanilla's pickup sound;
- At the largest GUI scale (a 320×180 canvas) English interface labels shrink a little — they do not overlap, but they are not comfortable to read;
- No server component, so server-only information is out of reach: renamed items and NBT take the route of grabbing the real entity before it is removed, which covers almost every case.

# FAQ

- **Do I need it on the server?** No — there is no server component; it works on any server, including vanilla servers and other people's modpacks;
- **Will I see cards when other players pick things up?** No. Only your own pickups pop a card;
- **I picked up a whole stack — why is there only one card?** That is merging; set `merge.mode` to `NEVER` for a separate card every time;
- **I changed the TOML and nothing happened.** Check for leftover old keys first (`stickTo` / `leftEdge` / `merge.enabled` / `merge.windowMs` are deprecated and ignored); appearance settings live in the theme JSON, not in the TOML;
- **No cards at all?** Check in order: the master switch `enabled`, the blacklist on the filter page, then whether the on-screen and queue limits are full;
- **The config screen will not open?** The key is K (rebindable in vanilla Controls); if another mod has taken it, use the TOML or pick another key.

# Feedback

https://github.com/E33EPUS/PickupCard/issues
