# Reduce Enderman Block Damage

A standalone plugin for **Paper 26.3** and **Java 25**.

Endermen still pick up blocks normally. After each successful pickup, the plugin
chooses a random delay from **10 through 45 seconds**. Once the delay expires,
the Enderman attempts to walk back and replace its block at the original position.

## Behavior

- Only the original position is used; tracked Endermen cannot place stolen
  blocks elsewhere while waiting or walking back.
- The original space must be empty air. Existing blocks and liquids are never
  overwritten. If occupied, the Enderman waits and retries.
- The Enderman must be nearby and have line of sight before restoring the
  original block state. Successful placement consumes its carried block.
- Killing the Enderman cancels the return. Death drops are unchanged; the
  plugin does not recreate a block after death.
- Combat, disabled AI, unreachable terrain, unloaded chunks, and traveling to
  another dimension can delay or prevent a return. There is no forced teleport
  or chunk loading. Returns are attempts, not guaranteed restoration.
- The plugin respects `mobGriefing` and fires a cancellable
  `EntityChangeBlockEvent` before returning a block so protection plugins can
  veto placement. Cancelled returns are retried.
- The origin and deadline are saved on the Enderman's persistent data container
  and restored when the entity loads, including after a normal server restart.
  Endermen already carrying blocks before installation are unchanged because
  their original pickup location is unknown.

The timer uses wall-clock time and is checked once every 20 server ticks.
Unloaded time counts toward the delay, but a return only happens while the entity
and original chunk are loaded. Server lag or the conditions above can make the
actual return take longer than 45 seconds.

There are no commands, permissions, or configuration files.

## Build and install

```sh
./gradlew build
```

Copy `build/libs/reduceendermanblockdmg-0.1.0.jar` into the server's `plugins/`
directory and restart Paper. Back up your world before installing new plugins.

Unit tests cover return deadlines, movement, empty-only placement, protection
cancellation, death, carried-block changes, and persistent state recovery.

## Publish to Hangar

The publication targets
[rewrite123/reduceendermanworlddmg](https://hangar.papermc.io/rewrite123/reduceendermanworlddmg),
the `Release` channel, and Paper `26.3`. The Hangar project name differs from
the local plugin folder and JAR name intentionally.

Create the Hangar project first and supply a token with `create_version`
permission through `HANGAR_API_TOKEN`. Never commit credentials.

```sh
./gradlew build publishPluginPublicationToHangar --console=plain
```

For later releases, set an unused version in `gradle.properties` and supply
release notes with `-PhangarChangelog="..."`. Normal builds do not publish.
