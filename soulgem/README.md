# soulgem

Craft soul gems, charge them with newly earned XP, and use them instead of lapis
to enchant or upgrade gear. For **Paper 26.3 / Java 25**, with no runtime
dependencies or required resource pack.

**Latest release:** [SoulGem 0.1.0 on Hangar](https://hangar.papermc.io/rewrite123/soulgem/versions/0.1.0).

## Crafting and charging

Both recipes are shapeless and produce **one uncharged gem**:

| Ingredients | Result | Appearance |
|---|---|---|
| 1 lapis lazuli + 1 amethyst shard | Soul Gem | Named amethyst shard |
| 1 lapis lazuli + 1 echo shard | Echo Soul Gem | Named echo shard |

Hold one uncharged gem in your **off-hand** and earn XP. The gem absorbs newly
earned XP until it contains the full amount needed to advance from your current
level to the next (for example, 7 points at level 0 or 112 at level 30).
The target is fixed when that gem first absorbs XP; existing progress in your
player XP bar does not reduce the cost. Your existing XP is not removed.

Partial progress stays on the gem across swapping, trading, death drops, and
server restarts. Uncharged gems do not stack, so partial progress cannot be
duplicated by splitting a stack. Charged gems glow and stack up to 64.
Only the one off-hand gem charges; excess XP from the completing pickup goes
to the player. Swap another uncharged gem into your off-hand to charge more.
Both kinds have the same charging cost.

Charging handles positive `PlayerExpChangeEvent` awards, including normal XP
pickups after vanilla Mending processing. It does not intercept `/xp`, direct
plugin XP setters, or XP already spent repairing gear. Other plugins that alter
XP awards after SoulGem's highest-priority listener can affect the remainder.
Ordinary renamed shards are not gems. Gems cannot be recycled through crafting
recipes as if they were ordinary shards.

## Enchanting and upgrading

1. Hold **one piece of gear or one book in your main hand**.
2. Put **charged gems of one kind in your off-hand**.
3. Right-click an enchanting table with an empty cursor.
4. Click one of the three offers in the **Soul Gem Enchanting** menu.

The menu shows the resulting enchantments, minimum player level, and price.
Its items are display copies, not storage slots; your real gear and gems stay
in your hands until the purchase succeeds.

| Offer | Charged gems consumed | Player levels consumed |
|---|---|---|
| 1 | 1 | 1 |
| 2 | 2 | 2 |
| 3 | 3 | 3 |

Minimum-level offers use vanilla enchanting-table bookshelf formulas, capped
at 15 effective bookshelves; the third offer requires level 30 at full power.
Bookshelves need the normal clear gap. Obstructed or unloaded shelves do not
count, and the plugin never loads neighboring chunks. Vanilla
enchantment-power-provider/transmitter block tags are respected.
There is no free creative-mode bypass.

**Amethyst gems** use the server's enchanting-table enchantment pool and normal
enchantment levels. **Echo gems** raise each newly rolled enchantment by one,
up to its vanilla maximum +1: for example, Sharpness V becomes Sharpness VI,
Protection IV becomes Protection V, and a level-I-only enchantment can become II.
This does not add treasure-only enchantments such as Mending to the table pool.
Some enchantments have little or no extra effect above their vanilla maximum.

Already-enchanted gear and enchanted books can be upgraded. Existing
enchantments, names, durability, lore, and persistent metadata are preserved.
New enchantments must be compatible with the existing ones; lower rolls do not
reduce existing levels. Existing above-cap enchantments are not reduced.
An offer that would make no improvement is disabled and costs nothing.
Repeated echo enchants do not raise an enchantment indefinitely: only new rolls
receive +1, never the item's existing level.

Offers are deterministic for the player's current enchanting seed, item,
bookshelves, and gem kind. Reopening the menu does not grant free rerolls;
successful enchanting changes the player's seed. The plugin uses Paper's
native enchanting API for rolls, rather than reproducing its enchantment
selection rules. This custom menu is not a byte-for-byte reproduction of
vanilla previews or their random sequence.

Normal lapis-based enchanting is disabled server-wide, including the native
enchanting screen opened by other plugins. Items and costs are rechecked on
purchase; changing hands, bookshelves, seed, cursor, permission, world, or
distance invalidates the menu without consuming anything.
Stay within six blocks of the table, which must still exist.
All menu inventory moves, shift-clicks, number keys, double-clicks, drops, and
drags are blocked. Disconnecting or closing the menu cannot lose stored items
because it never stores any real items.

## Compatibility

- `soulgem.use` defaults to everyone and permits charging/enchanting. Players
  without it cannot fall back to normal lapis enchanting.
- Existing compatible enchantments remain intact, including enchantments
  obtained with other plugins. Anvils, loot, villagers, and enchanted-book use
  outside the enchanting table are not modified.
- With `craftablebottleofenchanting` installed, main-hand glass bottles retain
  that plugin's Bottle Experience menu. SoulGem loads after it.
- Table interaction protection cancellations are respected. Enchanting uses a
  custom chest menu and does **not** fire native `EnchantItemEvent` or
  `PrepareItemEnchantEvent` for purchases; plugins relying solely on those
  events need a compatibility integration. Protection plugins can cancel the
  table interaction or menu click.
- Vanilla amethyst/echo shards provide the appearance. No custom texture pack
  or client mod is required.

## Build and install

```sh
./gradlew build
```

Copy `build/libs/soulgem-0.1.0.jar` into your server's `plugins/` directory and
restart Paper 26.3 using Java 25. Gradle runs on Java 17+ and provisions Java 25;
Windows users can run `gradlew.bat`. No configuration is required.

Tests cover gem identity, recipes, charging accounting, offer costs, upgrade
merging, book handling, and menu transaction protections. The MockBukkit
registry harness targets Paper 26.2; production compiles against Paper 26.3.
Before production deployment, smoke-test native enchantment rolls, bookshelf
gaps, XP orbs with Mending, recipe-book/shift crafting, reconnects, and your
protection plugins on an actual Paper 26.3 server.

## Publish to Hangar

Create the `soulgem` project under your Hangar account, then:

```sh
export HANGAR_API_TOKEN='your-token'
./gradlew build publishPluginPublicationToHangar
```

Uses the official Hangar publishing Gradle plugin. The token needs version and
page editing permissions. Publication includes this README as the resource
page. Never commit the token or a `hangarkey` file.
