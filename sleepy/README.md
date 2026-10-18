# sleepy

Skip the night when **at least 50%** of eligible players in a world are sleeping.
For **Paper 26.3 / Java 25**, with no runtime dependencies, commands, or permissions.

## Behavior

Sets Minecraft's built-in `playersSleepingPercentage` gamerule to **50** in every
loaded world when enabled, and in worlds loaded or created afterward.
Minecraft handles the sleep counter, normal bed delay, morning transition,
weather, and waking players; the plugin does not force time changes.

The threshold is per world, not across the whole server. Required sleepers are
rounded up: 1 of 2 players, 2 of 3, 2 of 4, or 3 of 5. Spectators and players
excluded by the server's normal sleep rules do not count. Normal bed restrictions
remain, including unsafe beds in the Nether and End. Other time or sleep gamerules
are not changed.

The gamerule is saved with each world and remains at 50 after removing the plugin.
To restore vanilla behavior, remove the plugin and set the sleeping percentage
back to 100 in each affected world. While enabled, the plugin reapplies 50 at
startup and world load; it does not continuously override administrator changes.

## Build and install

```sh
./gradlew build
```

Gradle runs on Java 17+ and provisions Java 25 for compilation if needed.
Windows users can use `gradlew.bat`. Copy `build/libs/sleepy-0.1.0.jar` into
your Paper server's `plugins/` directory and restart with Java 25.

Run `./gradlew test` to test the exact gamerule value, startup/world-load handling,
and error reporting.
Tests use MockBukkit's available 26.2 registry harness, while production compiles
against Paper 26.3. The night transition itself is provided by Minecraft and
should be smoke-tested on your Paper 26.3 server.

## Publish to Hangar

Uses [HangarMC's official Gradle publishing plugin](https://github.com/HangarMC/hangar-publish-plugin).
Create the `sleepy` project on Hangar and supply a key with `create_version`
permission through `HANGAR_API_TOKEN`, then run:

```sh
./gradlew build publishPluginPublicationToHangar \
  -PhangarChangelog="Initial release: skip nights with 50% sleeping."
```

Set a unique `version` in `gradle.properties` for each release. The default channel
is `Release`, and compatibility is Paper `26.3`. The project slug can be overridden
with `-PhangarProject=your-slug`. Never commit API keys. A normal build does not
upload anything to Hangar.
