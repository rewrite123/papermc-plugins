# IHatePowderedSnow

**Make snowy-biome travel less annoying: powder snow generated in new chunks becomes solid snow blocks, preserving the terrain shape without the snow traps.** For **Paper 26.3 / Java 25**, with no runtime dependencies.

![Features at a glance](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/ihatepowderedsnow/docs/images/features.png)

> **In game:** `/help ihatepowderedsnow` explains what changes and links to the source and issue tracker. There are no commands or permissions.

## Contents

- [Quick start](#quick-start)
- [What changes during generation](#what-changes-during-generation)
- [Scope and limits](#scope-and-limits)
- [Commands, permissions and configuration](#commands-permissions-and-configuration)
- [Install](#install)
- [Build from source and publish](#build-from-source-and-publish)
- [Links and support](#links-and-support)

## Quick start

![Generation flow](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/ihatepowderedsnow/docs/images/generation-flow.png)

1. Install the plugin on Paper 26.3 with Java 25.
2. Restart the server before exploring new terrain.
3. Newly generated chunks have `POWDER_SNOW` replaced by `SNOW_BLOCK`. No player action or configuration is needed.

## What changes during generation

The plugin registers a world-generation block populator that runs after vanilla terrain and features. For the chunk being populated, it checks all 256 columns from the world's minimum to maximum build height. Only powder snow is changed, including buried pockets; other block types and the shape of the terrain remain in place.

For a standard world from Y=-64 to Y=319, that is **256 × 384 = 98,304 block checks per generated chunk**. The scan uses Paper's generation-safe `LimitedRegion` API and touches only the chunk being populated; it does not load adjacent chunks.

## Scope and limits

| Situation | Result |
| --- | --- |
| Newly generated chunks in existing or future worlds | Powder snow becomes solid snow blocks |
| Already-generated chunks, including on later load | Not scanned or converted |
| Player-placed powder snow, buckets or powder snow in cauldrons | Unchanged |
| Snow layers, snow blocks and all other materials | Unchanged |
| Other plugins adding powder snow after this populator | May still add powder snow |
| Plugin disabled or removed | Converted blocks remain ordinary snow blocks; no automatic reversal |

The populator does not replace your world generator or run a recurring scan when chunks load. Explore beyond existing/pre-generated terrain to see the change. Back up your world before adding plugins that modify generation.

## Commands, permissions and configuration

| Entry | Availability |
| --- | --- |
| `/help ihatepowderedsnow` | In-game guide and clickable links |
| Other commands | None |
| Permissions | None |
| `config.yml` | Not used |

## Install

1. Download `ihatepowderedsnow-0.2.0.jar` from [Hangar](https://hangar.papermc.io/rewrite123/ihatepowderedsnow).
2. Put it in the server's `plugins/` directory and restart on **Paper 26.3 / Java 25**.
3. Explore chunks that have not already been generated. Existing terrain is never converted automatically.

The plugin loads at startup so it can register before initial world generation. Converted snow remains ordinary snow if you uninstall; conversion is not reversible automatically.

<details id="build-from-source-and-publish">
<summary>Build from source and publish</summary>

```sh
./gradlew build
```

The jar is written to `build/libs/`. Gradle runs on Java 17+ and downloads the Java 25 toolchain if needed. Tests cover exact block replacement, chunk/height boundaries, concurrent generation and world registration; they do not replace a real-server terrain-generation smoke test.

Regenerate the original feature and generation diagrams from the repository root with `python3 tools/docs/ihatepowderedsnow.py`.

Publishing uses [Hangar's Gradle plugin](https://github.com/HangarMC/hangar-publish-plugin). Set a unique version in `gradle.properties` and supply `HANGAR_API_TOKEN` with `create_version` and `edit_page` permissions. Never commit the token.

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar \
  -PhangarChangelog="Replace generated powder snow with solid snow blocks."
```

The Hangar main page is synchronized from this README. The diagrams use absolute raw GitHub URLs for display on GitHub and Hangar.

</details>

## Links and support

- **Source and full guide:** [GitHub plugin folder](https://github.com/rewrite123/papermc-plugins/tree/main/ihatepowderedsnow)
- **Download:** [Hangar](https://hangar.papermc.io/rewrite123/ihatepowderedsnow)
- **Report a bug or request a feature:** [GitHub issues](https://github.com/rewrite123/papermc-plugins/issues)

Include the plugin version, Paper version, world generator or other generation plugins, and whether the chunk was newly generated.
