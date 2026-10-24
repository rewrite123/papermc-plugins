# BetterLanterns

**Better crafting, brighter lanterns, and blue soul candles.** For **Paper 26.3 / Java 25**. No resource pack or runtime dependencies required.

![Features at a glance](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/betterlanterns/docs/images/features.png)

> **In game:** type `/help betterlanterns` for the full guide with clickable links. There are no commands, permissions, or settings.

## Contents

- [Quick start](#quick-start)
- [Recipes](#recipes)
- [Lighting behavior](#lighting-behavior)
- [Natural hostile-spawn blocking](#natural-hostile-spawn-blocking)
- [Commands, permissions and configuration](#commands-permissions-and-configuration)
- [Install](#install)
- [Build from source and publish](#build-from-source-and-publish)
- [Links and support](#links-and-support)

## Quick start

![Crafting flow](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/betterlanterns/docs/images/crafting-flow.png)

1. Make a lantern using nuggets, a glass pane and an ordinary candle.
2. Make four soul candles from four ordinary candles and soul sand.
3. Use a soul candle in place of the ordinary candle to make a soul lantern.
4. Place either lantern for brighter nearby light and protection against natural hostile spawns in direct sight. Place and ignite soul candles separately for a blue flame and a small halo.

## Recipes

Both vanilla lantern recipes are replaced. The custom recipes are automatically discovered by online and joining players.

| Recipe | Shape | Result |
| --- | --- | --- |
| Lantern | `GCG` / `IPI` / `III` | 1 lantern (`G` gold nugget, `C` ordinary candle, `I` iron nugget, `P` glass pane) |
| Soul candle | ` C ` / `CCC` / ` S ` | 4 soul candles (`C` ordinary candle, `S` soul sand) |
| Soul lantern | `GCG` / `IPI` / `III` | 1 soul lantern; `C` must be a soul candle |

![Lantern recipe](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/betterlanterns/docs/images/recipe-lantern.png)

![Soul candle recipe](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/betterlanterns/docs/images/recipe-soul-candle.png)

![Soul lantern recipe](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/betterlanterns/docs/images/recipe-soul-lantern.png)

Soul candles are custom-tagged items using the vanilla candle item/model. They stack up to four on a block but cannot mix with ordinary candles or be placed on cakes. Breaking them returns soul candles. Ignite with flint and steel or a fire charge; right-click with an empty hand to extinguish without sneaking. Water extinguishes them. Creative mode does not consume ignition items. Their flame is vanilla-disabled and replaced by blue soul-fire particles approximating the wicks; client particle settings affect visibility.

## Lighting behavior

- Regular lanterns still emit **15** at their own block.
- Nearby air within **three orthogonal block steps** gets invisible vanilla `LIGHT` blocks at **level 15**. Air paths bend around corners but do not pass through occupied blocks; this is block-grid distance, not a three-block Euclidean sphere.
- Soul lanterns get the same surrounding halo and keep their blue vanilla appearance. Their own block remains a vanilla level-10 emitter; adjacent lights bring it to 14. Paper cannot set its native emission to 15.
- Soul candles produce adjacent light at **3 levels per candle, minus one** on the neighboring light block; one to four candles therefore contribute levels 2, 5, 8 or 11. Their particles are decorative and do not color world light.
- Minecraft block light is not colored: both lantern types illuminate the world with ordinary vanilla block lighting.

Only air is replaced. Water, plants, glass and player-created light blocks are not overwritten. Occupying a managed light position with another block is respected; light sources recalculate after normal block/physics changes. Overlapping lanterns share managed lights, so removing one does not remove the other's illumination.

Managed lights and soul-candle identity/ignition are stored in **chunk persistent data**. Chunks restore saved managed lights before rebuilding them on load, including after restart. Unloading does not keep chunks loaded. Disabling removes managed lights in loaded chunks; unloaded chunks are cleaned when next loaded with the plugin installed. **Before uninstalling permanently, load affected chunks while the plugin is active and disable it cleanly.** Otherwise invisible lights in unloaded terrain remain until the plugin is installed again and those chunks load. Keep world backups.

Existing loaded chunks are scanned incrementally (one chunk per tick); recalculations are bounded to eight sources per tick. Initial coverage is not instantaneous. Ordinary placement/removal, support physics, pistons, fluids and explosions trigger updates; editing tools that bypass Bukkit block events may require unloading/reloading chunks to discover new lanterns.

## Natural hostile-spawn blocking

Both lantern types prevent **natural hostile (`Enemy`) spawns within 24 blocks** in a spherical radius.

| Rule | Behavior |
| --- | --- |
| Line of sight | Required from lantern center to half a block above the entity's feet; it does not bend around obstacles |
| Blocking shapes | Non-passable collision shapes block sight, including glass and closed doors; water/passable plants do not |
| Spawn reason | Only `NATURAL` events are blocked; animals, breeding, spawners, raids, reinforcements, patrols, commands and spawn eggs are unchanged |
| Existing mobs | Not removed; soul candles do not provide wards |
| Chunks | Only loaded, discovered lanterns participate; no chunks are force-loaded |

This is a server-side spawn check, not an invisible level-24 Minecraft light source. It does not change mob AI or spawning rules outside the radius.

## Commands, permissions and configuration

| Entry | Availability |
| --- | --- |
| `/help betterlanterns` | In-game guide and clickable links |
| Other commands | None |
| Permissions | None |
| `config.yml` | Not used |

## Install

1. Download `betterlanterns-0.2.0.jar` from [Hangar](https://hangar.papermc.io/rewrite123/betterlanterns).
2. Put it in your server's `plugins/` directory and restart on **Paper 26.3 / Java 25**.
3. Recipes are registered automatically; no configuration is required.

<details id="build-from-source-and-publish">
<summary>Build from source and publish</summary>

```sh
./gradlew build
```

The jar is written to `build/libs/`. Gradle runs on Java 17+ and downloads the Java 25 toolchain if needed. Tests use MockBukkit; they do not replace a real-client lighting and particle smoke test.

Regenerate original recipe and feature diagrams from the repository root with `python3 tools/docs/betterlanterns.py`.

Publishing uses [Hangar's Gradle plugin](https://github.com/HangarMC/hangar-publish-plugin). Set a unique `version` in `gradle.properties` and supply `HANGAR_API_TOKEN` with `create_version` and `edit_page` permissions. Never commit the token.

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar \
  -PhangarChangelog="Improved lantern crafting, lighting, and soul candles."
```

The Hangar main page is synchronized from this README; diagrams use absolute raw GitHub URLs.

</details>

## Links and support

- **Source and full guide:** [GitHub plugin folder](https://github.com/rewrite123/papermc-plugins/tree/main/betterlanterns)
- **Download:** [Hangar](https://hangar.papermc.io/rewrite123/betterlanterns)
- **Report a bug or request a feature:** [GitHub issues](https://github.com/rewrite123/papermc-plugins/issues)

Include your Paper version and any lighting, protection or world-edit plugins when reporting a problem.
