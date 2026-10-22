# enchantcap

An anvil cost-limit plugin for **Paper 26.3 / Java 25**. No runtime dependencies.

## Behavior

- Removes the server's normal 40-level anvil cutoff before results are calculated.
- Caps the displayed **and charged** cost of valid operations at **39 XP levels**,
  so vanilla clients do not display "Too Expensive".
- Scales material-repair prices by the fraction of an item's core crafting
  materials actually consumed, after applying the 39-level cap (details below).
- Keeps vanilla enchantment maximum levels by default, with an admin setting
  for additional levels obtainable through anvil combinations.
- Keeps item compatibility, enchantment conflicts, repair-material consumption
  and prior-work penalties unchanged. Players must still have enough XP levels.
- Applies to all players and all normal anvil operations, including repairs,
  renaming, tools, armor, weapons and enchanted books.
- Does not modify enchanting tables or bypass another plugin's rejected/empty
  result. Extra enchantment levels are opt-in; only valid anvil results are
  adjusted, never the original input items.

## Material repair pricing

For a valid repair using raw materials, the XP charge is:

`ceil(min(normal anvil cost, 39) * min(materials consumed, crafting count) / crafting count)`

The crafting count is the number of core materials in that kind of item:

| Item | Crafting count | One material, with a normal cost of 39 or higher |
|---|---:|---:|
| Pickaxe or axe | 3 | 13 levels |
| Sword or hoe | 2 | 20 levels |
| Shovel | 1 | 39 levels |
| Helmet | 5 | 8 levels |
| Chestplate | 8 | 5 levels |
| Leggings | 7 | 6 levels |
| Boots | 4 | 10 levels |

This covers wooden, stone, copper, iron, gold, diamond and netherite tools,
and material-repairable armor. The count comes from the item category, including
upgraded netherite gear. The ingredient must be a valid repair material for the
item. Stone and wooden tools retain vanilla's accepted repair-material choices.

A diamond pickaxe repaired with two diamonds costs two-thirds (26 levels at
the cap); three or more cost the full amount. Only ingredients **actually
consumed** count, not the size of the stack placed in the right slot. Fractions
round up, and positive charges never become free.

The amount of durability repaired and the number of ingredients consumed stay
vanilla. Combining two tools, enchanting without material repair, and renaming
without material repair keep their usual prices, capped at 39. If a material
repair also renames the item, the fraction applies to the entire operation's
cost. Items outside these tool/armor categories (such as elytra and maces) keep
their usual capped prices.

## Admin configuration

On first start, `plugins/enchantcap/config.yml` is created:

```yaml
max-additional-enchantment-levels: 0
```

- **0:** vanilla maximums (Unbreaking III, Efficiency V, etc.).
- **1:** up to vanilla maximum + 1 (Unbreaking IV, Efficiency VI, etc.).
- **2:** up to vanilla maximum + 2, and so on.

Restart the server after editing. The setting must be an integer from **0 to
254**; final enchantment levels cannot exceed Minecraft's **255** limit.
Invalid settings prevent the plugin from starting rather than silently
substituting a value.

Higher levels are earned through normal anvil merging: with the setting at 1,
Unbreaking III + Unbreaking III can produce IV, including enchanted-book
combinations. Unequal levels still follow vanilla merging; merely repairing or
renaming an item does not grant levels. Compatibility and conflict checks stay
vanilla. Single-level enchantments also use the configured extra allowance;
some enchantments do not gain a gameplay benefit from extra levels.

Lowering the setting does **not** strip existing higher enchantments from the
left-hand item, but it prevents further increases above the configured limit.
An over-level right-hand book/item cannot transfer a level above that limit.
Enchanting tables, loot and villager trades are unchanged.

The discount to 39 levels is intentional: vanilla clients hard-code the
"Too Expensive" display for costs of 40 or more. Full uncapped XP charging would
require accepting that misleading display or using a client modification.

The maximum server threshold is Java's `Integer.MAX_VALUE`; operations that
reach that representational limit are not supported. Prior-work costs still grow,
so this is not an infinite-integer repair system.

There are no commands or permissions. Other anvil plugins
can conflict if they replace results, thresholds or costs after enchantcap runs.
Restart the server when installing/removing; do not use plugin hot-reload.

## Build and install

```sh
./gradlew build --console=plain
```

On Windows use `gradlew.bat`. Gradle runs on Java 17+ and downloads a Java 25
toolchain if needed. The server itself must run Java 25.

Copy `build/libs/enchantcap-0.2.0.jar` to the server's `plugins/` directory and
restart. To uninstall, stop the server, remove the JAR and restart.
No server deployment or Hangar publication happens during a build.

## Publish to Hangar

The publication targets [rewrite123/enchantcap](https://hangar.papermc.io/rewrite123/enchantcap),
the `Release` channel, and Paper `26.3`. Create the Hangar project first and
provide a token with `create_version` permission through `HANGAR_API_TOKEN`.
Never store credentials in build files.

```sh
./gradlew build publishPluginPublicationToHangar \
  syncAllPluginPublicationPagesToHangar --console=plain
```

For subsequent releases, choose an unused version in `gradle.properties` and
provide release notes with `-PhangarChangelog="..."`. Normal `build` and `test`
tasks do not publish.

## Verification

`./gradlew test` covers early threshold setup, cost boundaries, cancelled opens,
non-anvil inventories, invalid results, material-cost fractions/rounding,
consumed-count handling, optional level bypass, stored-book limits and
preservation of existing higher levels. Tests use Mockito/MockBukkit; the native
repair-material predicate is mocked because MockBukkit does not implement its
delegate. They do not simulate Minecraft's full anvil menu.

Before using on valuable items, test on a Paper 26.3 server:

1. In survival, combine compatible items whose prior-work cost would exceed 39:
   verify a valid output appears for 39 levels.
2. With fewer than 39 levels, verify taking that output is refused; with enough
   levels, verify the correct XP and input materials are consumed once.
3. Verify a 39-level material repair of a diamond pickaxe costs 13 with one
   diamond, 26 with two and 39 with three; verify the actual ingredients consumed.
4. Verify one-material armor repairs use the table above, and repairing with a
   second tool keeps the usual capped price.
5. At the default setting, verify Efficiency V + V stays V. With the setting at
   1 after a restart, verify it produces VI and Unbreaking III + III produces IV.
6. Verify incompatible enchantments, rejected combinations and unrelated menus
   are unchanged. Verify existing over-level items survive a lower setting.
