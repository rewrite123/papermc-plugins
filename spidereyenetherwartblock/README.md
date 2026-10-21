# spidereyenetherwartblock

Craft **one nether wart block** from **nine spider eyes** in a crafting table.
For **Paper 26.3 / Java 25**, with no runtime dependencies.

**Latest release:** [0.1.0 on Hangar](https://hangar.papermc.io/rewrite123/spidereyenetherwartblock/versions/0.1.0).

## Recipe

Fill all nine slots of a crafting table with ordinary spider eyes:

```text
Spider Eye | Spider Eye | Spider Eye
Spider Eye | Spider Eye | Spider Eye  ->  1 Nether Wart Block
Spider Eye | Spider Eye | Spider Eye
```

The recipe requires a 3x3 crafting grid; it does not fit the player's 2x2 grid.
Fermented spider eyes do not work. Each craft consumes one eye from each slot.
Shift-crafting and recipe-book filling use normal server crafting behavior.
The recipe is automatically discovered for online players and players joining.

The result is an ordinary vanilla nether wart block. Vanilla recipes, including
the normal nether-wart-to-block recipe, are not replaced. There are no commands,
permissions, or configuration files.

## Build and install

```sh
./gradlew build
```

Copy `build/libs/spidereyenetherwartblock-0.1.0.jar` to your server's `plugins/`
directory and restart Paper using Java 25. The Gradle wrapper provisions the
Java 25 build toolchain. Windows users can run `gradlew.bat`.

Tests verify the full 3x3 recipe, ingredient identity, exact vanilla output,
recipe discovery, and registration/cleanup across disable and re-enable.

## Publish

The publication targets `rewrite123/spidereyenetherwartblock` on Hangar.
Provide an API token through `HANGAR_API_TOKEN`, then run:

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar
```

The official Hangar publishing plugin uploads the JAR and syncs this README.
Never commit credentials.
