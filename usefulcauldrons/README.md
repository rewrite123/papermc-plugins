# UsefulCauldrons

Cooking, dyeing, candy and charcoal using real cauldrons, without menus, resource
packs or client mods. Requires **Paper 26.3** and **Java 25**.

## Getting started

Drop ingredients into the **inside** of a cauldron. They are stored, cannot be
picked back up from the ground, and automatically craft as soon as a recipe
matches. Partial recipes and leftovers remain inside. Only ordinary, unmodified
items are accepted as ingredients: named, enchanted and other custom items are
not consumed by recipes. Capacity: 4,096 ingredients per cauldron.

Stored ingredients appear as small, gently floating and spinning items inside
the cauldron, with one visual per ingredient type. Cooked servings also have a
food visual. These are display entities, not dropped items: players, mobs and
hoppers cannot pick them up, and they cannot burn or despawn as dropped items do.
They update when ingredients cook or are retrieved, and disappear when empty.
The visuals are recreated from stored contents after a restart or chunk reload;
they do not contain extra copies of your items or keep chunks loaded.

- Add water normally with a water bucket.
- Heat means fire, soul fire, lava, or a **lit** campfire/soul campfire immediately
  below the cauldron.
- **Right-click with an empty bowl:** collect one cooked stew/soup/chilli.
- **Right-click with an empty hand:** collect one warm milk/hot chocolate.
  Their recipe already pays for the bowls.
- **Sneak-right-click with an empty main hand:** retrieve all ingredients and
  cooked servings. Keep enough empty bowls in your inventory for any stews;
  nothing is removed if you do not have enough. Milk needs no additional bowls.
- Finished candy, charcoal, concrete, cleaned/dyed dropped items and returned
  buckets/bottles pop out above the cauldron. Outputs are not automatically
  sucked back in; pick them up and throw them again to process them again.
- Eating/drinking a bowl-based food returns its empty bowl (except creative).

One kind of cooked food is stored at a time. Collect it before cooking another
batch. Matching is immediate, not delayed: add shared ingredients such as the
two mushrooms **last** when making suspicious stew, or mushroom stew will
already have cooked. Custom recipes are checked before vanilla recipes when
several are complete together.

## Water

An uncolored water cauldron removes leather/wolf armor dye and turns concrete
powder into its matching concrete. Drop the item inside or right-click while
holding it. One water level processes a dropped stack, or one held item per
click. Armor names, enchantments and damage are preserved.

## Hot water: stews

Each batch uses **one water level**. Bowls are required only when collecting.
Vanilla stew recipes yield **two servings** instead of one, without blaze powder:

| Stew | Ingredients |
|---|---|
| Mushroom | 1 red mushroom + 1 brown mushroom |
| Beetroot | 6 beetroot |
| Rabbit | 1 cooked rabbit + 1 carrot + 1 baked potato + 1 red **or** brown mushroom |
| Suspicious | 1 red mushroom + 1 brown mushroom + 1 supported vanilla suspicious-stew flower |

Suspicious stew retains the flower's vanilla effect and duration: dandelion/
blue orchid saturation (7 ticks); poppy/torchflower night vision (5s);
allium fire resistance (3s); azure bluet blindness (8s); any tulip weakness (9s);
oxeye daisy regeneration (8s); cornflower jump boost (6s); lily of the valley
poison (12s); wither rose wither (8s); open eyeblossom blindness (11s);
closed eyeblossom nausea (7s).

### Special batches

Every row makes **two servings** and requires **one blaze powder**, except
Hearty which needs **two**. Ingredients are one item each unless stated.
Effects are level I. Absorption is yellow extra hearts lasting 30 seconds.
Ingredient and blaze-powder costs are per two-serving batch. Previously cooked
servings remain available after updating; only newly cooked batches use this yield.

| Food | Ingredients (plus blaze powder) | Effect per serving |
|---|---|---|
| Golden Flavor | golden apple, golden carrot, glow berries **or** cocoa beans | regeneration 8s; 2.5 absorption hearts |
| Speedy Stew | raw rabbit **or** raw cod/salmon/tropical fish/pufferfish, nether wart, carrot, sweet berries | speed 30s |
| Carrota Stew | **4** golden carrots, short grass, spider eye, nether wart | night vision 30s |
| Spicy Chilli | raw beef, beetroot, dried kelp, gunpowder, magma cream, nether wart | fire resistance 30s |
| Hearty Stew | raw beef, raw porkchop, raw mutton, pumpkin, potato, nether wart, **2 blaze powder** | strength 10s; full hunger; 0.5 absorption heart |
| Fish Soup | raw cod, raw salmon, carrot, short grass, vine, nether wart | water breathing 10s |

Stews restore 6 hunger points (3 icons) and 7.2 saturation, except rabbit stew
which restores 10 hunger points and 12 saturation. Hearty fills hunger and
ensures at least 12 saturation. Pufferfish used in stew does not poison you.
Stronger/longer existing absorption is not shortened.

## Warm milk and hot chocolate

Use an **empty, heated cauldron**, without water:

- **1 milk bucket + 4 bowls + 1 blaze powder** immediately makes **4 warm milk**,
  returning one empty bucket.
- While **all four warm-milk servings are still stored**, drop in **6 cocoa
  beans + 2 sugar** to upgrade them to **4 hot chocolate**.
- Warm milk removes all potion effects, then blocks all new potion effects
  (beneficial and harmful) for **6 seconds**.
- Hot chocolate does the same for **8 seconds**, grants **1 absorption heart
  for 30 seconds**, and restores **8 hunger points + 9.6 saturation**.
- Warm milk restores no hunger. Either drink can be consumed at full hunger.
  Chocolate's own absorption is exempt from its effect immunity.

Adding water instead of using an empty pot does not make milk. Clear unwanted
ingredients using the retrieval gesture.

## Candy

Use an **empty, heated, uncovered cauldron**, without water:

- **1 food + 26 sugar** makes **1 Candied Food**.
- **1 food + 12 honey bottles** makes **1 Honey Candied Food**, returning
  **12 empty glass bottles**. Honey is preferred if both sweeteners are present.
- Supported foods: apple, sweet berries, carrot, melon slice, golden apple,
  glistering melon slice, golden carrot, pumpkin and chorus fruit.
- Every candy restores **12 hunger points (6 icons) + 14.4 saturation**.
- Sugar candy gives speed I for **10s**; honey candy for **20s**.
- Golden apple, glistering melon and golden carrot add **5s** to that duration.
- Golden apples keep regeneration II for 5s and 2 absorption hearts for 30s.
- Candied pumpkin looks like pumpkin pie. Glistering melon becomes edible.
  Candied chorus fruit does not randomly teleport you.

Entering a heated cauldron that still contains sugar starts a **20-second
sticky burn**. Leaving or entering water does not stop it; re-entering the
cauldron refreshes the timer. It deals **2 health points (1 heart) of fire
damage per second**, twice the previous damage, for the same 20-second duration.
Fire resistance and creative/spectator immunity still apply; other plugins
may cancel the damage. Milk is not a cure for this burn, which is not a potion
effect. Death clears it. Burn and milk-immunity timers use real elapsed time,
including time logged out or while the server is stopped.

## Dyed water

Drop one ordinary dye into water to give it **64 uses**. A different dye
replaces the color and resets to 64; the same dye does not waste additional dye
while the water is already that color. Retrieve ingredients before changing
color. Refilling water does not reset uses. Emptying it with a bucket discards
the color.

- Drop **4 sand + 4 gravel** into dyed water to make **8 concrete** in that color,
  with **no crafting dye**. Costs **8 uses**. No heat needed.
- Drop dyeable items inside, or right-click holding them: **1 use per item**.
  A dropped stack processes only as many items as remaining uses allow.
- Recolors wool, carpets, beds, banners, concrete, terracotta, glass/panes,
  shulker boxes, candles, bundles, harnesses, leather armor and wolf armor.
  Existing item metadata, container contents and banner patterns are preserved.
- Concrete powder is hardened and recolored in the same operation.
- Sheep entering are recolored for **1 use** (already matching sheep cost none).
- Players entering gain matching walking particles for **one minute**, costing
  **1 use per entry**, not per tick. Standing still does not emit a trail.
- Heated dyed water emits rising color-matched dust (vanilla smoke cannot be
  tinted without a resource pack).
- At zero uses the water becomes ordinary water. Dye operations do not use
  additional water levels. Vanilla washing/bottle extraction/extinguishing
  cannot bypass the use counter.

## Charcoal

Drop materials into an **empty, heated cauldron**, then place a **solid opaque
block directly above it**. Keep both heat and lid in place for **60 seconds**.
Black smoke rises above the lid while cooking. Completion produces a hiss and
light-gray smoke, and ejects the charcoal above the lid.

| Input | Charcoal |
|---|---:|
| 1 log/wood/stripped log/stripped wood (including nether stems/hyphae) | 2 |
| 2 planks of the **same type** | 1 |
| 2 wooden stairs/fences/fence gates/doors/trapdoors of the **same type** | 1 |
| 4 wooden slabs of the **same type** | 1 |
| 4 leaves, saplings or flowers of the **same type** | 1 |
| 4 sticks, bamboo, sugar cane, paper, vines, short grass, ferns, dead bushes, hanging/mangrove roots, red/brown mushrooms, wheat, kelp/dried kelp, carrots, potatoes, beetroot, sweet/glow berries of the **same type** | 1 |
| 8 wheat/melon/pumpkin/beetroot/torchflower seeds of the **same type** | 1 |

All eligible materials cook together; incomplete groups remain retrievable.
Other items are not converted. Removing heat or lid resets the timer when the
cauldron next ticks. Unloaded chunks pause processing and are never force-loaded.
Do not leave a lid off and replace it while the chunk is unloaded if you expect
that unobserved change to reset progress.

## Persistence and safety

Contents, dye uses and charcoal progress are saved atomically in
`plugins/UsefulCauldrons/contents.yml` every second after changes and on normal
shutdown. Abrupt process/power failure can lose the most recent second of
progress. Corrupt/unsupported storage prevents startup rather than silently
discarding contents. Back up this file with the world.

Cauldrons containing ingredients/servings cannot be broken, exploded or pushed.
Empty them first. Water refilling is allowed; extracting/replacing contents
with another fluid is blocked until empty. If a world editor or another plugin
forcibly replaces a pot, stored items/foods are ejected at that position on its
next loaded tick.

Cancelled player interactions and fluid events are respected. Automatic actions
also expose the cancellable `CauldronProcessEvent` for protection integrations;
claims plugins must explicitly integrate with this event for automatic dropped
items/entity dyeing. This does not claim universal protection-plugin support.
There are no commands or extra permissions.

## Build and publish

```sh
./gradlew build
HANGAR_API_TOKEN=your-token ./gradlew publishPluginPublicationToHangar \
  syncAllPluginPublicationPagesToHangar -PhangarChangelog="Release notes"
```

Install the resulting JAR from `build/libs/` in the server's `plugins/` directory
and restart. Hangar project: <https://hangar.papermc.io/rewrite123/usefulcauldrons>.
