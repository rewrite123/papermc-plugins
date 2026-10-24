# UsefulCauldrons

**Cook meals, dye items, make charcoal, and process materials inside vanilla cauldrons.**

For **Paper 26.3** and **Java 25**. No menus, resource packs, client mods, or extra plugins are required.

![UsefulCauldrons features](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/usefulcauldrons/docs/images/features.png)

> **In game:** type `/help UsefulCauldrons` for the in-game guide. It ends with clickable links to this page and the [issue tracker](https://github.com/rewrite123/papermc-plugins/issues).

## Contents

- [Quick start](#quick-start)
- [Stews and soups](#stews-and-soups)
- [Warm milk and hot chocolate](#warm-milk-and-hot-chocolate)
- [Candy](#candy)
- [Wax and honey](#wax-and-honey)
- [Water, dyeing, and concrete](#water-dyeing-and-concrete)
- [Charcoal](#charcoal)
- [Storage, protection, and limits](#storage-protection-and-limits)
- [Commands, permissions, and configuration](#commands-permissions-and-configuration)
- [Install](#install)
- [Build from source and publish](#build-from-source-and-publish)
- [Links and support](#links-and-support)

## Quick start

![Cauldron workflow](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/usefulcauldrons/docs/images/cauldron-flow.png)

- Drop ordinary ingredients into the inside of a vanilla cauldron. Recipes run automatically as soon as they match; there is no menu or waiting step except charcoal.
- A **water cauldron** is used for stews and dye work. Stews require heat and an open top, and consume one water level per batch.
- **Dry heated cauldrons** cook milk, candy, wax, and honey. Keep the top open for these recipes.
- For **charcoal**, use a dry heated cauldron with a solid opaque block directly above it.
- Right-click with an empty bowl to collect one bowl-based stew or soup. Right-click with an empty hand to collect one warm milk or hot chocolate.
- Sneak-right-click with an empty main hand to retrieve all remaining ingredients and servings. Keep the required bowls in your inventory before retrieving bowl-based food.

Only ordinary, unmodified items are used as ingredients. Items with custom names, lore, enchantments, or other metadata are not consumed by recipes. A cauldron stores up to **4,096 ingredients**. It stores one kind of cooked food at a time; collect the batch before another food recipe can run. Partial recipes and leftovers remain inside.

Ingredients and cooked servings appear as small floating, spinning display entities. They are visual indicators, not dropped items, and cannot be picked up by players, mobs, or hoppers. They are recreated from saved contents after restarts and chunk reloads; they do not contain additional items or keep chunks loaded.

## Stews and soups

![Special stew recipes](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/usefulcauldrons/docs/images/stew-recipes.png)

All stew recipes use water, heat from below, and an open top. Each batch drains **one water level** and makes **two servings**. Bowls are used when collecting servings. Ordinary vanilla-style mushroom, beetroot, and rabbit stews do not require blaze powder.

| Batch | Ingredients | Food and effects per serving |
| --- | --- | --- |
| Mushroom stew | 1 red mushroom + 1 brown mushroom | 6 hunger points, 7.2 saturation |
| Beetroot soup | 6 beetroot | 6 hunger points, 7.2 saturation |
| Rabbit stew | 1 cooked rabbit + 1 carrot + 1 baked potato + 1 red **or** brown mushroom | 10 hunger points, 12 saturation |
| Golden Flavor | 1 golden apple + 1 golden carrot + 1 glow berries **or** cocoa beans + 1 blaze powder | Regeneration I for 8 s; 2.5 absorption hearts for 30 s |
| Speedy Stew | 1 raw rabbit **or** raw cod/salmon/tropical fish/pufferfish + 1 nether wart + 1 carrot + 1 sweet berries + 1 blaze powder | Speed I for 30 s |
| Carrota Stew | 4 golden carrots + 1 short grass + 1 spider eye + 1 nether wart + 1 blaze powder | Night vision I for 30 s |
| Spicy Chilli | 1 raw beef + 1 beetroot + 1 dried kelp + 1 gunpowder + 1 magma cream + 1 nether wart + 1 blaze powder | Fire resistance I for 30 s |
| Hearty Stew | 1 raw beef + 1 raw porkchop + 1 raw mutton + 1 pumpkin + 1 potato + 1 nether wart + **2 blaze powder** | Strength I for 10 s; fills hunger, at least 12 saturation, and 0.5 absorption hearts for 30 s |
| Fish Soup | 1 raw cod + 1 raw salmon + 1 carrot + 1 short grass + 1 vine + 1 nether wart + 1 blaze powder | Water breathing I for 10 s |
| Suspicious stew | 1 red mushroom + 1 brown mushroom + 1 supported vanilla suspicious-stew flower | 6 hunger points, 7.2 saturation; vanilla flower effect and duration |

Special food effects are level I unless noted above. Stews restore 6 hunger points and 7.2 saturation except rabbit stew. Pufferfish used in Speedy Stew does not poison the eater. Existing stronger or longer absorption is not shortened.

Suspicious stew keeps the vanilla effect and duration of its flower:

| Flower | Effect | Duration |
| --- | --- | ---: |
| Dandelion, blue orchid | Saturation | 7 ticks |
| Poppy, torchflower | Night vision | 5 s |
| Allium | Fire resistance | 3 s |
| Azure bluet | Blindness | 8 s |
| Red, orange, white, pink tulip | Weakness | 9 s |
| Oxeye daisy | Regeneration | 8 s |
| Cornflower | Jump boost | 6 s |
| Lily of the valley | Poison | 12 s |
| Wither rose | Wither | 8 s |
| Open eyeblossom | Blindness | 11 s |
| Closed eyeblossom | Nausea | 7 s |

Because matching is immediate, add shared ingredients last when making suspicious stew. For example, add the third ingredient only after deciding not to make mushroom stew. Custom recipes are checked before vanilla recipes when multiple matches complete together.

## Warm milk and hot chocolate

![Milk and other processing](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/usefulcauldrons/docs/images/processing-recipes.png)

Use an **empty, dry, heated, uncovered cauldron**:

| Recipe | Ingredients | Result |
| --- | --- | --- |
| Warm milk | 1 milk bucket + 4 bowls + 1 blaze powder | 4 warm-milk servings; returns 1 empty bucket |
| Hot chocolate upgrade | While all 4 warm-milk servings remain, add 6 cocoa beans + 2 sugar | Replaces the four servings with 4 hot chocolates |

Warm milk removes all active potion effects and blocks new beneficial and harmful potion effects for **6 seconds**. It restores no hunger and can be consumed while full. Hot chocolate blocks effects for **8 seconds**, grants **1 absorption heart for 30 seconds**, and restores **8 hunger points + 9.6 saturation**. Its own absorption effect is allowed through the immunity. Both drinks use bowls paid for by the recipe, so collecting them needs an empty hand and does not consume extra bowls.

The chocolate upgrade only works while all four warm-milk servings remain stored. Adding water instead of using an empty pot does not make milk; retrieve unwanted ingredients first.

## Candy

Use a **dry, heated, uncovered cauldron**:

| Sweetener | Ingredients for one candy | Returned items |
| --- | --- | --- |
| Sugar | 1 supported food + 26 sugar | None |
| Honey | 1 supported food + 12 honey bottles | 12 empty glass bottles |

Supported foods: apple, sweet berries, carrot, melon slice, golden apple, glistering melon slice, golden carrot, pumpkin, and chorus fruit. Honey is preferred if the pot has enough honey bottles and sugar at the same time. Finished candy pops out above the cauldron.

Every candy restores **12 hunger points (6 icons) + 14.4 saturation** and gives Speed I. Sugar candy lasts **10 seconds**; honey candy lasts **20 seconds**. Golden apple, glistering melon, and golden carrot add **5 seconds**. Candied golden apple also grants Regeneration II for 5 seconds and 2 absorption hearts for 30 seconds. Candied pumpkin looks like pumpkin pie; glistering melon becomes edible; candied chorus fruit does not teleport the player.

**Sugar burn:** entering a heated cauldron containing sugar starts a 20-second burn. Re-entering refreshes the timer; leaving the pot or entering water does not end it. It deals 2 health points (1 heart) of fire damage per second. Fire resistance and creative/spectator modes prevent the damage; another plugin may cancel it. Milk does not cure this burn, and death clears it. The timer uses real elapsed time, including time offline or while the server is stopped.

## Wax and honey

Use an **empty, dry, heated, uncovered cauldron**:

| Ingredients | Output |
| --- | --- |
| 1 raw beef **or** 1 raw porkchop + 1 gunpowder | 1 wax |
| 1 ordinary honeycomb + 1 empty glass bottle | 1 wax + 1 normal honey bottle |

No blaze powder is required. Outputs pop out above the pot; incomplete ingredients remain stored or can be retrieved. Wax is an item with honeycomb as its actual material and a white-dye appearance. Use it in vanilla candle recipes, copper-waxing recipes, or directly on unwaxed copper blocks. Other vanilla honeycomb recipes can also consume it. It cannot dye water.

Wax is marked as a custom item and cannot be used as the ordinary honeycomb for honey extraction, preventing a wax-to-honey loop. Normal honey bottles can be picked up and dropped into a cauldron for candy.

## Water, dyeing, and concrete

![Dyed water workflow](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/usefulcauldrons/docs/images/dyed-water.png)

An uncolored water cauldron hardens concrete powder or removes dye from leather and wolf armor. Drop the item inside, or right-click while holding it. One water level processes a whole dropped stack; a held item processes one per click. Armor names, enchantments, and damage are preserved.

Drop one ordinary dye into water to give it **64 uses**. A different dye replaces the color and resets it to 64 uses; adding the same color again does not waste dye. Retrieve ingredients before changing color. Refilling water does not reset uses.

| Action | Cost / behavior |
| --- | --- |
| Dye a dyeable item | 1 use per item; dropped stacks process only up to remaining uses |
| Dye a sheep | 1 use per entry; sheep already matching the color cost none |
| Enter dyed water as a player | 1 use per entry; grants matching movement particles for 60 seconds |
| Heat a dyed-water cauldron | Emits rising colored dust; vanilla smoke itself cannot be tinted without a resource pack |
| Make concrete | 4 sand + 4 gravel -> 8 concrete in the water's color; costs 8 uses, no heat |
| Empty a full dyed water cauldron | Right-click with an empty bucket; receives normal water and loses the dye color/remaining uses |

Supported dyed families include wool, carpets, beds, banners, concrete, terracotta, stained glass and panes, shulker boxes, candles, bundles, harnesses, leather armor, and wolf armor. Concrete powder hardens and recolors in the same operation. Existing item metadata, container contents, and banner patterns are preserved. At zero uses the water becomes ordinary water. Dye operations do not consume additional water levels. Vanilla washing, bottle extraction, and extinguishing cannot bypass the use counter.

## Charcoal

Drop eligible fuels into an **empty, dry, heated cauldron**, then place a **solid opaque block directly above it**. Keep the heat and cover in place for **60 seconds**. Black smoke rises above the cover while cooking; completion makes a hiss and light-gray smoke and ejects charcoal above the cover.

| Fuel | Input needed | Charcoal output |
| --- | ---: | ---: |
| Logs, wood, stripped logs/wood, including nether stems/hyphae | 1 | 2 |
| Planks | 2 of the same material | 1 |
| Wooden stairs, fences, gates, doors, trapdoors | 2 of the same material | 1 |
| Wooden slabs | 4 of the same material | 1 |
| Leaves, saplings, flowers | 4 of the same material | 1 |
| Sticks, bamboo, sugar cane, paper, vines, short grass, ferns, dead bushes, hanging/mangrove roots, mushrooms, wheat, kelp, dried kelp, carrots, potatoes, beetroot, sweet/glow berries | 4 of the same material | 1 |
| Wheat, melon, pumpkin, beetroot, torchflower seeds | 8 of the same material | 1 |

Eligible fuels can cook together, and incomplete groups remain retrievable. Other items are not converted. Removing heat or the cover resets the timer on the next cauldron tick. Unloaded chunks pause processing and are never force-loaded. A cover change that occurs entirely while a chunk is unloaded cannot be observed and therefore may not reset progress.

## Storage, protection, and limits

- Contents, dye uses, and charcoal progress are stored in `plugins/UsefulCauldrons/contents.yml`, saved atomically every second after changes and on normal shutdown.
- An abrupt process or power failure can lose up to the most recent second of progress. Back up the storage file with the world.
- Corrupt or unsupported storage prevents startup rather than silently discarding contents. Resolve the file issue before restarting.
- Cauldrons containing ingredients or servings cannot be broken, exploded, or pushed. Empty them first. Water can be refilled, but extracting or replacing fluid is blocked until contents are removed.
- If another plugin or world editor forcibly replaces a managed pot, stored ingredients and servings are ejected at that location on its next loaded tick.
- Cancelled player interactions and fluid events are respected. Automatic work fires the cancellable `CauldronProcessEvent` with an operation name and optional entity. Protection plugins must integrate with this event to protect automatic dropped-item processing and entity dyeing; universal protection-plugin compatibility is not implied.

## Commands, permissions, and configuration

| Surface | Available |
| --- | --- |
| Plugin commands | None. Use cauldrons directly; `/help UsefulCauldrons` is Paper's help topic. |
| Plugin permissions | None. |
| Configuration files | No user-facing config; persistent cauldron data is in `contents.yml`. |
| Runtime dependencies | None beyond Paper. |

## Install

Requires Paper **26.3** and Java **25**.

1. Download the JAR from [Hangar](https://hangar.papermc.io/rewrite123/usefulcauldrons).
2. Place it in the server's `plugins` directory.
3. Restart Paper.

<details>
<summary>Build from source and publish</summary>

From the `usefulcauldrons` folder, run the build and unit tests:

```sh
./gradlew build --console=plain
```

The Gradle wrapper uses Gradle 9.1.0 and compiles with Java 25. The built JAR is in `build/libs/`. From the repository root, regenerate the original README diagrams with:

```sh
python3 tools/docs/usefulcauldrons.py
```

The Hangar publication targets `rewrite123/usefulcauldrons`, the `Release` channel, and Paper `26.3`. Create the Hangar project and provide a token through `HANGAR_API_TOKEN`; never commit credentials. Normal builds do not publish. To publish a version and sync this README page:

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar --console=plain -PhangarChangelog="Release notes"
```

Change `gradle.properties` to an unused version before publishing. The Hangar page is synced from this README.

</details>

## Links and support

- [Source code and plugin folder](https://github.com/rewrite123/papermc-plugins/tree/main/usefulcauldrons)
- [Hangar project](https://hangar.papermc.io/rewrite123/usefulcauldrons)
- [Report a bug or request a feature](https://github.com/rewrite123/papermc-plugins/issues)
