# Phantom Menace

**Brew Phantom Repellant, enchant a helmet, and stop insomnia-driven phantom spawns for its wearer.**

For **Paper 26.3** and **Java 25**. No client mod, resource pack, or datapack is required.

![Original Phantom Menace feature overview](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/phantommenace/docs/images/features.png)

> In-game guide: use `/help phantommenace` for the concise, color-coded guide. Paper's help output also provides clickable links to the full guide and issue tracker.

## Contents

- [Quick start](#quick-start)
- [Brewing Phantom Repellant](#brewing-phantom-repellant)
- [Enchanting a helmet](#enchanting-a-helmet)
- [What Phantom Menace changes](#what-phantom-menace-changes)
- [Compatibility and limits](#compatibility-and-limits)
- [Commands, permissions, and configuration](#commands-permissions-and-configuration)
- [Install and upgrade](#install-and-upgrade)
- [Build and publish](#build-and-publish)
- [Links and support](#links-and-support)

## Quick start

1. Install the plugin JAR on Paper 26.3 running Java 25 and fully restart the server.
2. Brew Phantom Repellant from plain water bottles and phantom membranes.
3. Put an eligible helmet in an enchanting table and repellant in the lapis slot.
4. Select the first offer. It adds Phantom Menace I for one player level and consumes one repellant.
5. Wear the helmet. Natural phantom pre-spawns associated with that player are cancelled.

![Brewing process from water bottles to repellant](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/phantommenace/docs/images/brewing-recipe.png)

![Steps for enchanting and using a protected helmet](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/phantommenace/docs/images/enchanting-flow.png)

## Brewing Phantom Repellant

Use the brewing stand normally. Put up to three **plain water bottles** in bottle slots, one **phantom membrane** in the ingredient slot, and provide normal blaze-powder fuel. The registered brewing recipe converts each water bottle to one Phantom Repellant. Other potion types, splash or lingering potions, glass bottles, and water potions with custom effects do not match.

| Input | Result | Notes |
| --- | --- | --- |
| Water potion + phantom membrane | Phantom Repellant per bottle | Up to three bottles per brewing cycle; normal brewing fuel is required |
| Awkward or other potion | No repellant | Only plain water bottles are accepted |

Repellant appears as a glowing awkward potion, but its underlying item is **lapis lazuli**. It cannot be drunk. The potion appearance and contents are visual; the item has a persistent marker so ordinary lapis or renamed potions cannot impersonate it. Vanilla enchanting UI may still describe the fuel as lapis. Repellant is blocked from crafting recipes that use lapis.

## Enchanting a helmet

Place the helmet in an enchanting table's item slot and Phantom Repellant in its lapis slot. The plugin replaces the offers with one valid offer in the first slot: **Phantom Menace I** for **one player level**. No bookshelves are required. Other enchantments, names, durability, and item metadata already on the helmet are retained.

| Supported helmet materials | Not supported |
| --- | --- |
| Leather, chainmail, copper, iron, golden, diamond, netherite, turtle | Books, enchanted books, pumpkins, mob heads, and non-helmet armor |

A helmet already carrying Phantom Menace cannot receive it again. After a successful non-creative enchant, one empty glass bottle drops above the enchanting table. Cancelled or unsuccessful enchanting returns no bottle. Creative enchanting follows vanilla's free-enchant behavior and does not create a bottle because it consumes no repellant. A helmet with the enchantment works only while worn.

## What Phantom Menace changes

Phantom Menace is a persistent custom enchantment registered through Paper's bootstrap registry API. It is supported on the helmets listed above, has one level, and is not injected into random enchanting, loot, or villager enchantment pools. Vanilla anvils and grindstones handle it as an enchantment.

The plugin cancels **natural phantom pre-spawns associated with the wearer** when that player has an enchanted helmet equipped. It does not reset insomnia, remove existing phantoms, protect against attacks, or affect command- or spawner-triggered phantoms. Other plugins may still cancel native brewing or enchanting events.

## Compatibility and limits

| Item | Behavior |
| --- | --- |
| Paper | 26.3 |
| Java | 25 |
| Configuration | None |
| Commands / permissions | None |
| Client changes | None |

**SoulGem:** use SoulGem **0.2.1 or newer**. Older versions treat Phantom Repellant as ordinary lapis and reject the special fuel. Repellant adds only Phantom Menace; SoulGem keeps its own enchanting behavior. Other plugins can still cancel the normal Paper events.

Keep the plugin installed while retaining items with Phantom Menace. Removing its registry entry can cause Minecraft to discard the custom enchantment. A full server restart is required after installing because the enchantment is registered during bootstrap; do not hot-load it.

## Commands, permissions, and configuration

There are no plugin commands, permissions, or configuration settings. Use `/help phantommenace` for the in-game guide.

## Install and upgrade

1. Back up the world.
2. Download `phantommenace-0.2.0.jar` from the [Hangar project](https://hangar.papermc.io/rewrite123/phantommenace).
3. Place it in `plugins/` and fully restart Paper 26.3 on Java 25.

No special upgrade migration is required for this release. Keep the plugin present for as long as the world contains its custom-enchanted items.

## Build and publish

Build from this plugin directory with:

```sh
./gradlew build --console=plain
```

The JAR is written to `build/libs/phantommenace-0.2.0.jar`. To regenerate the original documentation diagrams from the repository root, run `python3 tools/docs/phantommenace.py`.

The Hangar publication targets `rewrite123/phantommenace` and syncs this README as its resource page. To publish, create the Hangar project and provide a token in `HANGAR_API_TOKEN`, then run:

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar
```

Never commit the API token. The project icon is generated separately with `python3 tools/generate-project-icons.py --project phantommenace` from the repository root.

## Links and support

- [GitHub source and documentation](https://github.com/rewrite123/papermc-plugins/tree/main/phantommenace)
- [Hangar project](https://hangar.papermc.io/rewrite123/phantommenace)
- [Report a bug or request help](https://github.com/rewrite123/papermc-plugins/issues)
