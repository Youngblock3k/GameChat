# GyxerChat 2.0.0

The all-in-one chat plugin by **RyxerEverlynx**, updated and upgraded.

## Which jar do I use?

| Your server | Jar |
|---|---|
| Paper 1.21.11 | `GyxerChat-2.0.0-mc1.21.11.jar` |
| Paper 26.1.x | `GyxerChat-2.0.0-mc26.1.jar` |
| Paper 26.2 | `GyxerChat-2.0.0-mc26.2.jar` |
| Paper 26.3 | `GyxerChat-2.0.0-mc26.3.jar` |

Use **one** jar only. The 26.x jars need Java 25; the 1.21.11 jar needs Java 21 or newer.

## How to update from 1.0

1. Stop the server.
2. Delete the old `GyxerChat-1.0-PREMIUM.jar` from `plugins/`.
3. Put the new jar in `plugins/`.
4. Start the server.

All your data stays: config, mutes, ignores, tags, nicknames, chat colors and /settings choices.
New settings are added to your `config.yml` by themselves. Your own values and comments stay.

## What is new

**Fixed bugs**
- `/tpahere` did the same as `/tpa`. Now it really pulls the other player to you.
- `/ban`, `/tempban` and `/unban` said "message missing". They now work and show a proper ban screen.
- Staff chat toggled with `/sc` used the wrong format. Fixed.
- `/r` only worked once. Now you can reply back and forth.
- The TPA on/off button in the admin menu did nothing. Now it works.
- Teleporting into another world could cause an error. Fixed (and an option to block it).
- Chat games were checked even for muted players' and staff chat messages. Fixed.
- `/chatreload` did not restart the broadcaster, chat games or Discord. Now it reloads everything.
- The startup text always said "Hooked into PlaceholderAPI", even without it. Fixed.
- `%luckperms_prefix%` showed up as text when PlaceholderAPI was missing. Now it is left out.
- Tag IDs with capital letters in `tags.yml` could not be found. Fixed.
- Deleted tags stayed on players forever. Now they disappear.
- Ignores were only saved when the server stopped, so a crash lost them. Now they are saved at once.
- Menus checked item names and window titles, so any other plugin's "...Settings" chest got broken. Menus can't be hacked any more (shift-click, number keys and dragging are blocked).

**Faster and safer**
- Chat is now thread-safe. Version 1 could cause random errors or lag spikes with many players.
- The server no longer freezes while Discord connects (version 1 froze it at startup and on reload).
- **Discord console:** in version 1, *anyone* who could write in the console channel could run any command, even `op`. Now only the user IDs or role IDs you list under `discord.console-commands` can. It is off by default.
- Messages from Minecraft can no longer ping `@everyone` on Discord.
- Players can't put MiniMessage tags (like click events) in chat.
- `/mute`, `/ban` and `/ignore` no longer freeze the server looking up names, and typos aren't muted by accident.
- Umlauts and accents (ä ö ü é ß ...) are allowed in chat now. Before, the unicode filter removed them.

**New things**
- MiniMessage colors in every message: `<gradient:#ff0000:#0000ff>`, `<rainbow>`, `<bold>` ... The old `&a` and `&#FF00AA` codes still work too.
- `/mutechat` (alias `/lockchat`): lock the global chat.
- `/tpacancel`: take back your teleport request.
- `/nick <name> <player>`: admins can set other players' nicknames. `/nick off` removes your own.
- `/ignore <player>` switches ignoring on or off.
- A **Start Chat Game** button in `/chatadmin`.
- Chat games end after 60 seconds if nobody answers (`answer-timeout-seconds`).
- The muted list shows how much time is left.
- Muted players are told how long the mute lasts. Players are told when they are unmuted.
- Duplicate-message block (`repeat-block-seconds`).
- Strikes are forgotten after 30 minutes (`strike-reset-minutes`).
- TPA: requests run out (`expire-seconds`) and taking damage cancels the teleport (`cancel-on-damage`).
- Discord: a staff channel that works both ways, a log channel for punishments, and online/offline messages.
- The death message can show the real cause: use `{reason}` in `messages.death`.
- Turn small caps on or off with `small-caps`.
- Pages in long menus (more than 28 players, tags or mutes).
- Better tab completion. For example, `/unban` suggests banned players and `/unmute` suggests muted ones.

## New permissions

| Permission | Default | What it does |
|---|---|---|
| `gyxerchat.nick.colors` | op | Use color codes in `/nick` (without it, colors are removed) |
| `gyxerchat.chat.colors` | everyone | Use `&` color codes in chat |
| `gyxerchat.clearchat.bypass` | nobody | Your chat is not cleared by `/clearchat` |

Everything else is the same as before: `gyxerchat.nick`, `everlynx.chat.admin`, `everlynx.chat.staff`, `everlynx.spy`, `gyxerchat.chatcolor.<color>`, `gyxerchat.chatcolor.gradient` and `tag.equip.<id>`.

## Discord setup (short)

1. Make a bot in the Discord Developer Portal and switch on **Message Content Intent**.
2. In `config.yml`, set `discord.enabled: true`, paste the `bot-token`, and fill in the channel IDs.
3. To run console commands from Discord, set `console-commands.enabled: true` and add your own Discord user ID to `allowed-user-ids`.
4. Run `/chatreload`.
