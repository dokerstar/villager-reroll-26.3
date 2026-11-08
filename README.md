# Villager Trade Reroller for Minecraft 26.3 (Fabric)

A client-side Fabric mod for Minecraft 26.3. It rerolls librarian trades by breaking and replacing the lectern, opening the trade screen, and reading the enchanted books on offer. It stops when it finds the book you asked for.

It does not treat a swing or a fixed delay as a broken lectern. The next place or trade check waits until this client has the server's settled block state for that position: the break prediction has been acknowledged, the chunk is loaded, and the block there is air. If that confirmation does not arrive, the cycle stops and says why instead of placing another lectern.

The GUI, HUD, status lines, errors, command replies, and keybind names are in English and Russian (`en_us` and `ru_ru`). Russian is used when the game language is Русский (Россия). English remains the fallback.

This is a fork of [Villager Reroll by Joplayz](https://github.com/Joplayz/villager-reroll-26.1.2), updated to Minecraft 26.3 and reworked so it holds up in survival on a multiplayer server. The reroll loop and the idea are Joplayz's. I fixed what broke for me on a survival server and added the things I kept wishing it had.

## What's different from the original

The original targets Minecraft 26.1.2 / 26.2. This version:

- Runs on **Minecraft 26.3** with Fabric Loader 0.19.5 and Fabric API 0.162.0.
- Waits for server confirmation that the chosen lectern is gone before the next place or trade check. A sent hit or a timer is not enough. Up to three break attempts, then a stop with a chat message if the break is still unconfirmed, the spot has a different block, the chunk is unloaded, or the world changes.
- Includes a Russian translation (`assets/villager-reroll/lang/ru_ru.json`) for the screens, HUD, statuses, errors, `/reroll` replies, and keybinds. `en_us.json` stays as the fallback.
- Finds your lectern in the hotbar, the offhand, or anywhere in your inventory. The original only checked the hotbar, so survival players who kept lecterns in the offhand or main inventory got `No lectern in hotbar!` and the mod stopped.
- Walks over to a lectern that drops out of pickup range, picks it up, and walks back to where you were standing. It sneaks the whole way, so it won't walk you off a ledge.
- Lets you pick the exact villager to reroll. Look at it and run `/reroll villager`, and only you will see it outlined. No more rerolling the wrong librarian in a crowded trading hall.
- Adds `/reroll` chat commands that run only on your client. Nothing is sent to the server, so you can set everything up without opening the config screen. This is handy when another mod (Wurst, for example) takes over the pause-menu button that Mod Menu normally uses.
- Lets you resize and move the HUD (scale 0.5 to 2.0, six screen positions, plus a one-line compact mode) so it stops covering the villager.
- Fixes a few bugs that show up in survival:
  - The set-position key now saves the empty space above the block you look at. Before, it saved the floor block itself, and placement failed with `Lectern position is blocked!`.
  - Trade offers from the previous attempt are cleared before each new one, so the mod never judges a villager by stale trades.
  - It stops before your axe breaks, and breaks the lectern by hand if you have no axe.

## Requirements

| Mod | Version |
|-|-|
| Minecraft | 26.3 |
| [Fabric Loader](https://fabricmc.net/use/) | 0.19.5 or newer |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 0.162.0+26.3 |
| [YetAnotherConfigLib (YACL)](https://modrinth.com/mod/yacl) | 3.9.7+26.3-fabric |
| [Mod Menu](https://modrinth.com/mod/modmenu) | 21.0.0 |

It's a client-only mod. The server doesn't need it installed.

## Install

1. Install the Fabric Loader for Minecraft 26.3.
2. Put Fabric API, YACL and Mod Menu in your `mods` folder.
3. Download the jar from the [Releases page](../../releases), or build it yourself (see below), and drop it into `mods`.

## Quick start

1. Hold some lecterns (hotbar, offhand or inventory all work) and an axe.
2. Stand next to an unemployed villager.
3. Look at the floor where the lectern should go and press **L** (or run `/reroll pos`).
4. Add what you want: `/reroll add mending 1 20` means Mending, any level, 20 emeralds or less.
5. Press **J** (or `/reroll start`). Press **K** or `/reroll stop` to stop.

When it finds a match, it stops, plays a sound, and removes that enchantment from your list. Move to the next villager and start again to hunt for whatever is left.

## Commands

Every command is client-side. Tab completion works for enchantment names and HUD positions.

| Command | What it does |
|-|-|
| `/reroll start` / `stop` | Start or stop the reroller |
| `/reroll pos` | Set the lectern spot from the block you're looking at |
| `/reroll pos <x> <y> <z>` / `pos clear` | Set the spot by coordinates, or clear it |
| `/reroll villager` / `villager clear` | Lock onto the villager you're looking at, or go back to "nearest villager" |
| `/reroll add <enchant> [minLevel] [maxCost]` | Add or update a target |
| `/reroll remove <enchant>` / `list` / `clear` | Manage the target list |
| `/reroll hud on\|off` | Show or hide the HUD |
| `/reroll hud scale <0.5-2.0>` | Resize the HUD |
| `/reroll hud pos <corner>` | `top_left`, `middle_left`, `bottom_left`, `top_right`, `middle_right`, `bottom_right` |
| `/reroll hud compact true\|false` | One-line HUD |
| `/reroll delay close\|retry <ticks>` | Timing between steps |
| `/reroll radius <1-16>` | How far to look for the nearest villager |
| `/reroll pickup true\|false` | Walk to a dropped lectern and back |
| `/reroll highlight true\|false` | Outline the selected villager |
| `/reroll settings` | Show current values next to the defaults |
| `/reroll config` / `config more` | Open the config screens |
| `/reroll debug` | Show what the mod sees in your hotbar, offhand and inventory |

## Default settings

| Setting | Default | Range |
|-|-|-|
| Start / stop key | J | |
| Emergency stop key | K | |
| Set lectern position key | L | |
| Select villager key | unbound | |
| Open config key | unbound | |
| Minimum enchantment level | 1 | 1-5 |
| Maximum emerald cost | 64 | 1-64 |
| Delay after closing the trade screen | 20 ticks (1 s) | 5-100 |
| Delay before placing the next lectern | 40 ticks (2 s) | 10-200 |
| Villager search radius | 6 blocks | 1-16 |
| Walk to pick up dropped lectern | on | |
| Outline selected villager | on | |
| HUD | on, scale 1.0, middle left, full panel | scale 0.5-2.0 |

Settings live in `config/villager-reroll.json`.

## Things to know

- The auto-pickup walk goes in a straight line. It doesn't jump, climb or path around blocks. If something is in the way, it gives up after 10 seconds and tells you why.
- The mod turns your character to face the dropped lectern. Some anti-cheat plugins don't like sudden rotation. If your server complains, turn it off with `/reroll pickup false`.
- Check your server's rules before using any automation mod.

## Building from source

You need JDK 25.

```bash
./gradlew build
```

The jar ends up in `build/libs/`.

## Credits

The original mod, the reroll state machine and the config screens are by [Joplayz](https://github.com/Joplayz) ([villager-reroll-26.1.2](https://github.com/Joplayz/villager-reroll-26.1.2)). This fork adds the 26.3 port, the survival fixes and the features listed above. If you're on 26.1.2 or 26.2, use the original.

## Support

If this mod saved you a few hundred lecterns, you can [buy me a coffee](https://buymeacoffee.com/tongtongkok).

<a href="https://buymeacoffee.com/tongtongkok"><img width="200" height="200" alt="Buy Me a Coffee QR code" src="https://github.com/user-attachments/assets/4034910a-9019-498d-a3e7-85d96b789e5e" /></a>

## License

MIT, same as the original. See [LICENSE](LICENSE). The original copyright notice is kept.
