# inventorysorter

Inventory sorting for **Paper 26.3 / Java 25**, with no runtime dependencies.

## Usage

Hold Shift and left- or right-click the **same empty inventory slot twice within
half a second** while your cursor is empty. The inventory section you clicked is
sorted on the next server tick:

- Player inventory: the 27 main storage slots only. Hotbar, armor, offhand, and
  crafting slots are never moved.
- Chest or double chest: the entire chest.
- Shulker box, barrel, or your ender chest: the entire container.
- Clicking player storage while a container is open sorts only player storage;
  clicking the container sorts only that container. Items never cross between them.

Open a placed shulker box to sort its contents; the plugin does not change the
contents of shulker-box items carried inside another inventory. Crafting tables,
furnaces, anvils, merchant interfaces, and custom plugin menus are not supported:
their slots may have special meanings or restrictions.

The `inventorysorter.use` permission is granted to everyone by default.
Spectators cannot sort. Cancelled inventory clicks are respected. Closing the
inventory, disconnecting, or clicking another kind of slot resets the gesture.
The delayed sort checks that the same view remains open and the clicked slot and
cursor are still empty before changing anything.

Optional command alternatives (also useful when the inventory is completely full):

| Command | Action |
| --- | --- |
| `/sort` | Sort the open container, or player storage if no container is open |
| `/sort player` | Sort the 27 player storage slots |
| `/sort container` | Sort the open supported container |

If another plugin owns `/sort`, use `/inventorysorter:sort`.

## Ordering

Groups are sorted alphabetically, then material names alphabetically within each
group (the English Minecraft material identifiers, not custom names or translated
client labels). The groups are:

Armor, Brewing, Building Blocks, Decoration, Enchanting, Food, Minerals,
Miscellaneous, Mob Drops, Plants, Redstone, Spawn Eggs, Storage, Tools, Transport,
Weapons.

Examples: carrots and cooked beef are Food; bones and string are Mob Drops;
flowers and saplings are Plants; axes and pickaxes are Tools. Items matching
multiple categories have a fixed precedence in `ItemGroups`: edible items are
Food even if they also drop from mobs; ores are Minerals rather than Building
Blocks. Unclassified blocks go to Building Blocks, and remaining items go to
Miscellaneous.

Empty slots move to the end. Existing stacks are **not merged or split**. Stacks
of the same material retain their relative order. Enchantments, names, durability,
container contents, counts, and all other item metadata are preserved.

## Build and install

From this directory:

```sh
./gradlew build
```

Gradle runs on Java 17+ and provisions a Java 25 toolchain if needed. Windows users
can use `gradlew.bat`. Copy `build/libs/inventorysorter-0.1.0.jar` to the Paper
server's `plugins/` directory and restart the server (running Java 25).

Run `./gradlew test` for the grouping, stack preservation, gesture, inventory
target, and event handling tests. Behavioral tests use MockBukkit's available
26.2 registry harness; production code compiles against Paper 26.3. Verify the
gesture with your Minecraft client on a Paper 26.3 test server before deploying
to production.

## Publish to Hangar

Releases are published to [rewrite123/inventorysorter](https://hangar.papermc.io/rewrite123/inventorysorter)
using [HangarMC's official Gradle plugin](https://github.com/HangarMC/hangar-publish-plugin).
Supply a Hangar API key with `create_version` permission through the
`HANGAR_API_TOKEN` environment variable; never commit API keys.

Set a unique `version` in `gradle.properties`, then run:

```sh
./gradlew build publishPluginPublicationToHangar \
  -PhangarChangelog="Describe the changes in this release."
```

The default channel is `Release` and declared compatibility is Paper `26.3`.
Override with `-PhangarProject=your-slug`, `-PhangarChannel=Snapshot`, or
`-Pversion=0.1.1` as needed. Custom channels must exist on Hangar first.
