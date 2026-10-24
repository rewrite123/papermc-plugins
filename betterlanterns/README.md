# betterlanterns

Better crafting, brighter lanterns, and blue soul candles for
**Paper 26.3 / Java 25**. No resource pack or runtime dependencies required.

## Crafting

Both vanilla lantern crafting recipes are replaced. These three recipes appear
in the recipe book automatically for online/joining players.

### Lantern

```text
Gold nugget    Candle       Gold nugget
Iron nugget    Glass pane   Iron nugget
Iron nugget    Iron nugget  Iron nugget
```

Makes **1 lantern**. Uses an ordinary undyed candle, not a soul candle.

### Soul candle

```text
               Soul sand
Candle         Candle      Candle
               Soul sand
```

Makes **1 soul candle**, from three ordinary undyed candles and two soul sand.
Soul candles are tagged custom items backed by the vanilla candle item/model.
They stack up to four on a block, but cannot mix with ordinary candles or be
placed on cakes. Breaking them returns soul candles; they can also be used in
the soul-lantern recipe.

Ignite with flint and steel or a fire charge; right-click with an empty hand to
extinguish (without sneaking). Water extinguishes them. Creative mode does not
consume ignition items. Soul candles use **blue soul-fire particles**, with the
vanilla flame disabled, not a custom block texture. Particle positions are an
approximation of the candle wicks. Client particle settings affect visibility.
Adjacent invisible lights approximate ordinary candle brightness (3 light levels
per candle, less one level on the adjacent light block).

### Soul lantern

Use the lantern recipe above with **a soul candle** instead of an ordinary candle.
Makes **1 soul lantern**. Vanilla soul-torch lantern crafting is removed.
Existing lanterns and soul lanterns get the improved behavior too.

## Real brighter lighting

- Regular lanterns still emit **15** at their own block.
- Nearby air within **three orthogonal block steps** gets invisible vanilla
  `LIGHT` blocks at **level 15**, with vanilla light falloff beyond the halo.
- Air paths bend around corners, but do not go through occupied blocks. A wall
  can therefore block or shorten the halo. This uses Minecraft's block-grid
  distance, not a three-block Euclidean sphere.
- Soul lanterns keep their blue vanilla appearance and receive the **same
  surrounding halo**. Their own block remains a vanilla level-10 emitter and is
  lit to **14** by adjacent lights; Paper cannot set its native emission to 15.
- Minecraft block light is not colored: both lantern types illuminate the world
  with ordinary vanilla block lighting.

Only air is replaced. Water, plants, glass, and player-created light blocks are
not overwritten. Occupying a managed light position with another block is
respected; light sources are recalculated after normal block/physics changes.
Overlapping lanterns share managed lights, so removing one does not remove the
other's illumination.

Managed lights and soul-candle identity/ignition are stored in **chunk persistent
data**. Chunks restore their saved managed lights before rebuilding them on
load, including after a server restart. Unloading does not keep chunks loaded.
Disabling removes managed lights in loaded chunks; unloaded chunks are cleaned
when next loaded with the plugin installed. **Before uninstalling permanently,
load the affected chunks while the plugin is active and disable it cleanly.**
Otherwise invisible lights in unloaded terrain remain until the plugin is
installed again and those chunks load. Keep world backups.

Existing loaded chunks are scanned incrementally (one chunk per tick), and
light recalculations are bounded to eight sources per tick. Initial coverage is
not instantaneous. Ordinary placement/removal, support physics, pistons, fluids,
and explosions trigger updates; editing tools that bypass Bukkit block events
may require unloading/reloading their chunks to discover new lanterns.

## Invisible monster protection

Both lantern types prevent **natural hostile (`Enemy`) spawns within 24 blocks**
in a spherical radius, even where visible light is low.

- Requires direct **line of sight** from the lantern center to the spawn position
  (half a block above the entity's feet).
- Solid collision shapes block it, including glass and closed doors; it does
  not bend around obstacles. Water and passable plants do not block this check.
- Only `NATURAL` spawn events are blocked. Animals, breeding, spawners, raids,
  reinforcements, patrols, commands, and spawn eggs are unchanged.
- It does not remove mobs already present and soul candles do not provide wards.
- Only loaded, discovered lanterns participate; no chunks are force-loaded.

This is a server-only spawn check, not an invisible level-24 Minecraft light
source. It does not change mob AI or spawning rules outside the radius.

## Install and build

Place `betterlanterns-0.1.0.jar` in your Paper 26.3 server's `plugins/` directory
and restart on Java 25. No commands or configuration are needed.

```sh
./gradlew build
```

Output: `build/libs/betterlanterns-0.1.0.jar`. The build runs unit/MockBukkit
tests; these do not replace a real-client lighting and particle smoke test.
Gradle provisions the Java 25 toolchain automatically.

## Source and support

- [GitHub](https://github.com/rewrite123/papermc-plugins)
- [Report an issue](https://github.com/rewrite123/papermc-plugins/issues)

Include your Paper version and any lighting, protection, or world-edit plugins.

## Publishing

Uses [Hangar's official publishing plugin](https://github.com/HangarMC/hangar-publish-plugin).
Set `HANGAR_API_TOKEN` with `create_version` and `edit_page` permissions:

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar \
  -PhangarChangelog="Brighter lanterns, monster wards, and soul candles."
```

Use a unique version for every release. The main Hangar page is synchronized
from this README; the manual GitHub workflow uses `HANGAR_API_TOKEN`.
The original matching project icon is `assets/icon.png`.
