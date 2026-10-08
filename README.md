# DrunkShyt

A Paper plugin that adds one shot to your counter whenever you take damage.
Record what you have completed with `/shots drank`, and keep the remaining count
in your name tag, Tab list, or action bar.

Made for [pharolen](https://www.twitch.tv/pharolen) by **Aspect**.

Requires **Java 25** and **Paper 26.1.1**. Tested against Paper build 29.
The Minecraft Five font pack is optional; no client mod is required.

## Install

1. Build the plugin with the commands below, or download the JAR from this repository's Releases page when available.
2. Stop the server and put the JAR in `plugins/`. Keep only one DrunkShyt JAR active.
3. Start the server and run `/shots` in game. Player counters are created on join.

PvP starts **on**. Shot effects start **off**, with no rules configured.
The plugin observes damage; it does not cancel hits or make players invulnerable.
To use the custom font, follow the [resource-pack guide](RESOURCE-PACK.md).

## Behavior

- Every uncancelled event that causes positive final damage adds exactly **1** to that player's total and remaining count. It is per event, not per heart. Fully blocked/absorbed hits, creative, and spectator do not count. Repeated fire or poison ticks count individually unless excluded in the configuration.
- Players are set up automatically on join. The name tag, Tab list, and action bar show `[10] shots | [03] left`. Bracketed numbers have a minimum of two digits and never truncate larger values.
- Quiet, player-local sounds and a brief action-bar update are on by default. `/shots feedback off` disables this extra feedback. These are Minecraft sounds; controller vibration is not supported.
- Plugin controls and counters explicitly use the configured font with italics off. Usernames always use `minecraft:default`, including the Tab list, status messages, admin responses, and displayed reset commands. The optional [Minecraft Five pack](RESOURCE-PACK.md) uses `font: drunkshyt:five` with crisp five-pixel bitmap letters. The default configuration remains usable without a pack. The client controls GUI scale.
- Total means all assigned shots in the current round. Remaining means total minus completed. Recording completion never reduces the total. Remaining can be corrected from zero through the assigned total.
- Damage, death, and manual counting modes are supported. Death mode counts once on death and does not also count the fatal damage event. Pausing stops automatic counting while leaving manual controls available.
- Death, reconnects, and normal server restarts keep the counts and individual feedback preference. Reset is explicit and confirmed. New players start at zero.

## Players

| Command | Result |
| --- | --- |
| `/shots` | Open the inventory control menu |
| `/shots status [player]` | Show a saved counter |
| `/shots drank [amount]` | Record completed shots; defaults to 1 |
| `/shots left <amount>` | Set remaining without changing assigned total |
| `/shots feedback [on\|off]` | Set or toggle quiet feedback |
| `/shots reset` | Ask to reset your counter |
| `/shots reset confirm` | Reset your total and remaining to zero |
| `/shots help` | Show commands |

Aliases: `/shot`, `/drinks`; `drink` and `done` also record completion. The menu supports one completed per click, five per shift-click, an editable command suggestion for remaining, feedback toggle, and a separate reset confirmation screen. Menu items cannot be taken or moved into the player's inventory.

## Admins and server console

Operators automatically have `drunkshyt.admin`. Other players get `drunkshyt.use` and `drunkshyt.reset` by default. Remove the reset permission through your permission system if only admins should reset rounds.

| Command | Result |
| --- | --- |
| `/shots add <player> <amount>` | Add to a known player's total and remaining |
| `/shots set <player> <total> [left]` | Set total and remaining; remaining defaults to total |
| `/shots reset <player\|all>` | Ask to reset one or every saved counter, including offline players |
| `/shots reset <player\|all> confirm` | Confirm that reset |
| `/shots mode <damage\|death\|manual>` | Change and save counting mode |
| `/shots pause` / `/shots resume` | Pause or resume automatic counting and save the choice |
| `/shots pvp <on\|off\|status>` | Enable, disable, or inspect combat between players in every world |
| `/shots effects add <id> <drank\|left> <amount> <effect> [seconds] [level]` | Save a potion-effect threshold; defaults to 30 seconds and level I |
| `/shots effects list` / `/shots effects types` | Show configured rules or available Minecraft potion effects |
| `/shots effects remove <id>` | Remove one rule |
| `/shots effects clear confirm` | Remove all rules |
| `/shots effects <on\|off>` | Enable or disable future effect triggers |
| `/shots effects help` | Show effect commands in game |

Omit the leading slash in the panel console. Players must have joined once before admins can adjust them by name. Counts are bounded to 0 through 999999. Invalid input does not change state.

PvP and effects are **admin commands only**, with no menu controls. PvP uses Minecraft's native world switch, including for worlds loaded later. Fall, mob, fire, and other environmental damage continue normally. New installations default to PvP **on** and shot effects **off**. Admins can change either setting; their saved choice survives restarts.

Effects start disabled with no configured rules. Add the desired rules and explicitly enable effects when ready. For example:

```text
/shots pvp off
/shots effects add owed left 5 slowness 30 1
/shots effects add completed drank 10 nausea 15 1
/shots effects on
```

The first effect applies Slowness I for 30 seconds when a player's remaining count reaches or passes 5. The second applies nausea for 15 seconds when their completed count reaches or passes 10. `drank` (alias `have`) means completed; `left` (alias `havent`) means still owed. Thresholds apply separately to each player's counts, including changes made through the counter commands or menu. A jump past a threshold fires once. A count must drop below the threshold before that rule can fire again; resetting a round also permits future crossings.

Only living, online Survival/Adventure players with counter permission receive effects. Rejoining, restarting, adding a rule, or enabling effects does not replay already-reached thresholds. Effects are applied on the next tick after counting so they do not interfere with the current damage event. Existing vanilla effect merging applies; disabling/removing rules leaves active effects to expire normally. Instant effects apply once and ignore duration. Harmful effects can cause normal damage events, which still count unless their causes are excluded.

Rule IDs use 1–32 lowercase letters, digits, underscores, or hyphens. Up to 64 rules are supported, with thresholds 1–999999, duration 1–3600 seconds, and levels 1–255. All rules and their enabled state are saved.

## Configuration and storage

`plugins/DrunkShyt/config.yml` is created on first start. `ignored-damage-causes` can exclude causes such as `FIRE_TICK`, `POISON`, `LAVA`, or `FALL`. `action-bar`, `name-tags`, and `tab-list` independently control displays. Restart after editing the file; admin commands take effect immediately. The defaults are `pvp-enabled: true` and `effects.enabled: false`; effect rules live under `effects`.

Player records are stored locally in `plugins/DrunkShyt/players/`. Saves are coalesced on a single background worker every 250 ms and atomically replace each record. Normal shutdown flushes pending records. A machine crash can lose changes still queued for that short interval. Failed writes remain queued and emit a console error. Invalid saved records cause startup to stop without overwriting them.

Native name tags use the main scoreboard. Existing membership in another plugin's team is respected instead of overwritten; another plugin that replaces a player's scoreboard may hide this suffix. Player display/chat names are not modified.

## Build and install

```sh
./gradlew clean test build
```

Run this with JDK 25 installed and `JAVA_HOME` set. On Windows, use `gradlew.bat clean test build`.

The artifact is `build/libs/DrunkShyt-1.1.2.jar`. Upload it to `plugins/`, disable the previous version, and restart the server. The existing Minecraft Five 1.1 resource pack works unchanged with these commands. Do not use Bukkit `/reload`. To remove the plugin, stop the server, move the JAR out of `plugins/`, and start it; keep the data directory if you want saved counts for later.

The build pins the [Paper 26.1.1 API](https://jd.papermc.io/paper/26.1.1/) and follows [Paper's project setup](https://docs.papermc.io/paper/dev/project-setup/).

## Verification

52 JUnit tests cover arithmetic, invalid inputs/overflow, event eligibility, paused/manual/death behavior, independent player persistence, saved feedback preference, reset persistence, interrupted/corrupt files, mixed username/plugin fonts, PvP world defaults/overrides/cleanup, effect threshold crossings/rearming, disabled and removed rules, and effect YAML persistence. See [testing](docs/TESTING.md) for the optional Minecraft font test and the limits of the automated checks.

## Contributing

Bug reports should include the plugin version, Paper version, what you did, and
what you expected. See [CONTRIBUTING.md](CONTRIBUTING.md) for the build and checks.

## License

Project code is covered by [LICENSE](LICENSE). The font and Gradle wrapper have
separate notices listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
This project is not affiliated with or endorsed by Mojang or Microsoft.
