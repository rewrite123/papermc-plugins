# Craftable Bottle of Enchanting

**Turn glass bottles and raw XP into Bottles o' Enchanting at an enchanting table, then recover a glass bottle when they break.**

Built for **Paper 26.3 / Java 25**. No runtime dependencies.

![Features at a glance](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/craftablebottleofenchanting/docs/images/features.png)

> **In game:** type `/help craftablebottleofenchanting` for the complete usage guide and clickable links to this page and the [issue tracker](https://github.com/rewrite123/papermc-plugins/issues).

## Contents

- [Quick start](#quick-start)
- [Bottle experience](#bottle-experience)
- [Returning glass bottles](#returning-glass-bottles)
- [Permissions and limits](#permissions-and-limits)
- [Install](#install)
- [Links and support](#links-and-support)

## Quick start

![Bottle experience flow](https://raw.githubusercontent.com/rewrite123/papermc-plugins/main/craftablebottleofenchanting/docs/images/bottle-flow.png)

1. Hold glass bottles in your main hand (hotbar slot).
2. Right-click an enchanting table to open the custom **Bottle Experience** menu.
3. Click its action to exchange one bottle and **11 raw XP points** for one Bottle o' Enchanting.
4. Throw the experience bottle; when it breaks, one glass bottle drops at the impact location.

No lapis, bookshelves, enchantment, or command is required. Opening an enchanting table without a glass bottle keeps normal enchanting unchanged.

## Bottle experience

The menu is a custom one-action inventory, not an added enchantment offer in the vanilla table. Each click bottles exactly one item. The input bottles must remain in the hotbar slot used to open the menu. The output merges into a compatible experience-bottle stack when possible, otherwise uses an empty inventory slot.

| Input | Cost | Output |
| --- | ---: | --- |
| 1 glass bottle | 11 raw XP points (not levels) | 1 Bottle o' Enchanting |

XP is deducted from the player's total experience, including partial progress toward the next level. All game modes pay the same cost; spectators cannot use the menu. Armor and offhand items are untouched. Nothing is consumed if the player lacks XP, the input bottle is missing, or there is no output space.

The menu blocks shift-clicking, number-key transfers, dragging, and taking the display item. A conversion is committed after inventory event dispatch. The player must still be online, have permission, have an empty cursor, and remain within six blocks of the same intact enchanting table. Cancelled table interaction events are respected.

## Returning glass bottles

Whenever an experience bottle breaks, the plugin drops **one glass bottle at its impact location**. This includes dispenser-thrown bottles. Vanilla XP drops (normally 3-11 XP) are unchanged. Cancelled impacts do not produce a bottle. Returned items obey ordinary physics and may be destroyed by hazards.

The 11-point conversion cost prevents repeatedly bottling and throwing from generating XP under vanilla XP-drop rules. Other plugins may modify those drops.

## Permissions and limits

| Permission | Default | Grants |
| --- | --- | --- |
| `craftablebottleofenchanting.use` | Everyone | Open the menu and convert bottles. |
| `/help craftablebottleofenchanting` | Everyone | Open the in-game usage guide and links. |

Dead and spectator players cannot use the menu. Other game modes pay normally. The menu is bound to its opener and opening table; removed tables, a different world, or moving beyond six blocks stop conversion. Keep the cursor empty before clicking.

## Install

1. Download the latest jar from [Hangar](https://hangar.papermc.io/rewrite123/craftablebottleofenchanting).
2. Put `craftablebottleofenchanting-0.2.0.jar` in the Paper server's `plugins/` directory and restart on Java 25.
3. No configuration is required.

Build and test from the plugin folder with `./gradlew build --console=plain`. Gradle runs on Java 17+ and provisions Java 25 for compilation; Windows can use `gradlew.bat`. Tests use the available MockBukkit 26.2 registry harness while production compiles against Paper 26.3. Smoke-test the menu, protected enchanting tables, and bottle impacts on Paper 26.3 before production use.

<details>
<summary>Build from source and publish</summary>

This project uses [HangarMC's official publishing Gradle plugin](https://github.com/HangarMC/hangar-publish-plugin). Create the `craftablebottleofenchanting` Hangar project and provide `HANGAR_API_TOKEN` with `create_version` permission. The publication defaults to channel `Release` and Paper `26.3`; normal build/test tasks do not upload anything. Never commit API keys.

```sh
./gradlew build publishPluginPublicationToHangar syncAllPluginPublicationPagesToHangar --console=plain
```

Choose a unique release version in `gradle.properties` and provide release notes with `-PhangarChangelog="..."`. This README is synced as the Hangar resource page. Generate its original diagrams from the repository root with `python3 tools/docs/craftablebottleofenchanting.py`.

</details>

## Links and support

- **Source and full guide:** [github.com/rewrite123/papermc-plugins/tree/main/craftablebottleofenchanting](https://github.com/rewrite123/papermc-plugins/tree/main/craftablebottleofenchanting)
- **Download:** [hangar.papermc.io/rewrite123/craftablebottleofenchanting](https://hangar.papermc.io/rewrite123/craftablebottleofenchanting)
- **Report a bug or request a feature:** [GitHub issues](https://github.com/rewrite123/papermc-plugins/issues)
