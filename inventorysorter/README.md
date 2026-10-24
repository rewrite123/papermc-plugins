# inventorysorter

**Combine matching partial stacks and sort your storage with a quick double Shift-click.** Choose the player-storage section or a supported container; items never move between them.

Built for **Paper 26.3 / Java 25**, with no runtime dependencies. Configured project version: **0.3.0**.

![InventorySorter features](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/inventorysorter/docs/images/features.png)

> **In game:** use `/help inventorysorter` for the full guide. The guide includes the available command and a clickable link to this page and the issue tracker.

## Contents

- [Quick start](#quick-start)
- [What gets sorted](#what-gets-sorted)
- [Sorting and stack merging](#sorting-and-stack-merging)
- [Commands](#commands)
- [Permission](#permission)
- [Configuration](#configuration)
- [Install](#install)
- [Links and support](#links-and-support)

## Quick start

![Double Shift-click sorting flow](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/inventorysorter/docs/images/double-click-flow.png)

1. Open your inventory or a supported container.
2. Make sure your cursor is empty and identify an empty slot in the section to sort.
3. Hold **Shift** and left- or right-click that **same empty slot twice within half a second**.
4. Keep the same screen open. Sorting is applied on the next server tick; matching stacks are combined first.

![Example before and after sorting](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/inventorysorter/docs/images/example-sort.png)

If your inventory is full, use `/sort` instead. The optional command can also target player storage or a supported container directly.

## What gets sorted

| Where you click or command | Slots that are sorted | Notes |
| --- | --- | --- |
| Player inventory | The 27 main storage slots (slots 9–35) | Hotbar, armor, offhand, and crafting slots are never moved. |
| Chest or double chest | The entire chest | Clicking your own storage while the chest is open sorts only your storage. |
| Barrel, placed shulker box, or your ender chest | The entire container | Shulker boxes carried as items are not opened or modified. |
| Other inventory screens | Your own main storage, when you click those slots | Includes crafting table, furnace, smoker, blast furnace, anvil, enchanting table, brewing stand, villager trade, hopper, and similar screens. Their special slots are not sorted. |
| Custom plugin menu | Only supported physical containers are sorted | Your own storage below a custom menu can be sorted unless the menu plugin cancels the click. |

A placed shulker box must be open as a container to sort its contents. The plugin never moves items between the player inventory and a container.

## Sorting and stack merging

![Stack combining and category ordering example](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/inventorysorter/docs/images/example-sort.png)

The sort order is alphabetical by **category**, then by the English Minecraft material identifier (not custom names or translated client labels):

| Category | Examples |
| --- | --- |
| Armor | Helmets, chestplates, leggings, boots, elytra |
| Brewing | Potions, cauldrons, glass bottles, brewing stands |
| Building Blocks | Blocks not matched by a more specific category |
| Decoration | Dyes, banners, music discs, pottery sherds |
| Enchanting | Enchanted books, enchanting tables, experience bottles, anvils |
| Food | Edible items |
| Minerals | Ores, ingots, nuggets, raw materials, and listed minerals |
| Miscellaneous | Items not matched by another category |
| Mob Drops | Bones, string, rotten flesh, and listed drops |
| Plants | Flowers, crops, saplings, leaves, and other listed plants |
| Redstone | Redstone parts, buttons, pressure plates, and rails |
| Spawn Eggs | All spawn eggs |
| Storage | Chests, barrels, shulker boxes, bundles, and buckets |
| Tools | Tools, pickaxes, axes, shovels, hoes, and listed utility items |
| Transport | Boats, rafts, minecarts, saddles, and horse armor |
| Weapons | Swords, bows, crossbows, tridents, spears, arrows, and shields |

Each category has fixed precedence in the implementation. For example, edible mob drops are Food, ores are Minerals rather than Building Blocks, and hopper is Redstone. Items within each category are ordered by their material names.

Before sorting, similar stacks combine up to the inventory's maximum stack size. Only stacks that vanilla considers similar merge: renamed, enchanted, damaged, or otherwise different item metadata prevents a merge. The total item count and metadata are preserved; empty slots move to the end. When stacks are combined, the resulting order follows the sorted pile sequence. Existing overstacked items are left intact rather than normalized.

## Commands

| Command | Description |
| --- | --- |
| `/sort` | Sort the open supported container, or your 27 main storage slots if no supported container is open. Also sorts player storage while other screens are open. |
| `/sort player` | Sort the 27 main player-storage slots. |
| `/sort container` | Sort an open chest, barrel, placed shulker box, or ender chest. |
| `/inventorysorter:sort` | Namespaced alternative if another plugin also registers `/sort`; add the same optional argument if needed. |

Commands are player-only and require an empty cursor. `/sort container` needs a supported container open. The plugin reports a missing container rather than sorting an unrelated screen. Invalid arguments show the command usage. The command is useful when the inventory has no empty slot for the double-click gesture.

## Permission

| Permission | Default | Grants |
| --- | --- | --- |
| `inventorysorter.use` | Everyone | Use the sort command and double-click gesture. |

Players who are offline, dead, or in spectator mode cannot sort. Operators do not get a separate sorting mode.

## Configuration

There is no `config.yml` and no configurable sorting behavior.

## Install

1. Download the plugin jar from [Hangar](https://hangar.papermc.io/rewrite123/inventorysorter).
2. Place it in the server's `plugins/` directory and restart. Run the server on Java 25 and Paper 26.3.
3. Players with `inventorysorter.use` can sort immediately; it is granted to everyone by default.

The plugin has no runtime dependencies. Closing an inventory, disconnecting, or making another kind of click resets a pending double-click. Cancelled clicks are respected. Before applying a scheduled sort, the plugin checks that the player can still sort, the same view remains open, and the cursor and clicked slot are still empty.

<details>
<summary>Build from source and publish</summary>

Run from this plugin directory:

```sh
./gradlew build
```

Gradle runs on Java 17+ and provisions a Java 25 toolchain if needed. Windows users can use `gradlew.bat`. The jar is written to `build/libs/`. Tests cover item grouping, stack combination and metadata, click timing, supported targets, command handling, and event behavior. The tests use MockBukkit's available Paper 26.2 registry harness; production compiles against Paper 26.3. Verify the gesture on a Paper 26.3 test server before deploying.

This README is also used as the Hangar project description. The Gradle publication is configured to use `README.md` as its resource page. Images use absolute raw GitHub URLs so they render on both GitHub and Hangar. To regenerate the original diagrams, run this from the repository root:

```sh
python3 tools/docs/inventorysorter.py
```

To publish, set a unique version in `gradle.properties` and provide a Hangar API key with `create_version` permission through `HANGAR_API_TOKEN` (never commit the key):

```sh
./gradlew build publishPluginPublicationToHangar \
  -PhangarChangelog="Describe the changes in this release."
```

The default channel is `Release`, and the declared compatibility is Paper `26.3`. Override with `-PhangarProject=your-slug`, `-PhangarChannel=Snapshot`, or `-Pversion=0.3.1` as needed. Custom channels must exist on Hangar first. See [HangarMC's Gradle publishing plugin](https://github.com/HangarMC/hangar-publish-plugin).

</details>

## Links and support

- **Source and full guide:** [GitHub](https://github.com/rewrite123/papermc-plugins/tree/main/inventorysorter)
- **Download:** [Hangar](https://hangar.papermc.io/rewrite123/inventorysorter)
- **Report a bug or request a feature:** [GitHub issues](https://github.com/rewrite123/papermc-plugins/issues)
