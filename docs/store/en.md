# Overview

Pickup Card turns every item you pick up into a card in the bottom-right corner of your HUD: the item icon, its name and how much you picked up, with rarity deciding the accent colour. Picking things up is no longer just a "ding" and an extra slot in your inventory.

A client-side mod: nothing goes on the server, it works on any server and in any modpack, and it has no required dependencies — every card is drawn as vectors with NanoVG, with no vanilla hint textures and no rendering library to install.

# Features

- 🃏 **Card notices** — picking up an item or XP pops a card: rarity bar + item icon + name + count; grabbing the same thing again merges into the same card and the count rolls up instead of spamming the screen;
- 🔇 **Three filter lists** — blacklist (never shown), whitelist (always shown and highlighted) and muted (shown, but not highlighted); rules accept minecraft:stone / #forge:ores / @somebotania, and the built-in ignore list for the usual spam items can be switched off wholesale;
- ✨ **Rarity accent** — vanilla's four tier colours (white / yellow / aqua / purple); with RarityCore installed it switches to its seven tiers and your custom colours, plus an entrance shimmer and a tiered glow on high-rarity cards;
- 🆕 **NEW badge** — items you have not seen this session light up once;
- 📐 **Placement & stacking** — cards land in the strip to the right of the hotbar, right-aligned by default; the anchor is draggable, the card can be mirrored (bar moved to the far right), spacing is adjustable, and scaling is automatic (shrinks only when needed, floor 60%) or manual (50%–200%);
- 🚦 **Queue when full** — a full screen queues new pickups instead of pushing old cards out; when the queue is full too they collapse into one "N more" overflow card;
- 🎛️ **Every animation switchable** — entrance, merge pulse and glow breathing each have their own switch; entrance and exit each come in several shapes (slide / wipe, fade / train retract / wipe close);
- 🎨 **Card look is data** — colours, corner radius, border, padding and animation timings live in a theme JSON; change one file and it takes effect within a second, no restart;
- ⚙️ **Config screen** — press K: paged, scrollable, hover explanations, and a preview that uses the real card painter and replays the whole timeline, so any timing key you touch is visible immediately.

# Installation

Needs Minecraft 1.20.1 + Forge 47.4.x, with no other required dependencies. Drop the jar into mods/ and you are done — nothing needs to be installed on the server.

# Known Limitations

- Only 1.20.1 Forge ships;
- The muted list only affects emphasis — it does **not** mute vanilla's pickup sound;
- At the largest GUI scale (a 320×180 canvas) English interface labels shrink a little — they do not overlap, but they are not comfortable to read.

# FAQ

**Do I need it on the server?** No — it has no server component. It works on any server, including vanilla servers and other people's modpacks.

**Will I see cards when other players pick things up?** No. Only your own pickups pop a card.

**I picked up a whole stack — why is there only one card?** That is merging. Set `merge.mode` to `NEVER` if you want a separate card every time.

**I changed the TOML and nothing happened?** Check for leftover old keys first (`stickTo` / `leftEdge` / `merge.enabled` / `merge.windowMs` are deprecated and ignored); appearance settings live in the theme JSON, not in the TOML.

**No cards at all?** Check in order: the master switch `enabled`, the blacklist on the filter page, and whether the on-screen and queue limits are full.

**The config screen will not open?** The key is K (rebindable in vanilla Controls). If another mod has taken it, use the TOML or pick another key.

# Feedback

[Open an issue](https://github.com/E33EPUS/PickupCard/issues) or leave a comment below.
