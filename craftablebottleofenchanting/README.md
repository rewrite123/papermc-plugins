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

1. Hold glass bottles in your main hand.
2. **Sneak (shift) and right-click** an enchanting table. One glass bottle and **11 raw XP points** become one Bottle o' Enchanting instantly, with no menu.
3. Repeat for more bottles; each shift-right-click converts one.
4. Throw the experience bottle; when it breaks, one glass bottle drops at the impact location.

No lapis, bookshelves, menu, or command is required. A normal right-click (without sneaking) opens the vanilla enchanting table as usual, even while holding bottles.

## Bottle experience

Sneak-right-clicking an enchanting table with glass bottles in your main hand converts exactly one bottle per click; no inventory screen opens. The bottle is taken from the held stack. The output merges into a compatible experience-bottle stack when possible, otherwise uses an empty inventory slot.

| Action while holding glass bottles | Result |
| --- | --- |
| Sneak + right-click an enchanting table | Bottle one Bottle o' Enchanting |
| Right-click without sneaking | Vanilla enchanting table opens |
| Off-hand bottles, other blocks | Nothing changes |

| Input | Cost | Output |
| --- | ---: | --- |
| 1 glass bottle | 11 raw XP points (not levels) | 1 Bottle o' Enchanting |

XP is deducted from the player's total experience, including partial progress toward the next level. All game modes pay the same cost; spectators cannot bottle experience. Armor and offhand items are untouched. Nothing is consumed if the player lacks XP, the input bottle is missing, or there is no output space.

Each conversion happens immediately on the click and tells you the result in chat. Cancelled or protected table interactions (for example, by region-protection plugins) are respected.

## Returning glass bottles

Whenever an experience bottle breaks, the plugin drops **one glass bottle at its impact location**. This includes dispenser-thrown bottles. Vanilla XP drops (normally 3-11 XP) are unchanged. Cancelled impacts do not produce a bottle. Returned items obey ordinary physics and may be destroyed by hazards.

The 11-point conversion cost prevents repeatedly bottling and throwing from generating XP under vanilla XP-drop rules. Other plugins may modify those drops.

## Permissions and limits

| Permission | Default | Grants |
| --- | --- | --- |
| `craftablebottleofenchanting.use` | Everyone | Convert bottles by sneak-right-clicking an enchanting table. |
| `/help craftablebottleofenchanting` | Everyone | Open the in-game usage guide and links. |

Dead and spectator players cannot bottle experience. Other game modes pay normally. Only main-hand glass bottles count.

## Install

1. Download the latest jar from [Hangar](https://hangar.papermc.io/rewrite123/craftablebottleofenchanting).
2. Put `craftablebottleofenchanting-0.3.0.jar` in the Paper server's `plugins/` directory and restart on Java 25.
3. No configuration is required.

Build and test from the plugin folder with `./gradlew build --console=plain`. Gradle runs on Java 17+ and provisions Java 25 for compilation; Windows can use `gradlew.bat`. Tests use the available MockBukkit 26.2 registry harness while production compiles against Paper 26.3. Smoke-test sneak-right-click bottling, protected enchanting tables, and bottle impacts on Paper 26.3 before production use.

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
