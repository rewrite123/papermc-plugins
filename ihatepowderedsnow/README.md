# ihatepowderedsnow

Make snowy-biome travel less annoying: **powder snow in newly generated chunks
becomes solid snow blocks**, preserving the shape of the terrain without the
snow traps.

For **Paper 26.3 / Java 25**, with no runtime dependencies.

## How it works

- Runs as a world-generation block populator after vanilla terrain/features.
- Replaces `POWDER_SNOW` with `SNOW_BLOCK`, not a thin snow layer.
- Checks the complete height of the chunk, including buried powder-snow pockets.
- Applies to all worlds, including worlds created after the plugin starts.
- **Existing chunks are not scanned or converted**, even when loaded again.
- Player-placed powder snow, powder-snow buckets, and powder snow collected in
  cauldrons are unchanged. This is not a general ban on powder snow.
- No commands, permissions, or configuration are needed.

The populator uses Paper's generation-safe `LimitedRegion` API and touches only
the chunk being populated, without loading adjacent chunks. It does not replace
your world generator or alter other block types. Other plugins that generate
powder snow **after** this populator runs may still introduce it.

There is a bounded scan of 256 columns across the world's build height per new
chunk (98,304 block checks for a standard -64 to 320 world). There is no recurring
scan or chunk-load processing. Already-generated/pre-generated terrain stays as
it is; explore beyond its boundaries to see the change.

## Install

1. Use Paper 26.3 with Java 25.
2. Place `ihatepowderedsnow-0.1.0.jar` in the server's `plugins/` directory.
3. Restart the server before exploring new terrain.

The plugin loads at startup so it can register before initial world generation.
Converted snow remains ordinary snow if you uninstall the plugin; conversion
is not reversible automatically. Back up your world before adding plugins
that modify world generation.

## Build and test

```sh
./gradlew build
```

The JAR is written to `build/libs/ihatepowderedsnow-0.1.0.jar`. Gradle provisions
the Java 25 build toolchain automatically. Tests cover exact block replacement,
chunk/height boundaries, concurrent generation, and world registration; they do
not replace a real-server terrain-generation smoke test.

## Source and support

- [GitHub repository](https://github.com/rewrite123/papermc-plugins)
- [Report an issue](https://github.com/rewrite123/papermc-plugins/issues)

When reporting a problem, include the plugin version, Paper version, world
generator/other generation plugins, and whether the chunk was newly generated.

## Publish

Uses [Hangar's official Gradle publishing plugin](https://github.com/HangarMC/hangar-publish-plugin).
With `HANGAR_API_TOKEN` set to a key with `create_version` and `edit_page`:

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar \
  -PhangarChangelog="Replace generated powder snow with solid snow blocks."
```

Use a unique version in `gradle.properties` for each release. The Hangar main
page is synchronized from this README. A manual GitHub Actions publishing
workflow uses the `HANGAR_API_TOKEN` repository secret.
