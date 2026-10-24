# SoulGem

**Charge crafted soul gems with new XP, then use them in the vanilla enchanting table to enchant or upgrade gear.** Echo gems boost new rolls by one level.

Built for **Paper 26.3 / Java 25**. No runtime dependencies or required resource pack.

![Features at a glance](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/soulgem/docs/images/features.png)

> **In game:** type `/help soulgem` for a full guide to crafting, charging, and enchanting. It ends with clickable links to this page and the [issue tracker](https://github.com/rewrite123/papermc-plugins/issues).

## Contents

- [Quick start](#quick-start)
- [Crafting recipes](#crafting-recipes)
- [Charging](#charging)
- [Enchanting and upgrading](#enchanting-and-upgrading)
- [Permissions](#permissions)
- [Compatibility and limits](#compatibility-and-limits)
- [Install](#install)
- [Links and support](#links-and-support)

## Quick start

![From shards to enchanting](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/soulgem/docs/images/soulgem-flow.png)

1. Craft a Soul Gem or Echo Soul Gem.
2. Hold one uncharged gem in your **off-hand** and earn XP. It absorbs new XP until it contains the full amount needed to advance from your current level to the next.
3. Put the charged gem in an enchanting table's lapis slot and use a vanilla offer.

No commands, configuration, resource pack, datapack, or client mod are required.

## Crafting recipes

Both recipes are shapeless and make **one uncharged gem**:

| Ingredients | Result | Appearance before charging |
| --- | --- | --- |
| 1 lapis lazuli + 1 amethyst shard | 1 Soul Gem | Amethyst shard |
| 1 lapis lazuli + 1 echo shard | 1 Echo Soul Gem | Echo shard |

![Soul Gem crafting recipe](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/soulgem/docs/images/amethyst-recipe.png)

![Echo Soul Gem crafting recipe](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/soulgem/docs/images/echo-recipe.png)

Gems are identified by plugin data, not by their displayed name, and cannot be recycled as normal shards or used as crafting ingredients. An ordinary renamed shard is not a gem.

## Charging

![Charging flow](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/soulgem/docs/images/charging-flow.png)

Hold **one uncharged gem** in your off-hand and earn XP. The gem stores the amount needed to advance one full level, fixed when it first absorbs XP. For example, the amount is 7 points at level 0 and 112 points at level 30. XP already in your progress bar does not reduce this cost, and your existing XP is not removed.

Partial progress is stored on the item and survives swapping, trading, death drops, and restarts. Uncharged gems do not stack, preventing partial charge from being duplicated by splitting a stack. Only the off-hand gem charges. The excess from an XP award that completes a gem goes to the player; then swap in another uncharged gem if you want to charge more. Both gem types have the same XP cost. Charged gems glow and stack up to 64.

Charging applies to positive `PlayerExpChangeEvent` awards, including ordinary XP pickups after vanilla Mending processing. It does not intercept `/xp`, direct plugin XP setters, or XP already spent repairing gear. Plugins that alter awards after SoulGem's highest-priority listener can change the remainder.

## Enchanting and upgrading

SoulGem keeps the **vanilla enchanting table screen**. Put a gear item or book in the item slot and charged gems in the lapis slot, then select an offer. Offers, bookshelf power, previews, and seeds remain vanilla:

| Offer | Charged gems consumed | Player levels consumed |
| ---: | ---: | ---: |
| 1 | 1 | 1 |
| 2 | 2 | 2 |
| 3 | 3 | 3 |

Plain lapis no longer produces offers; the player is told that charged gems are needed. In creative mode an empty fuel slot still works as in vanilla. The fuel slot holds one stack, so do not mix gem kinds.

- **Soul Gem (amethyst):** normal vanilla enchantment roll.
- **Echo Soul Gem:** each rolled enchantment gains **+1**, up to its vanilla maximum +1. For example, Sharpness V can become VI and Protection IV can become V; level-I-only enchantments can become II. The hover preview shows the boosted level.

Vanilla does not add treasure enchantments such as Mending to its offer pool. Some enchantments have little or no extra effect beyond their vanilla maximum.

### Upgrading enchanted gear

SoulGem re-enables table offers for already-enchanted gear (the vanilla table normally greys it out). Existing enchantments, names, durability, and lore are retained. Only rolled enchantments that increase an existing level or add a non-conflicting enchantment are applied. Lower rolls do not reduce levels. If an offer would not improve the item, it is refused without consuming anything; select a different offer or change bookshelves to reroll. Echo gems boost new rolls only and cannot repeatedly raise existing levels. Enchanted books cannot be re-enchanted because vanilla does not allow it.

### Charged gem appearance and legacy items

The vanilla table fuel slot accepts lapis lazuli only, enforced by both server and client. A charged gem is therefore a lapis lazuli item that retains the amethyst or echo shard model, name, glint, and lore. Inventory appearance is still a shard, though the table's cost tooltip may identify its base material as lapis. Uncharged gems remain real shards. Gems cannot serve as crafting lapis.

Charged gems from 0.1.x (which were charged shards) are converted automatically when the holder joins or opens an inventory, preserving their count. Version 0.2.0 removed the custom chest menu; enchanting now happens in the normal table.

## Permissions

| Permission | Default | Grants |
| --- | --- | --- |
| `soulgem.use` | Everyone | Charge soul gems and enchant at tables. |

Players without `soulgem.use` cannot enchant at tables at all. Dead players and spectators cannot use SoulGem's charging/enchanting behavior.

## Compatibility and limits

- When **PhantomMenace** is enabled, its marked phantom repellant is recognized and its helmet-enchanting offers and purchases are left to that plugin. Ordinary lapis remains rejected. Both plugins must be installed for the exception.
- Enchanting fires native `PrepareItemEnchantEvent` and `EnchantItemEvent`. Protection, logging, and enchant-modifying plugins can still participate; plugins modifying enchantments at `HIGHEST` priority may change the result.
- Anvils, loot, villager trades, and enchanted books used outside the table are not modified. `craftablebottleofenchanting` works independently.
- Tests use a MockBukkit registry harness targeting Paper 26.2; production compiles against Paper 26.3. Smoke-test enchanting, upgrades, XP orbs with Mending, and protection plugins on the target server.

## Install

1. Download the latest jar from [Hangar](https://hangar.papermc.io/rewrite123/soulgem).
2. Put `soulgem-0.3.0.jar` in the server's `plugins/` directory and restart Paper 26.3 on Java 25.
3. No configuration is required.

Build and run the tests with `./gradlew build --console=plain` from the plugin folder. Gradle runs on Java 17+ and provisions Java 25; Windows users can run `gradlew.bat`. Tests cover gem identity, legacy conversion, recipes, charging accounting, lapis refusal, echo boosts, and upgrade merging.

<details>
<summary>Build from source and publish</summary>

Create the `soulgem` Hangar project and set `HANGAR_API_TOKEN` with version and page editing permissions. Never commit the token or a `hangarkey` file.

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar --console=plain
```

This README is synced as the Hangar resource page. Generate or refresh its original diagrams from the repository root with `python3 tools/docs/soulgem.py`; the README uses absolute raw GitHub image URLs so they also display on Hangar.

</details>

## Links and support

- **Source and full guide:** [github.com/rewrite123/papermc-plugins/tree/main/soulgem](https://github.com/rewrite123/papermc-plugins/tree/main/soulgem)
- **Download:** [hangar.papermc.io/rewrite123/soulgem](https://hangar.papermc.io/rewrite123/soulgem)
- **Report a bug or request a feature:** [GitHub issues](https://github.com/rewrite123/papermc-plugins/issues)
