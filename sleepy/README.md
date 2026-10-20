# sleepy

Skip the night when **50% of players** or a **minimum number of players** in a
world are sleeping, whichever happens first.
For **Paper 26.3 / Java 25**, with no runtime dependencies.

**Latest release:** [sleepy 0.2.0 on Hangar](https://hangar.papermc.io/rewrite123/sleepy/versions/0.2.0).

**What's new in 0.2.0:** admins can change the sleeping percentage and set a
minimum number of sleeping players in game. Meeting **either** one skips the night.
On a busy server you can make 10 sleepers enough even when 50% would be 25 people.
On a small server the percentage still works when fewer than 10 players are online.

## Behavior

Night is skipped in a world when either condition is met:

- **Percentage** (default **50%**): this share of the world's non-spectator
  players are sleeping, rounded up. At 50% that is 1 of 2, 2 of 3, 2 of 4, or 3 of 5.
- **Minimum players** (default **off**): this many players are sleeping,
  regardless of how many are online.

Examples with 50% and a minimum of 10:

| Players in world | 50% needs | Minimum needs | Sleepers required |
|---|---|---|---|
| 4 | 2 | 10 | **2** |
| 20 | 10 | 10 | **10** |
| 30 | 15 | 10 | **10** |
| 60 | 30 | 10 | **10** |

Sleepy works through Minecraft's built-in `playersSleepingPercentage` gamerule.
It keeps each world's gamerule at the value that requires exactly the number of
sleepers in the table. It recalculates every second, when a player gets into
bed, and when a world loads. Minecraft still handles the bed delay, the "X/Y
players sleeping" message (which shows the real requirement), the morning
transition, weather, and waking players.

The requirement is per world, not across the whole server. Spectators do not
count. Normal bed restrictions remain, including unsafe beds in the Nether and
End. Other time or sleep gamerules are not changed. In the rare case of more than
100 players in one world, the gamerule's whole-percent steps may make the
minimum one or two players easier than configured, never harder.

## Commands

| Command | Who | Action |
|---|---|---|
| `/sleepy` or `/sleepy status` | everyone | Show the current rules and how many sleepers your world needs |
| `/sleepy percent <0-100>` | admins | Set the sleeping percentage |
| `/sleepy minplayers <number\|off>` | admins | Set the minimum sleeping players (`off` or `0` disables it) |

Admin commands need the `sleepy.admin` permission (operators by default). Changes
apply immediately and are saved to `plugins/sleepy/config.yml`:

```yaml
sleeping-percentage: 50
min-sleeping-players: 0
```

You can also edit the file and restart. Out-of-range values fall back to the
defaults with a warning in the console.

## Upgrading from 0.1.0

Replace the JAR and restart. A default `config.yml` (50%, minimum off) is
created, so behavior is unchanged until an admin sets a minimum.

## Removing the plugin

The gamerule is saved with each world and keeps its last value after you remove
the plugin. To restore vanilla behavior, remove the plugin and set
`playersSleepingPercentage` back to 100 in each world. While the plugin is
enabled, it manages that gamerule, so use `/sleepy percent` instead of `/gamerule`.

## Build and install

```sh
./gradlew build
```

Gradle runs on Java 17+ and provisions Java 25 for compilation if needed.
Windows users can use `gradlew.bat`. Copy `build/libs/sleepy-0.2.0.jar` into
your Paper server's `plugins/` directory and restart with Java 25.

Run `./gradlew test` to test vanilla-matching rounding, that the gamerule
requires exactly the intended sleepers for every percentage, minimum, and player
count up to 100, plus commands, permissions, validation, saved settings, and
player-count changes.
Tests use MockBukkit's available 26.2 registry harness, while production compiles
against Paper 26.3. The night transition itself is provided by Minecraft and
should be smoke-tested on your Paper 26.3 server.

## Publish to Hangar

Releases are published to [rewrite123/sleepy](https://hangar.papermc.io/rewrite123/sleepy)
using [HangarMC's official Gradle publishing plugin](https://github.com/HangarMC/hangar-publish-plugin).
Supply a key with `create_version` permission through `HANGAR_API_TOKEN`, then run:

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar \
  -PhangarChangelog="Describe the changes in this release."
```

Set a unique `version` in `gradle.properties` for each release. The default channel
is `Release`, and compatibility is Paper `26.3`. The project slug can be overridden
with `-PhangarProject=your-slug`. This README is synced as the Hangar project
page. Never commit API keys. A normal build does not upload anything to Hangar.
